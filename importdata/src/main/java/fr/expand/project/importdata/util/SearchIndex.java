package fr.expand.project.importdata.util;

import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.generated.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Native fulltext index content is derived exclusively from model-declared searchable fields. */
public final class SearchIndex {
    public static final String NAME = "data_object_search";
    public static final String EXPRESSION =
            "reduce(text='', field IN coalesce($searchFields[coalesce(n.type,head([label IN"
                + " labels(n) WHERE label IN keys($searchFields)]))],[]) | text+'"
                + " '+coalesce(toString(n[field]),''))";

    private SearchIndex() {}

    public static Map<String, List<String>> fieldsFromXml(String xml) {
        if (xml == null || xml.isBlank()) return Map.of();
        try {
            var manager = new ModelManager();
            return fields(manager.loadModelFromXml(xml));
        } catch (jakarta.xml.bind.JAXBException e) {
            throw new IllegalStateException("Stored model XML is invalid", e);
        }
    }

    public static Map<String, List<String>> fields(DATAMODEL model) {
        if (model == null || model.getOBJECTTYPES() == null) return Map.of();
        Map<String, OBJECTTYPE> types = new LinkedHashMap<>();
        for (var type : model.getOBJECTTYPES().getOBJECTTYPE()) types.put(type.getNAME(), type);
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (var type : types.values()) {
            List<OBJECTTYPE> ancestry = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            for (var ancestor = type;
                    ancestor != null && visited.add(ancestor.getNAME());
                    ancestor = types.get(ancestor.getPARENT())) ancestry.add(ancestor);
            Collections.reverse(ancestry);
            Map<String, ATTRIBUTEDEFINITION> definitions = new LinkedHashMap<>();
            for (var ancestor : ancestry)
                if (ancestor.getATTRIBUTEDEFINITIONS() != null)
                    for (var field : ancestor.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION())
                        definitions.put(field.getNAME(), field);
            result.put(
                    type.getNAME(),
                    definitions.values().stream()
                            .filter(
                                    field ->
                                            field.isSEARCHABLE()
                                                    && !CypherUtils.isReservedProperty(
                                                            field.getNAME()))
                            .map(ATTRIBUTEDEFINITION::getNAME)
                            .toList());
        }
        return result;
    }

    public static String text(
            String type, Map<String, Object> properties, Map<String, List<String>> fields) {
        StringJoiner text = new StringJoiner(" ");
        for (String field : fields.getOrDefault(type, List.of())) {
            Object value = properties.get(field);
            if (value != null) text.add(value.toString());
        }
        return text.toString();
    }

    public static String revision(String xml) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Treat Lucene operators and field syntax as literal input, preserving parameter-only Cypher.
     */
    public static String literalQuery(String query) {
        StringJoiner terms = new StringJoiner(" AND ");
        for (String term : query.trim().split("\\s+")) {
            if (term.isEmpty()) continue;
            StringBuilder literal = new StringBuilder();
            for (char c : term.toCharArray()) {
                if ("+-!(){}[]^\"~*?:\\/&|".indexOf(c) >= 0) literal.append('\\');
                literal.append(c);
            }
            // Quoting avoids AND/OR/NOT being interpreted as user-supplied boolean operators.
            terms.add("\"" + literal + "\"");
        }
        return terms.toString();
    }
}
