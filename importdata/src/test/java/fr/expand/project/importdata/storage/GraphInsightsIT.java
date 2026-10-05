package fr.expand.project.importdata.storage;

import fr.expand.project.importdata.dao.Neo4jDriverProvider;
import fr.expand.project.importdata.data.GraphInsightsStore;
import fr.expand.project.importdata.data.Neo4jDataStore;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.Neo4jModelStore;

import org.junit.*;

import java.util.*;

/** Isolated fixtures cover direction, depth, privacy, schema drift and response budgets. */
public class GraphInsightsIT {
    private String key;
    private ModelManager manager;
    private Neo4jModelStore models;
    private Neo4jDataStore data;
    private final GraphInsightsStore insights = new GraphInsightsStore();

    @Before
    public void setup() throws Exception {
        String name = "GraphInsightsIT_" + UUID.randomUUID().toString().replace("-", "");
        key = name + ":1";
        manager = new ModelManager();
        manager.loadModelFromXml(
                """
<DATA_MODEL NAME="%s" VERSION="1"><OBJECT_TYPES>
<OBJECT_TYPE NAME="PERSON"><ATTRIBUTE_DEFINITIONS>
<ATTRIBUTE_DEFINITION NAME="NAME" TYPE="STRING" REQUIRED="true" SEARCHABLE="true"/>
<ATTRIBUTE_DEFINITION NAME="AGE" TYPE="INTEGER"/>
</ATTRIBUTE_DEFINITIONS></OBJECT_TYPE>
<OBJECT_TYPE NAME="PLACE"><ATTRIBUTE_DEFINITIONS>
<ATTRIBUTE_DEFINITION NAME="NAME" TYPE="STRING" SEARCHABLE="true"/>
</ATTRIBUTE_DEFINITIONS></OBJECT_TYPE></OBJECT_TYPES><LINK_TYPES>
<LINK_TYPE NAME="FOLLOWS" DIRECTED="true"><SOURCE_TYPES><TYPE_REF NAME="PERSON"/></SOURCE_TYPES>
<TARGET_TYPES><TYPE_REF NAME="PERSON"/></TARGET_TYPES><ATTRIBUTE_DEFINITIONS>
<ATTRIBUTE_DEFINITION NAME="WEIGHT" TYPE="INTEGER"/></ATTRIBUTE_DEFINITIONS></LINK_TYPE>
<LINK_TYPE NAME="KNOWS" DIRECTED="false"><SOURCE_TYPES><TYPE_REF NAME="PERSON"/></SOURCE_TYPES>
<TARGET_TYPES><TYPE_REF NAME="PERSON"/></TARGET_TYPES></LINK_TYPE>
</LINK_TYPES></DATA_MODEL>
"""
                        .formatted(name));
        models = new Neo4jModelStore();
        models.createModel(manager.getCurrentModel(), manager.getCurrentModelXml());
        data = new Neo4jDataStore();
    }

    @After
    public void cleanup() {
        if (models != null) models.deleteModelAndDataByKey(key);
        if (models == null) return;
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            session.run("MATCH (n:NotData {modelKey:$key}) DETACH DELETE n", Map.of("key", key))
                    .consume();
            session.run(
                            "MATCH (n:DataObject {modelKey:$key}) DETACH DELETE n",
                            Map.of("key", key + "_foreign"))
                    .consume();
        }
    }

    private long person(String name) {
        return data.createObject(key, "PERSON", Map.of("NAME", name));
    }

    private long link(long a, long b, String type) {
        return data.createLink(key, a, b, type, type.equals("FOLLOWS"), Map.of());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> report, String field) {
        return (List<Map<String, Object>>) report.get(field);
    }

    private Map<String, Object> paths(
            long from, long to, int depth, boolean directed, String... types) {
        return insights.paths(key, from, to, depth, Set.of(types), directed, manager);
    }

    @Test
    public void shortestPathsRespectDirectionDepthTypesAndKeepLinkMetadata() {
        long a = person("A"), b = person("B"), c = person("C");
        link(a, b, "FOLLOWS");
        long bc = link(b, c, "KNOWS");
        var found = paths(a, c, 2, true);
        Assert.assertEquals(1, rows(found, "paths").size());
        var path = rows(found, "paths").get(0);
        Assert.assertEquals(3, rows(path, "objects").size());
        Assert.assertEquals(bc, rows(path, "links").get(1).get("id"));
        Assert.assertNotNull(rows(path, "links").get(1).get("uuid"));
        Assert.assertEquals(false, rows(path, "links").get(1).get("directed"));
        Assert.assertTrue(rows(paths(a, c, 1, true), "paths").isEmpty());
        Assert.assertTrue(rows(paths(c, a, 2, true), "paths").isEmpty());
        Assert.assertEquals(1, rows(paths(c, a, 2, false), "paths").size());
        Assert.assertTrue(rows(paths(a, c, 3, false, "FOLLOWS"), "paths").isEmpty());
        Assert.assertEquals(1, rows(paths(c, b, 1, true, "KNOWS"), "paths").size());
        Assert.assertEquals(1, rows(paths(a, a, 1, true), "paths").size());
        link(a, c, "FOLLOWS");
        Assert.assertEquals(2, rows(rows(paths(a, c, 3, true), "paths").get(0), "objects").size());
        Assert.assertThrows(IllegalArgumentException.class, () -> paths(a, c, 7, true));
        Assert.assertThrows(IllegalArgumentException.class, () -> paths(a, c, 2, true, "UNKNOWN"));
    }

    @Test
    public void pathsNeverUseForeignOrNonDataObjectIntermediates() {
        long a = person("A"), b = person("B");
        long foreign;
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            foreign =
                    session.run(
                                    "MATCH (a:DataObject),(b:DataObject) WHERE id(a)=$a AND"
                                        + " id(b)=$b CREATE (x:DataObject"
                                        + " {modelKey:$foreign,type:'PERSON',NAME:'Private'})"
                                        + " CREATE (a)-[:FOLLOWS]->(x)-[:FOLLOWS]->(b) RETURN id(x)"
                                        + " AS id",
                                    Map.of("a", a, "b", b, "foreign", key + "_foreign"))
                            .single()
                            .get("id")
                            .asLong();
            session.run(
                            "MATCH (a:DataObject),(b:DataObject) WHERE id(a)=$a AND id(b)=$b CREATE"
                                    + " (x:NotData {modelKey:$key}) CREATE"
                                    + " (a)-[:FOLLOWS]->(x)-[:FOLLOWS]->(b)",
                            Map.of("a", a, "b", b, "key", key))
                    .consume();
        }
        Assert.assertTrue(rows(paths(a, b, 6, true), "paths").isEmpty());
        Assert.assertThrows(NoSuchElementException.class, () -> paths(a, foreign, 6, false));
        // Explicitly remove the non-data corruption fixture; production model deletion does not own
        // it.
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            session.run("MATCH (n:NotData {modelKey:$key}) DETACH DELETE n", Map.of("key", key))
                    .consume();
        }
    }

    @Test
    public void pathCountAndTraversalBudgetsAreExplicit() {
        long a = person("A"), b = person("B");
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            session.run(
                            "MATCH (a:DataObject),(b:DataObject) WHERE id(a)=$a AND id(b)=$b UNWIND"
                                    + " range(1,25) AS i CREATE (x:DataObject"
                                    + " {modelKey:$key,type:'PERSON',NAME:toString(i)}) CREATE"
                                    + " (a)-[:FOLLOWS {directed:true}]->(x)-[:FOLLOWS"
                                    + " {directed:true}]->(b)",
                            Map.of("a", a, "b", b, "key", key))
                    .consume();
        }
        var result = paths(a, b, 2, true);
        Assert.assertEquals(GraphInsightsStore.MAX_PATHS, rows(result, "paths").size());
        Assert.assertEquals(true, result.get("truncated"));
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            session.run(
                            "MATCH (a:DataObject) WHERE id(a)=$a UNWIND range(1,2100) AS i CREATE"
                                + " (x:DataObject {modelKey:$key,type:'PERSON',NAME:toString(i)})"
                                + " CREATE (a)-[:FOLLOWS {directed:true}]->(x)",
                            Map.of("a", a, "key", key))
                    .consume();
        }
        result = paths(a, b, 2, true);
        Assert.assertEquals(true, result.get("truncated"));
        Assert.assertTrue(
                ((Number) result.get("visitedObjects")).intValue()
                        <= GraphInsightsStore.MAX_OBJECTS);
        Assert.assertTrue(
                ((Number) result.get("scannedEdges")).intValue() <= GraphInsightsStore.MAX_LINKS);
    }

    @Test
    public void edgeScanBudgetAndQualityErrorSamplesStayBounded() {
        long a = person("A"), b = person("B"), unreachable = person("Unreachable");
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            session.run(
                            "MATCH (a:DataObject),(b:DataObject) WHERE id(a)=$a AND id(b)=$b UNWIND"
                                    + " range(1,10020) AS i CREATE (a)-[:FOLLOWS"
                                    + " {directed:true,WEIGHT:'bad'}]->(b)",
                            Map.of("a", a, "b", b))
                    .consume();
        }
        var result = paths(a, unreachable, 2, true);
        Assert.assertTrue(rows(result, "paths").isEmpty());
        Assert.assertEquals(true, result.get("truncated"));
        Assert.assertEquals(GraphInsightsStore.MAX_LINKS, result.get("scannedEdges"));
        var quality = insights.quality(key, manager);
        Assert.assertEquals(true, quality.get("truncated"));
        Assert.assertEquals(false, quality.get("linkCountExact"));
        Assert.assertEquals(GraphInsightsStore.MAX_LINKS, quality.get("scannedLinks"));
        Assert.assertEquals(GraphInsightsStore.MAX_SAMPLES, rows(quality, "issues").size());
        Assert.assertTrue(
                rows(quality, "issues").stream().allMatch(i -> i.get("severity").equals("error")));
    }

    @Test
    public void qualityReportsNormalizedDuplicatesInvalidAttributesAndEndpoints() {
        long a = person("  Alice   SMITH "), b = person("alice smith"), c = person("Other");
        long place = data.createObject(key, "PLACE", Map.of("NAME", "somewhere"));
        long bad = link(a, place, "FOLLOWS");
        data.updateObject(key, c, Map.of("AGE", "not-a-number", "REMOVED_FIELD", "old"));
        data.updateLink(key, bad, Map.of("WEIGHT", "bad"));
        var quality = insights.quality(key, manager);
        Assert.assertEquals(4, quality.get("objectCount"));
        Assert.assertEquals(1, quality.get("linkCount"));
        Assert.assertEquals(false, quality.get("truncated"));
        var issues = rows(quality, "issues");
        Assert.assertTrue(
                issues.stream()
                        .anyMatch(
                                i ->
                                        i.get("category").equals("potential_duplicate")
                                                && Set.of(a, b).contains(i.get("id"))));
        Assert.assertTrue(
                issues.stream()
                        .anyMatch(
                                i ->
                                        i.get("category").equals("isolated")
                                                && i.get("id").equals(c)));
        Assert.assertTrue(
                issues.stream()
                        .anyMatch(i -> i.get("id").equals(c) && i.get("severity").equals("error")));
        Assert.assertTrue(
                issues.stream()
                        .anyMatch(
                                i ->
                                        i.get("id").equals(c)
                                                && i.get("reason")
                                                        .toString()
                                                        .contains("REMOVED_FIELD")));
        Assert.assertTrue(
                issues.stream()
                        .anyMatch(
                                i ->
                                        i.get("entity").equals("link")
                                                && i.get("reason")
                                                        .toString()
                                                        .contains("source ou cible")));
        Assert.assertTrue(
                issues.stream()
                        .anyMatch(
                                i ->
                                        i.get("entity").equals("link")
                                                && i.get("reason").toString().contains("WEIGHT")));
    }

    @Test
    public void qualityDoesNotMergeTypesOrBlankValuesAndCapsItsScan() {
        person(" ");
        person(" ");
        person("same");
        data.createObject(key, "PLACE", Map.of("NAME", "same"));
        var quality = insights.quality(key, manager);
        Assert.assertFalse(
                rows(quality, "issues").stream()
                        .anyMatch(i -> i.get("category").equals("potential_duplicate")));
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            session.run(
                            "UNWIND range(1,2100) AS i CREATE (:DataObject"
                                    + " {modelKey:$key,type:'PERSON',NAME:toString(i)})",
                            Map.of("key", key))
                    .consume();
        }
        quality = insights.quality(key, manager);
        Assert.assertEquals(true, quality.get("truncated"));
        Assert.assertEquals(false, quality.get("objectCountExact"));
        Assert.assertEquals(GraphInsightsStore.MAX_OBJECTS, quality.get("scannedObjects"));
        Assert.assertEquals(GraphInsightsStore.MAX_SAMPLES, rows(quality, "issues").size());
        Assert.assertEquals(true, quality.get("samplesTruncated"));
    }
}
