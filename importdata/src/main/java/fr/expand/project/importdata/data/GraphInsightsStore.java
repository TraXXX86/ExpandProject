package fr.expand.project.importdata.data;

import fr.expand.project.importdata.dao.Neo4jDriverProvider;
import fr.expand.project.importdata.dto.generated.ATTRIBUTE;
import fr.expand.project.importdata.dto.generated.LINK;
import fr.expand.project.importdata.dto.generated.OBJECT;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.generated.OBJECTTYPE;
import fr.expand.project.importdata.model.generated.TYPEREF;
import fr.expand.project.importdata.util.CypherUtils;
import fr.expand.project.importdata.validation.DataValidator;
import fr.expand.project.importdata.validation.ValidationResult;

import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

import java.text.Normalizer;
import java.time.Duration;
import java.util.*;

/** Read-only graph diagnostics with explicit work and response budgets. */
public final class GraphInsightsStore {
    public static final int MAX_OBJECTS = 2000;
    public static final int MAX_LINKS = 10000;
    public static final int MAX_PATHS = 20;
    public static final int MAX_SAMPLES = 100;
    private static final TransactionConfig READ_CONFIG =
            TransactionConfig.builder().withTimeout(Duration.ofSeconds(15)).build();

    public Map<String, Object> paths(
            String key,
            long from,
            long to,
            int depth,
            Set<String> types,
            boolean respectDirection,
            ModelManager manager) {
        if (key == null || key.isBlank() || from < 0 || to < 0 || depth < 1 || depth > 6)
            throw new IllegalArgumentException("Paramètres de recherche de chemins invalides");
        Set<String> allowed = new LinkedHashSet<>();
        manager.getCurrentModel()
                .getLINKTYPES()
                .getLINKTYPE()
                .forEach(t -> allowed.add(t.getNAME()));
        if (!allowed.containsAll(types)) throw new IllegalArgumentException("Type de lien inconnu");
        Set<String> selected = types.isEmpty() ? allowed : types;
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            return session.executeRead(
                    tx -> findPaths(tx, key, from, to, depth, selected, respectDirection),
                    READ_CONFIG);
        }
    }

    private record Step(long previous, Map<String, Object> link) {}

    private Map<String, Object> findPaths(
            TransactionContext tx,
            String key,
            long from,
            long to,
            int maxDepth,
            Set<String> types,
            boolean respectDirection) {
        Map<Long, Map<String, Object>> objects = new LinkedHashMap<>();
        var endpoints =
                tx.run(
                        "MATCH (n:DataObject {modelKey:$key}) WHERE id(n) IN $ids RETURN n",
                        Map.of("key", key, "ids", List.of(from, to)));
        while (endpoints.hasNext()) {
            Node n = endpoints.next().get("n").asNode();
            objects.put(n.id(), objectRow(n));
        }
        if (!objects.containsKey(from) || !objects.containsKey(to))
            throw new NoSuchElementException("Objet source ou cible introuvable");
        Map<Long, Integer> distance = new HashMap<>();
        Map<Long, List<Step>> predecessors = new HashMap<>();
        distance.put(from, 0);
        List<Long> frontier = List.of(from);
        int scanned = 0;
        boolean truncated = false;
        for (int level = 0;
                level < maxDepth && !frontier.isEmpty() && !distance.containsKey(to);
                level++) {
            int remaining = MAX_LINKS - scanned;
            if (remaining <= 0) {
                truncated = true;
                break;
            }
            var result =
                    tx.run(
                            "MATCH (a:DataObject {modelKey:$key})-[r]-(b:DataObject"
                                    + " {modelKey:$key}) WHERE id(a) IN $frontier AND"
                                    + " coalesce(r.linkType,type(r)) IN $types AND (NOT $respect OR"
                                    + " coalesce(r.directed,true)=false OR id(startNode(r))=id(a))"
                                    + " RETURN a,b,r LIMIT $limit",
                            Map.of(
                                    "key",
                                    key,
                                    "frontier",
                                    frontier,
                                    "types",
                                    new ArrayList<>(types),
                                    "respect",
                                    respectDirection,
                                    "limit",
                                    remaining + 1));
            Set<Long> next = new LinkedHashSet<>();
            while (result.hasNext()) {
                var row = result.next();
                if (scanned >= MAX_LINKS) {
                    truncated = true;
                    break;
                }
                scanned++;
                Node a = row.get("a").asNode();
                Node b = row.get("b").asNode();
                if (!distance.containsKey(b.id())) {
                    if (distance.size() >= MAX_OBJECTS) {
                        truncated = true;
                        continue;
                    }
                    distance.put(b.id(), level + 1);
                    objects.put(b.id(), objectRow(b));
                    next.add(b.id());
                }
                if (distance.get(b.id()) == level + 1) {
                    predecessors
                            .computeIfAbsent(b.id(), ignored -> new ArrayList<>())
                            .add(new Step(a.id(), linkRow(row.get("r").asRelationship())));
                }
            }
            result.consume();
            frontier = new ArrayList<>(next);
            if (truncated) break;
        }
        List<Map<String, Object>> paths = new ArrayList<>();
        if (distance.containsKey(to))
            collectPaths(
                    to, from, predecessors, objects, new ArrayList<>(), new ArrayList<>(), paths);
        if (paths.size() > MAX_PATHS) {
            paths.remove(paths.size() - 1);
            truncated = true;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("paths", paths);
        response.put("truncated", truncated);
        response.put("shortestOnly", true);
        response.put("visitedObjects", distance.size());
        response.put("scannedEdges", scanned);
        response.put(
                "limits",
                Map.of(
                        "maxDepth",
                        maxDepth,
                        "maxVisited",
                        MAX_OBJECTS,
                        "maxEdges",
                        MAX_LINKS,
                        "maxPaths",
                        MAX_PATHS));
        return response;
    }

    private void collectPaths(
            long current,
            long from,
            Map<Long, List<Step>> predecessors,
            Map<Long, Map<String, Object>> objects,
            List<Map<String, Object>> reverseObjects,
            List<Map<String, Object>> reverseLinks,
            List<Map<String, Object>> paths) {
        if (paths.size() > MAX_PATHS) return;
        reverseObjects.add(objects.get(current));
        if (current == from) {
            var nodes = new ArrayList<>(reverseObjects);
            var links = new ArrayList<>(reverseLinks);
            Collections.reverse(nodes);
            Collections.reverse(links);
            paths.add(Map.of("objects", nodes, "links", links));
        } else {
            for (Step step : predecessors.getOrDefault(current, List.of())) {
                reverseLinks.add(step.link());
                collectPaths(
                        step.previous(),
                        from,
                        predecessors,
                        objects,
                        reverseObjects,
                        reverseLinks,
                        paths);
                reverseLinks.remove(reverseLinks.size() - 1);
                if (paths.size() > MAX_PATHS) break;
            }
        }
        reverseObjects.remove(reverseObjects.size() - 1);
    }

    public Map<String, Object> quality(String key, ModelManager manager) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("modelKey manquant");
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            return session.executeRead(tx -> scanQuality(tx, key, manager), READ_CONFIG);
        }
    }

    private Map<String, Object> scanQuality(
            TransactionContext tx, String key, ModelManager manager) {
        var nodeResult =
                tx.run(
                        "MATCH (n:DataObject {modelKey:$key}) WITH n LIMIT $limit RETURN n, NOT"
                            + " EXISTS { MATCH (n)--(:DataObject {modelKey:$key}) } AS isolated",
                        Map.of("key", key, "limit", MAX_OBJECTS + 1));
        var nodes = nodeResult.list();
        boolean moreNodes = nodes.size() > MAX_OBJECTS;
        var linkResult =
                tx.run(
                        "MATCH (a:DataObject {modelKey:$key})-[r]->(b:DataObject {modelKey:$key}) "
                                + "RETURN a,b,r LIMIT $limit",
                        Map.of("key", key, "limit", MAX_LINKS + 1));
        var links = linkResult.list();
        boolean moreLinks = links.size() > MAX_LINKS;
        var issues = new ArrayList<Map<String, Object>>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("isolated", 0);
        counts.put("potential_duplicate", 0);
        counts.put("schema_drift", 0);
        Map<List<String>, Long> duplicateKeys = new HashMap<>();
        DataValidator validator = new DataValidator(manager);
        for (var record : nodes.subList(0, Math.min(nodes.size(), MAX_OBJECTS))) {
            Node node = record.get("n").asNode();
            String type = objectType(node);
            if (record.get("isolated").asBoolean())
                issue(
                        issues,
                        counts,
                        "isolated",
                        "object",
                        node.id(),
                        type,
                        "info",
                        "Objet sans lien vers un autre objet de ce modèle",
                        List.of());
            OBJECT object = new OBJECT();
            object.setID(0);
            object.setTYPE(type);
            object.getATTRIBUTE().addAll(dtoAttributes(node.asMap()));
            int attrCount = object.getATTRIBUTE().size();
            validationIssues(
                    issues,
                    counts,
                    "object",
                    node.id(),
                    type,
                    validator.validateObjectAttributes(object));
            if (object.getATTRIBUTE().size() > attrCount)
                issue(
                        issues,
                        counts,
                        "schema_drift",
                        "object",
                        node.id(),
                        type,
                        "warning",
                        "Attributs absents auxquels le modèle actuel attribue une valeur par"
                                + " défaut",
                        List.of());
            List<String> signature = duplicateSignature(node, manager);
            if (signature != null) {
                Long first = duplicateKeys.putIfAbsent(signature, node.id());
                if (first != null)
                    issue(
                            issues,
                            counts,
                            "potential_duplicate",
                            "object",
                            node.id(),
                            type,
                            "warning",
                            "Valeurs représentatives ou recherchables identiques après"
                                    + " normalisation ; vérification manuelle nécessaire",
                            List.of(first));
            }
        }
        for (var record : links.subList(0, Math.min(links.size(), MAX_LINKS))) {
            Relationship rel = record.get("r").asRelationship();
            String type = linkType(rel);
            LINK link = new LINK();
            link.setTYPE(type);
            link.getATTRIBUTE().addAll(dtoAttributes(rel.asMap()));
            int attrCount = link.getATTRIBUTE().size();
            validationIssues(
                    issues, counts, "link", rel.id(), type, validator.validateLinkAttributes(link));
            if (link.getATTRIBUTE().size() > attrCount)
                issue(
                        issues,
                        counts,
                        "schema_drift",
                        "link",
                        rel.id(),
                        type,
                        "warning",
                        "Attributs absents auxquels le modèle actuel attribue une valeur par"
                                + " défaut",
                        List.of());
            var definition = manager.getLinkType(type);
            if (definition != null) {
                String source = objectType(record.get("a").asNode());
                String target = objectType(record.get("b").asNode());
                if (!allowed(manager, source, definition.getSOURCETYPES().getTYPEREF())
                        || !allowed(manager, target, definition.getTARGETTYPES().getTYPEREF()))
                    issue(
                            issues,
                            counts,
                            "schema_drift",
                            "link",
                            rel.id(),
                            type,
                            "error",
                            "Types source ou cible incompatibles avec le modèle actuel",
                            List.of());
                if (rel.get("directed").asBoolean(true) != definition.isDIRECTED())
                    issue(
                            issues,
                            counts,
                            "schema_drift",
                            "link",
                            rel.id(),
                            type,
                            "warning",
                            "Orientation du lien différente de la définition actuelle",
                            List.of());
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("objectCount", nodes.size());
        report.put("linkCount", links.size());
        report.put("objectCountExact", !moreNodes);
        report.put("linkCountExact", !moreLinks);
        report.put("scannedObjects", Math.min(nodes.size(), MAX_OBJECTS));
        report.put("scannedLinks", Math.min(links.size(), MAX_LINKS));
        report.put("truncated", moreNodes || moreLinks);
        report.put("issues", issues);
        report.put("issueCounts", counts);
        report.put(
                "samplesTruncated",
                counts.values().stream().mapToInt(Integer::intValue).sum() > issues.size());
        report.put(
                "limits",
                Map.of(
                        "maxObjects",
                        MAX_OBJECTS,
                        "maxLinks",
                        MAX_LINKS,
                        "maxSamples",
                        MAX_SAMPLES));
        report.put(
                "duplicateHeuristic",
                "Même type et mêmes valeurs non vides pour tous les attributs représentatifs, ou à"
                    + " défaut recherchables. Unicode NFKC, espaces normalisés, casse ignorée. Les"
                    + " doublons restent à confirmer ; comparaison limitée aux objets analysés.");
        return report;
    }

    private static boolean allowed(ModelManager manager, String type, List<TYPEREF> references) {
        return references.stream().anyMatch(ref -> manager.isTypeOrSubtype(type, ref.getNAME()));
    }

    private static List<String> duplicateSignature(Node node, ModelManager manager) {
        String name = objectType(node);
        OBJECTTYPE type = manager.getObjectType(name);
        if (type == null) return null;
        Set<String> fields = new TreeSet<>();
        if (type.getREPRESENTATIVEATTRIBUTES() != null)
            type.getREPRESENTATIVEATTRIBUTES()
                    .getATTRIBUTEREF()
                    .forEach(ref -> fields.add(ref.getNAME()));
        if (fields.isEmpty())
            manager.getAttributeDefinitionMap(type).values().stream()
                    .filter(attr -> attr.isSEARCHABLE())
                    .forEach(attr -> fields.add(attr.getNAME()));
        if (fields.isEmpty()) return null;
        List<String> signature = new ArrayList<>();
        signature.add(name);
        for (String field : fields) {
            String value =
                    node.get(field).isNull() ? "" : String.valueOf(node.get(field).asObject());
            value =
                    Normalizer.normalize(value, Normalizer.Form.NFKC)
                            .strip()
                            .replaceAll("(?U)\\s+", " ")
                            .toLowerCase(Locale.ROOT);
            if (value.isEmpty()) return null;
            signature.add(field);
            signature.add(value);
        }
        return signature;
    }

    private static void validationIssues(
            List<Map<String, Object>> issues,
            Map<String, Integer> counts,
            String entity,
            long id,
            String type,
            ValidationResult validation) {
        validation
                .getErrors()
                .forEach(
                        error ->
                                issue(
                                        issues,
                                        counts,
                                        "schema_drift",
                                        entity,
                                        id,
                                        type,
                                        "error",
                                        error.getMessage(),
                                        List.of()));
        validation
                .getWarnings()
                .forEach(
                        warning ->
                                issue(
                                        issues,
                                        counts,
                                        "schema_drift",
                                        entity,
                                        id,
                                        type,
                                        "warning",
                                        warning.getMessage(),
                                        List.of()));
    }

    private static void issue(
            List<Map<String, Object>> issues,
            Map<String, Integer> counts,
            String category,
            String entity,
            long id,
            String type,
            String severity,
            String reason,
            List<Long> related) {
        counts.compute(category, (ignored, count) -> count + 1);
        Map<String, Object> sample =
                Map.of(
                        "category",
                        category,
                        "entity",
                        entity,
                        "id",
                        id,
                        "type",
                        type,
                        "severity",
                        severity,
                        "reason",
                        reason,
                        "relatedIds",
                        related);
        if (issues.size() < MAX_SAMPLES) {
            issues.add(sample);
        } else {
            // A long list of isolated objects must not hide later schema errors.
            int lowest = -1;
            int priority = severityPriority(severity);
            for (int index = 0; index < issues.size(); index++) {
                int existing = severityPriority(String.valueOf(issues.get(index).get("severity")));
                if (existing < priority) {
                    priority = existing;
                    lowest = index;
                }
            }
            if (lowest >= 0) issues.set(lowest, sample);
        }
    }

    private static int severityPriority(String severity) {
        return switch (severity) {
            case "error" -> 2;
            case "warning" -> 1;
            default -> 0;
        };
    }

    private static List<ATTRIBUTE> dtoAttributes(Map<String, Object> properties) {
        var result = new ArrayList<ATTRIBUTE>();
        attributeRows(properties)
                .forEach(
                        row -> {
                            ATTRIBUTE attr = new ATTRIBUTE();
                            attr.setKEY(row.get("key"));
                            attr.setVALUE(row.get("value"));
                            result.add(attr);
                        });
        return result;
    }

    private static List<Map<String, String>> attributeRows(Map<String, Object> properties) {
        var result = new ArrayList<Map<String, String>>();
        properties.entrySet().stream()
                .filter(entry -> !CypherUtils.isReservedProperty(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry ->
                                result.add(
                                        Map.of(
                                                "key",
                                                entry.getKey(),
                                                "value",
                                                String.valueOf(entry.getValue()))));
        return result;
    }

    private static String objectType(Node node) {
        if (!node.get("type").isNull()) return node.get("type").asString();
        for (String label : node.labels()) if (!"DataObject".equals(label)) return label;
        return "Object";
    }

    private static String linkType(Relationship rel) {
        return rel.get("linkType").asString(rel.type());
    }

    private static Map<String, Object> objectRow(Node node) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", node.id());
        row.put("type", objectType(node));
        row.put("attributes", attributeRows(node.asMap()));
        for (String field : List.of("dataId", "uuid"))
            if (!node.get(field).isNull()) row.put(field, node.get(field).asObject());
        return row;
    }

    private static Map<String, Object> linkRow(Relationship rel) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rel.id());
        row.put("fromId", rel.startNodeId());
        row.put("toId", rel.endNodeId());
        row.put("type", linkType(rel));
        row.put("linkType", linkType(rel));
        row.put("relationshipType", rel.type());
        row.put("directed", rel.get("directed").asBoolean(true));
        row.put("attributes", attributeRows(rel.asMap()));
        if (!rel.get("uuid").isNull()) row.put("uuid", rel.get("uuid").asString());
        return row;
    }
}
