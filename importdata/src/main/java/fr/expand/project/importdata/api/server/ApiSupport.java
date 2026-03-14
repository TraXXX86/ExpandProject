package fr.expand.project.importdata.api.server;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fr.expand.project.importdata.dto.DataPackAttribute;
import fr.expand.project.importdata.dto.generated.DATAS;
import spark.Response;

final class ApiSupport {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    String toJson(Object payload) {
        return GSON.toJson(payload);
    }

    String error(Response response, int status, String message) {
        response.status(status);
        return toJson(Map.of("error", message));
    }

    Map<String, Object> readJsonBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = GSON.fromJson(body, Map.class);
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    String getString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    Integer getInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    Long getLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    boolean getBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }

    List<DataPackAttribute> readAttributes(Object value) {
        List<DataPackAttribute> attributes = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return attributes;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object keyValue = map.get("key");
            if (keyValue == null) {
                continue;
            }
            String key = keyValue.toString();
            String attributeValue = map.get("value") == null ? "" : map.get("value").toString();
            attributes.add(new DataPackAttribute(key, attributeValue));
        }
        return attributes;
    }

    List<DataPackAttribute> readAttributesFromMap(Map<String, Object> attributes) {
        List<DataPackAttribute> rows = new ArrayList<>();
        if (attributes == null) {
            return rows;
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            rows.add(new DataPackAttribute(key, entry.getValue() == null ? "" : entry.getValue().toString()));
        }
        return rows;
    }

    Map<String, Object> attributesToMap(List<DataPackAttribute> attributes) {
        Map<String, Object> map = new HashMap<>();
        if (attributes == null) {
            return map;
        }
        for (DataPackAttribute attribute : attributes) {
            if (attribute == null || attribute.getKEY() == null || attribute.getKEY().isBlank()) {
                continue;
            }
            map.put(attribute.getKEY(), attribute.getVALUE() == null ? "" : attribute.getVALUE());
        }
        return map;
    }

    List<Map<String, Object>> readPermissionList(Object value) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return rows;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            String modelKey = map.get("modelKey") == null ? null : map.get("modelKey").toString();
            if (modelKey == null || modelKey.isBlank()) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("modelKey", modelKey);
            row.put("visible", getBoolean(map.get("visible")));
            row.put("canRead", getBoolean(map.get("canRead")));
            row.put("canCreate", getBoolean(map.get("canCreate")));
            row.put("canUpdate", getBoolean(map.get("canUpdate")));
            row.put("canDelete", getBoolean(map.get("canDelete")));
            rows.add(row);
        }
        return rows;
    }

    String sanitizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String value = username.trim();
        if (value.isBlank()) {
            return null;
        }
        if (!value.matches("[A-Za-z0-9._-]+")) {
            return null;
        }
        return value;
    }

    String readMultipartText(HttpServletRequest request, String partName) throws Exception {
        configureMultipart(request);
        Part part = request.getPart(partName);
        if (part == null) {
            return null;
        }
        try (InputStream input = part.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            part.delete();
        }
    }

    String readMultipartField(HttpServletRequest request, String partName) throws Exception {
        configureMultipart(request);
        Part part = request.getPart(partName);
        if (part == null) {
            return null;
        }
        try (InputStream input = part.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } finally {
            part.delete();
        }
    }

    void configureMultipart(HttpServletRequest request) {
        MultipartConfigElement config = new MultipartConfigElement(System.getProperty("java.io.tmpdir"));
        request.setAttribute("org.eclipse.jetty.multipartConfig", config);
    }

    DATAS loadDataFromXml(String xml) throws Exception {
        var context = jakarta.xml.bind.JAXBContext.newInstance(DATAS.class);
        var unmarshaller = context.createUnmarshaller();
        return (DATAS) unmarshaller.unmarshal(new java.io.StringReader(xml));
    }

    int countObjects(DATAS data) {
        if (data.getOBJECTS() == null || data.getOBJECTS().getOBJECT() == null) {
            return 0;
        }
        return data.getOBJECTS().getOBJECT().size();
    }

    int countLinks(DATAS data) {
        if (data.getLINKS() == null || data.getLINKS().getLINK() == null) {
            return 0;
        }
        return data.getLINKS().getLINK().size();
    }
}
