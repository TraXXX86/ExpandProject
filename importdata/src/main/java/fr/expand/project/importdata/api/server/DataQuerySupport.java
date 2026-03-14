package fr.expand.project.importdata.api.server;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import spark.Request;

final class DataQuerySupport {

    static final int DEFAULT_PAGE_SIZE = 25;
    static final int DEFAULT_LOOKUP_SIZE = 30;
    static final int MAX_PAGE_SIZE = 200;

    private DataQuerySupport() {
    }

    static int parseNonNegativeInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(parsed, 0);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static int parseLimit(String value, int defaultValue) {
        int parsed = parseNonNegativeInt(value, defaultValue);
        if (parsed <= 0) {
            return defaultValue;
        }
        return Math.min(parsed, MAX_PAGE_SIZE);
    }

    static String normalizeOperator(String value) {
        return "equals".equalsIgnoreCase(value == null ? "" : value.trim()) ? "equals" : "contains";
    }

    static List<String> readMultiValueParam(Request request, String paramName) {
        if (request == null || paramName == null || paramName.isBlank()) {
            return List.of();
        }
        return normalizeMultiValues(request.queryParamsValues(paramName), request.queryParams(paramName));
    }

    static List<String> normalizeMultiValues(String[] repeatedValues, String singleValue) {
        Set<String> values = new LinkedHashSet<>();
        addTokens(values, repeatedValues);
        addTokens(values, singleValue);
        return new ArrayList<>(values);
    }

    private static void addTokens(Set<String> values, String[] rawValues) {
        if (rawValues == null) {
            return;
        }
        for (String rawValue : rawValues) {
            addTokens(values, rawValue);
        }
    }

    private static void addTokens(Set<String> values, String rawValue) {
        if (values == null || rawValue == null || rawValue.isBlank()) {
            return;
        }
        String[] tokens = rawValue.split(",");
        for (String token : tokens) {
            String normalized = token == null ? "" : token.trim();
            if (!normalized.isBlank()) {
                values.add(normalized);
            }
        }
    }
}
