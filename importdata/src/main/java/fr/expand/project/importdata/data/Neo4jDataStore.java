package fr.expand.project.importdata.data;

import fr.expand.project.importdata.audit.*;
import fr.expand.project.importdata.dao.Neo4jDriverProvider;
import fr.expand.project.importdata.util.CypherUtils;
import fr.expand.project.importdata.util.SearchIndex;
import fr.expand.project.importdata.workflow.WorkflowEngine;

import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.*;

/** Tenant/model-scoped data operations. Query values never become Cypher text. */
public class Neo4jDataStore implements AutoCloseable {
    private final Driver driver;
    private final AuditActor actor;
    private final String expectedModelXml;
    private static final String OBJECT_RETURN =
            " RETURN id(n) AS id, labels(n) AS labels, properties(n) AS props ";
    private static final String LINK_RETURN =
            " RETURN id(r) AS id,id(a) AS fromId,id(b) AS toId,type(r) AS relType,properties(r) AS"
                    + " props ";

    public Neo4jDataStore() {
        this(null);
    }

    public Neo4jDataStore(String expectedModelXml) {
        this(expectedModelXml, AuditActor.system());
    }

    public Neo4jDataStore(String expectedModelXml, AuditActor actor) {
        this.actor = Objects.requireNonNull(actor);
        driver = Neo4jDriverProvider.getDriver();
        this.expectedModelXml = expectedModelXml;
    }

    private Map<String, List<String>> lockModel(TransactionContext tx, String key) {
        var schema =
                tx.run(
                        "MATCH (m:DataModel {key:$key}) SET m.key=m.key RETURN m.xml AS xml",
                        Map.of("key", key));
        if (!schema.hasNext()) throw new IllegalArgumentException("Model no longer exists: " + key);
        var record = schema.single();
        String storedXml = record.get("xml").isNull() ? "" : record.get("xml").asString();
        if (expectedModelXml != null && !expectedModelXml.equals(storedXml))
            throw new fr.expand.project.importdata.dao.StorageConflictException(
                    "Model changed during validation; reload and retry");
        return SearchIndex.fieldsFromXml(storedXml);
    }

    private static boolean valid(String key, long id) {
        return key != null && !key.isBlank() && id >= 0;
    }

    private static Map<String, Object> params(String key, long id) {
        return new HashMap<>(Map.of("modelKey", key, "id", id));
    }

    public List<Map<String, Object>> loadObjects(String key) {
        if (key == null || key.isBlank()) return List.of();
        try (Session session = driver.session()) {
            return session.executeRead(
                    tx ->
                            objects(
                                    tx.run(
                                            "MATCH (n:DataObject {modelKey:$modelKey})"
                                                    + OBJECT_RETURN
                                                    + "ORDER BY id(n)",
                                            Map.of("modelKey", key))));
        }
    }

    public List<Map<String, Object>> loadLinks(String key) {
        if (key == null || key.isBlank()) return List.of();
        try (Session session = driver.session()) {
            return session.executeRead(
                    tx ->
                            links(
                                    tx.run(
                                            "MATCH (a:DataObject"
                                                    + " {modelKey:$modelKey})-[r]->(b:DataObject"
                                                    + " {modelKey:$modelKey})"
                                                    + LINK_RETURN
                                                    + "ORDER BY id(r)",
                                            Map.of("modelKey", key))));
        }
    }

    public Map<String, Object> loadDataPage(
            String key, int offset, int limit, String q, String type) {
        return loadDataPage(key, offset, limit, q, type, "contains");
    }

    public Map<String, Object> loadDataPage(
            String key, int offset, int limit, String q, String type, String searchMode) {
        return loadDataPage(key, offset, limit, q, type, searchMode, null, null);
    }

    public Map<String, Object> loadDataPage(
            String key,
            int offset,
            int limit,
            String q,
            String type,
            String searchMode,
            String workflowStatus,
            String workflowId) {
        if (workflowStatus != null && workflowStatus.length() > 128
                || workflowId != null && workflowId.length() > 128)
            throw new IllegalArgumentException("Workflow filter must be at most 128 characters");
        String mode = searchMode == null || searchMode.isBlank() ? "contains" : searchMode;
        if (!Set.of("contains", "fulltext").contains(mode))
            throw new IllegalArgumentException("searchMode must be contains or fulltext");
        if (q != null && q.length() > 512)
            throw new IllegalArgumentException("Search query must be at most 512 characters");
        boolean fulltext = mode.equals("fulltext") && q != null && !q.isBlank();
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("Model key is required");
        if (offset < 0 || limit < 1 || limit > 500)
            throw new IllegalArgumentException("offset must be nonnegative; limit must be 1..500");
        List<String> types =
                type == null || type.isBlank()
                        ? List.of()
                        : Arrays.stream(type.split(","))
                                .map(String::trim)
                                .filter(v -> !v.isBlank())
                                .distinct()
                                .toList();
        Map<String, Object> p = new HashMap<>();
        p.put("modelKey", key);
        p.put("offset", offset);
        p.put("limit", limit);
        p.put("q", q == null ? "" : q.trim().toLowerCase(Locale.ROOT));
        p.put("types", types);
        p.put("workflowStatus", workflowStatus == null ? "" : workflowStatus);
        p.put("workflowId", workflowId == null ? "" : workflowId);
        if (fulltext) {
            ensureSearchIndex(key);
            p.put("query", SearchIndex.literalQuery(q));
        } else p.put("searchFields", searchableFields(key, q));
        String filter =
                "MATCH (n:DataObject {modelKey:$modelKey}) WHERE (size($types)=0 OR any(t IN $types"
                        + " WHERE t=n.type OR t IN labels(n))) AND ($q='' OR any(owner IN"
                        + " keys($searchFields) WHERE (owner=n.type OR owner IN labels(n)) AND"
                        + " any(field IN $searchFields[owner] WHERE toLower(toString(n[field]))"
                        + " CONTAINS $q))) ";
        if (fulltext)
            filter =
                    "CALL db.index.fulltext.queryNodes('data_object_search',$query) YIELD node AS n"
                        + " WHERE n.modelKey=$modelKey AND (size($types)=0 OR any(t IN $types WHERE"
                        + " t=n.type OR t IN labels(n))) ";
        final String queryFilter =
                filter
                        + " AND ($workflowId='' OR n._workflowId=$workflowId) AND"
                        + " ($workflowStatus='' OR ($workflowStatus='__unassigned__' AND"
                        + " n._workflowId IS NULL) OR n._workflowState=$workflowStatus) ";
        try (Session session = driver.session()) {
            return session.executeRead(
                    tx -> {
                        long total =
                                tx.run(queryFilter + "RETURN count(n) AS count", p)
                                        .single()
                                        .get("count")
                                        .asLong();
                        var rows =
                                objects(
                                        tx.run(
                                                queryFilter
                                                        + OBJECT_RETURN
                                                        + "ORDER BY id(n) SKIP $offset LIMIT"
                                                        + " $limit",
                                                p));
                        List<Long> ids =
                                rows.stream()
                                        .map(row -> ((Number) row.get("id")).longValue())
                                        .toList();
                        p.put("ids", ids);
                        var relations =
                                links(
                                        tx.run(
                                                "MATCH (a:DataObject"
                                                    + " {modelKey:$modelKey})-[r]->(b:DataObject"
                                                    + " {modelKey:$modelKey}) WHERE id(a) IN $ids"
                                                    + " AND id(b) IN $ids"
                                                        + LINK_RETURN
                                                        + "ORDER BY id(r) LIMIT 5001",
                                                p));
                        boolean moreLinks = relations.size() > 5000;
                        if (moreLinks) relations.remove(relations.size() - 1);
                        long totalLinks =
                                tx.run(
                                                "MATCH (:DataObject"
                                                    + " {modelKey:$modelKey})-[r]->(:DataObject"
                                                    + " {modelKey:$modelKey}) RETURN count(r) AS"
                                                    + " count",
                                                p)
                                        .single()
                                        .get("count")
                                        .asLong();
                        var typeRows =
                                tx.run(
                                        "MATCH (n:DataObject {modelKey:$modelKey}) UNWIND labels(n)"
                                                + " AS label WITH DISTINCT label WHERE label <>"
                                                + " 'DataObject' RETURN label ORDER BY label",
                                        p);
                        List<String> objectTypes = new ArrayList<>();
                        while (typeRows.hasNext())
                            objectTypes.add(typeRows.next().get("label").asString());
                        Map<String, Object> page = new LinkedHashMap<>();
                        page.put("objects", rows);
                        page.put("links", relations);
                        page.put("objectCount", rows.size());
                        page.put("linkCount", relations.size());
                        page.put("totalObjects", total);
                        page.put("totalLinks", totalLinks);
                        page.put("hasMoreLinks", moreLinks);
                        page.put("hasMore", (long) offset + rows.size() < total);
                        page.put("offset", offset);
                        page.put("limit", limit);
                        page.put("searchMode", mode);
                        page.put("objectTypes", objectTypes);
                        return page;
                    });
        }
    }

    private static Map<String, List<String>> searchableFields(String key, String query) {
        if (query == null || query.isBlank()) return Map.of();
        try (var store = new fr.expand.project.importdata.model.Neo4jModelStore()) {
            return SearchIndex.fieldsFromXml(store.loadModelXmlByKey(key));
        }
    }

    /** One protected backfill per model schema; later searches use only the native index. */
    private void ensureSearchIndex(String key) {
        try (Session session = driver.session()) {
            session.executeWrite(
                    tx -> {
                        var schema =
                                tx.run(
                                        "MATCH (m:DataModel {key:$key}) SET m.key=m.key RETURN"
                                                + " m.xml AS xml,m.searchRevision AS revision",
                                        Map.of("key", key));
                        if (!schema.hasNext())
                            throw new IllegalArgumentException("Model no longer exists: " + key);
                        var record = schema.single();
                        String xml = record.get("xml").asString("");
                        String revision = SearchIndex.revision(xml);
                        if (!revision.equals(record.get("revision").asString(""))) {
                            Map<String, Object> p = new HashMap<>();
                            p.put("modelKey", key);
                            p.put("searchFields", SearchIndex.fieldsFromXml(xml));
                            p.put("revision", revision);
                            tx.run(
                                            "MATCH (n:DataObject {modelKey:$modelKey}) SET"
                                                    + " n.searchText="
                                                    + SearchIndex.EXPRESSION,
                                            p)
                                    .consume();
                            tx.run(
                                            "MATCH (m:DataModel {key:$modelKey}) SET"
                                                    + " m.searchRevision=$revision",
                                            p)
                                    .consume();
                        }
                        return null;
                    });
        }
    }

    public Map<String, Object> neighbors(String key, long id, int limit) {
        if (!valid(key, id)) throw new IllegalArgumentException("Invalid object ID or model key");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        Map<String, Object> p = params(key, id);
        p.put("limit", limit + 1);
        try (Session session = driver.session()) {
            return session.executeRead(
                    tx -> {
                        var center =
                                objects(
                                        tx.run(
                                                "MATCH (n:DataObject {modelKey:$modelKey}) WHERE"
                                                        + " id(n)=$id"
                                                        + OBJECT_RETURN,
                                                p));
                        if (center.isEmpty()) return null;
                        var adjacent =
                                objects(
                                        tx.run(
                                                "MATCH (center:DataObject"
                                                    + " {modelKey:$modelKey})--(n:DataObject"
                                                    + " {modelKey:$modelKey}) WHERE id(center)=$id"
                                                    + " AND id(n)<>$id WITH DISTINCT n"
                                                        + OBJECT_RETURN
                                                        + "ORDER BY id(n) LIMIT $limit",
                                                p));
                        boolean more = adjacent.size() > limit;
                        if (more) adjacent.remove(adjacent.size() - 1);
                        center.addAll(adjacent);
                        p.put(
                                "ids",
                                center.stream()
                                        .map(row -> ((Number) row.get("id")).longValue())
                                        .toList());
                        var relations =
                                links(
                                        tx.run(
                                                "MATCH (a:DataObject"
                                                    + " {modelKey:$modelKey})-[r]->(b:DataObject"
                                                    + " {modelKey:$modelKey}) WHERE id(a) IN $ids"
                                                    + " AND id(b) IN $ids AND (id(a)=$id OR"
                                                    + " id(b)=$id)"
                                                        + LINK_RETURN
                                                        + "ORDER BY id(r) LIMIT 5001",
                                                p));
                        boolean moreLinks = relations.size() > 5000;
                        if (moreLinks) relations.remove(relations.size() - 1);
                        return Map.of(
                                "objects",
                                center,
                                "links",
                                relations,
                                "hasMore",
                                more || moreLinks,
                                "hasMoreLinks",
                                moreLinks);
                    });
        }
    }

    public Map<String, Object> loadObjectById(String key, long id) {
        if (!valid(key, id)) return null;
        try (Session session = driver.session()) {
            return session.executeRead(
                    tx -> {
                        var rows =
                                objects(
                                        tx.run(
                                                "MATCH (n:DataObject {modelKey:$modelKey}) WHERE"
                                                        + " id(n)=$id"
                                                        + OBJECT_RETURN,
                                                params(key, id)));
                        return rows.isEmpty() ? null : rows.get(0);
                    });
        }
    }

    public Map<String, Object> loadLinkById(String key, long id) {
        if (!valid(key, id)) return null;
        try (Session session = driver.session()) {
            return session.executeRead(
                    tx -> {
                        var rows =
                                links(
                                        tx.run(
                                                "MATCH (a:DataObject"
                                                    + " {modelKey:$modelKey})-[r]->(b:DataObject"
                                                    + " {modelKey:$modelKey}) WHERE id(r)=$id"
                                                        + LINK_RETURN,
                                                params(key, id)));
                        return rows.isEmpty() ? null : rows.get(0);
                    });
        }
    }

    private static Map<String, Object> uuidParams(String key, String uuid) {
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("Model key is required");
        if (uuid == null
                || !uuid.matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Invalid relationship UUID");
        return new HashMap<>(Map.of("modelKey", key, "uuid", UUID.fromString(uuid).toString()));
    }

    /** UUID addresses remain stable when Neo4j recycles numeric relationship IDs. */
    public Map<String, Object> loadLinkByUuid(String key, String uuid) {
        Map<String, Object> p = uuidParams(key, uuid);
        try (Session session = driver.session()) {
            return session.executeRead(
                    tx -> {
                        var rows =
                                links(
                                        tx.run(
                                                "MATCH (a:DataObject {modelKey:$modelKey})-[r"
                                                        + " {uuid:$uuid}]->(b:DataObject"
                                                        + " {modelKey:$modelKey})"
                                                        + LINK_RETURN,
                                                p));
                        if (rows.size() > 1)
                            throw new fr.expand.project.importdata.dao.StorageConflictException(
                                    "Ambiguous relationship UUID");
                        return rows.isEmpty() ? null : rows.get(0);
                    });
        }
    }

    public boolean updateLinkByUuid(String key, String uuid, Map<String, Object> attrs) {
        return changeLinkByUuid(key, uuid, attrs, false);
    }

    public boolean deleteLinkByUuid(String key, String uuid) {
        return changeLinkByUuid(key, uuid, Map.of(), true);
    }

    private boolean changeLinkByUuid(
            String key, String uuid, Map<String, Object> attrs, boolean delete) {
        Map<String, Object> p = uuidParams(key, uuid);
        p.put("attributes", sanitized(attrs));
        try (Session session = driver.session()) {
            return session.executeWrite(
                    tx -> {
                        lockModel(tx, key);
                        var ids =
                                tx.run(
                                                "MATCH (a:DataObject {modelKey:$modelKey})-[r"
                                                    + " {uuid:$uuid}]->(b:DataObject"
                                                    + " {modelKey:$modelKey}) RETURN id(r) AS id",
                                                p)
                                        .list(r -> r.get("id").asLong());
                        if (ids.isEmpty()) return false;
                        if (ids.size() > 1)
                            throw new fr.expand.project.importdata.dao.StorageConflictException(
                                    "Ambiguous relationship UUID");
                        long id = ids.get(0);
                        var before = AuditTrail.link(tx, key, id);
                        p.put("id", id);
                        tx.run(
                                        "MATCH (a:DataObject"
                                                + " {modelKey:$modelKey})-[n]->(b:DataObject"
                                                + " {modelKey:$modelKey}) WHERE id(n)=$id "
                                                + (delete ? "DELETE n" : "SET n += $attributes"),
                                        p)
                                .consume();
                        AuditTrail.record(
                                tx,
                                key,
                                actor,
                                delete ? "DELETE" : "UPDATE",
                                "LINK",
                                AuditTrail.identity(before),
                                before,
                                delete ? null : AuditTrail.link(tx, key, id));
                        return true;
                    });
        }
    }

    private static Map<String, Object> sanitized(Map<String, Object> attrs) {
        if (attrs == null) throw new IllegalArgumentException("Attributes are required");
        Map<String, Object> result = new LinkedHashMap<>();
        attrs.forEach(
                (k, v) -> {
                    CypherUtils.validateAttributeKey(k);
                    result.put(k, v == null ? "" : v.toString());
                });
        return result;
    }

    public boolean updateObject(String key, long id, Map<String, Object> attrs) {
        return update(key, id, attrs, false);
    }

    public boolean updateLink(String key, long id, Map<String, Object> attrs) {
        return update(key, id, attrs, true);
    }

    private boolean update(String key, long id, Map<String, Object> attrs, boolean link) {
        if (!valid(key, id)) return false;
        Map<String, Object> p = params(key, id);
        p.put("attributes", sanitized(attrs));
        String query =
                link
                        ? "MATCH (a:DataObject {modelKey:$modelKey})-[n]->(b:DataObject"
                                + " {modelKey:$modelKey})"
                        : "MATCH (n:DataObject {modelKey:$modelKey})";
        try (Session session = driver.session()) {
            return session.executeWrite(
                    tx -> {
                        p.put("searchFields", lockModel(tx, key));
                        var before =
                                link
                                        ? AuditTrail.link(tx, key, id)
                                        : AuditTrail.object(tx, key, id);
                        if (before == null) return false;
                        tx.run(
                                        query
                                                + " WHERE id(n)=$id SET n += $attributes "
                                                + (link
                                                        ? ""
                                                        : "SET n.searchText="
                                                                + SearchIndex.EXPRESSION
                                                                + " ")
                                                + "RETURN id(n)",
                                        p)
                                .consume();
                        AuditTrail.record(
                                tx,
                                key,
                                actor,
                                "UPDATE",
                                link ? "LINK" : "OBJECT",
                                AuditTrail.identity(before),
                                before,
                                link
                                        ? AuditTrail.link(tx, key, id)
                                        : AuditTrail.object(tx, key, id));
                        return true;
                    });
        }
    }

    public boolean deleteObject(String key, long id) {
        return delete(key, id, false);
    }

    public boolean deleteLink(String key, long id) {
        return delete(key, id, true);
    }

    private boolean delete(String key, long id, boolean link) {
        if (!valid(key, id)) return false;
        String query =
                link
                        ? "MATCH (a:DataObject {modelKey:$modelKey})-[n]->(b:DataObject"
                                + " {modelKey:$modelKey})"
                        : "MATCH (n:DataObject {modelKey:$modelKey})";
        try (Session session = driver.session()) {
            return session.executeWrite(
                    tx -> {
                        lockModel(tx, key);
                        var before =
                                link
                                        ? AuditTrail.link(tx, key, id)
                                        : AuditTrail.object(tx, key, id);
                        if (before == null) return false;
                        if (!link) AuditTrail.deletingLinks(tx, key, id, actor);
                        tx.run(
                                        query
                                                + " WHERE id(n)=$id WITH n "
                                                + (link ? "DELETE n" : "DETACH DELETE n")
                                                + " RETURN 1 AS deleted",
                                        params(key, id))
                                .consume();
                        AuditTrail.record(
                                tx,
                                key,
                                actor,
                                "DELETE",
                                link ? "LINK" : "OBJECT",
                                AuditTrail.identity(before),
                                before,
                                null);
                        return true;
                    });
        }
    }

    public long createObject(String key, String type, Map<String, Object> attributes) {
        return createObject(key, type, attributes, null);
    }

    public long createObject(
            String key, String type, Map<String, Object> attributes, Integer externalId) {
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("Model key is required");
        if (externalId != null && externalId < 0)
            throw new IllegalArgumentException("External object ID must be nonnegative");
        String label = CypherUtils.identifier(type);
        Map<String, Object> props = sanitized(attributes);
        props.put("modelKey", key);
        props.put("type", type);
        props.put("uuid", UUID.randomUUID().toString());
        if (externalId != null) props.put("dataId", externalId);
        try (Session session = driver.session()) {
            return session.executeWrite(
                    tx -> {
                        var searchFields = lockModel(tx, key);
                        props.putAll(WorkflowEngine.initialProperties(tx, key, type));
                        props.put("searchText", SearchIndex.text(type, props, searchFields));
                        if (externalId != null
                                && tx.run(
                                                "MATCH (n:DataObject {modelKey:$key}) WHERE"
                                                    + " n.dataId=$id AND (n.type=$type OR $type IN"
                                                    + " labels(n)) RETURN n LIMIT 1",
                                                Map.of("key", key, "id", externalId, "type", type))
                                        .hasNext())
                            throw new fr.expand.project.importdata.dao.StorageConflictException(
                                    "Object already exists: " + type + "/" + externalId);
                        long id =
                                tx.run(
                                                "CREATE (n:DataObject:"
                                                        + label
                                                        + ") SET n=$props RETURN id(n) AS id",
                                                Map.of("props", props))
                                        .single()
                                        .get("id")
                                        .asLong();
                        var after = AuditTrail.object(tx, key, id);
                        AuditTrail.record(
                                tx,
                                key,
                                actor,
                                "CREATE",
                                "OBJECT",
                                AuditTrail.identity(after),
                                null,
                                after);
                        return id;
                    });
        } catch (org.neo4j.driver.exceptions.ClientException e) {
            throw fr.expand.project.importdata.dao.StorageConflictException.translate(e);
        }
    }

    public long createLink(
            String key,
            long fromId,
            long toId,
            String type,
            boolean directed,
            Map<String, Object> attrs) {
        if (!valid(key, fromId) || toId < 0)
            throw new IllegalArgumentException("Invalid model key or endpoint ID");
        Map<String, Object> p = params(key, fromId);
        p.put("toId", toId);
        Map<String, Object> props = sanitized(attrs);
        props.put("modelKey", key);
        props.put("linkType", type);
        props.put("directed", directed);
        props.put("uuid", UUID.randomUUID().toString());
        p.put("props", props);
        try (Session session = driver.session()) {
            return session.executeWrite(
                    tx -> {
                        lockModel(tx, key);
                        var result =
                                tx.run(
                                        "MATCH (a:DataObject {modelKey:$modelKey}),(b:DataObject"
                                                + " {modelKey:$modelKey}) WHERE id(a)=$id AND"
                                                + " id(b)=$toId CREATE (a)-[r:"
                                                + CypherUtils.identifier(type)
                                                + "]->(b) SET r=$props RETURN id(r) AS id",
                                        p);
                        if (!result.hasNext())
                            throw new IllegalArgumentException("Link endpoint not found");
                        long id = result.single().get("id").asLong();
                        var after = AuditTrail.link(tx, key, id);
                        AuditTrail.record(
                                tx,
                                key,
                                actor,
                                "CREATE",
                                "LINK",
                                AuditTrail.identity(after),
                                null,
                                after);
                        return id;
                    });
        }
    }

    private static List<Map<String, Object>> objects(Result result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (result.hasNext()) {
            Record record = result.next();
            Map<String, Object> props = record.get("props").asMap();
            String type =
                    props.containsKey("type")
                            ? String.valueOf(props.get("type"))
                            : record.get("labels").asList(Value::asString).stream()
                                    .filter(label -> !"DataObject".equals(label))
                                    .findFirst()
                                    .orElse("Object");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", record.get("id").asLong());
            row.put("type", type);
            row.put("workflow", WorkflowEngine.objectState(props));
            row.put("attributes", attributeRows(props));
            if (props.containsKey("dataId")) row.put("dataId", props.get("dataId"));
            if (props.containsKey("uuid")) row.put("uuid", props.get("uuid"));
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> links(Result result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (result.hasNext()) {
            Record record = result.next();
            Map<String, Object> props = record.get("props").asMap();
            String relType = record.get("relType").asString();
            String linkType = String.valueOf(props.getOrDefault("linkType", relType));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", record.get("id").asLong());
            row.put("fromId", record.get("fromId").asLong());
            row.put("toId", record.get("toId").asLong());
            row.put("type", linkType);
            row.put("linkType", linkType);
            row.put("relationshipType", relType);
            row.put("directed", props.getOrDefault("directed", true));
            row.put("attributes", attributeRows(props));
            if (props.containsKey("uuid")) row.put("uuid", props.get("uuid"));
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> attributeRows(Map<String, Object> props) {
        List<Map<String, Object>> attrs = new ArrayList<>();
        props.entrySet().stream()
                .filter(entry -> !CypherUtils.isReservedProperty(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry ->
                                attrs.add(
                                        Map.of(
                                                "key",
                                                entry.getKey(),
                                                "value",
                                                String.valueOf(entry.getValue()))));
        return attrs;
    }

    @Override
    public void close() {
        /* Shared driver belongs to application. */
    }
}
