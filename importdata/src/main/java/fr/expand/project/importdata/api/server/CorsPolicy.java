package fr.expand.project.importdata.api.server;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class CorsPolicy {

    private static final String DEFAULT_ALLOWED_METHODS = "GET,POST,PUT,DELETE,OPTIONS";
    private static final String DEFAULT_MAX_AGE_SECONDS = "600";
    private static final List<String> DEFAULT_ALLOWED_HEADERS = List.of(
        "Content-Type",
        "Authorization",
        "Accept",
        "Origin",
        "X-Session-Token"
    );
    private static final Set<String> DEFAULT_ALLOWED_HEADERS_LOWER = DEFAULT_ALLOWED_HEADERS
        .stream()
        .map(header -> header.toLowerCase(Locale.ROOT))
        .collect(Collectors.toCollection(LinkedHashSet::new));
    private static final List<String> DEFAULT_DEV_ORIGINS = List.of(
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:3000",
        "http://127.0.0.1:3000"
    );

    private final String environment;
    private final Set<String> allowedOrigins;
    private final boolean allowCredentials;

    private CorsPolicy(String environment, Set<String> allowedOrigins, boolean allowCredentials) {
        this.environment = environment;
        this.allowedOrigins = Collections.unmodifiableSet(new LinkedHashSet<>(allowedOrigins));
        this.allowCredentials = allowCredentials;
    }

    static CorsPolicy load() {
        return build(CorsPolicy::readSetting);
    }

    static CorsPolicy fromSettings(Map<String, String> settings) {
        return build(settings::get);
    }

    private static CorsPolicy build(SettingLookup lookup) {
        String environment = normalizeEnvironment(firstNonBlank(
            lookup.read("APP_ENV"),
            lookup.read("ENVIRONMENT"),
            "dev"
        ));
        String allowedOriginsValue = firstNonBlank(
            lookup.read("CORS_ALLOWED_ORIGINS"),
            lookup.read("CORS_ALLOWED_ORIGINS_" + environment.toUpperCase(Locale.ROOT)),
            defaultOriginsFor(environment)
        );
        boolean allowCredentials = parseBoolean(lookup.read("CORS_ALLOW_CREDENTIALS"), true);
        return new CorsPolicy(environment, parseOrigins(allowedOriginsValue), allowCredentials);
    }

    String getEnvironment() {
        return environment;
    }

    boolean isAllowCredentials() {
        return allowCredentials;
    }

    boolean isOriginAllowed(String origin) {
        String normalizedOrigin = normalizeOrigin(origin);
        return normalizedOrigin != null && allowedOrigins.contains(normalizedOrigin);
    }

    ResolvedCors resolve(String origin, String requestedHeaders) {
        String normalizedOrigin = normalizeOrigin(origin);
        if (normalizedOrigin == null || !allowedOrigins.contains(normalizedOrigin)) {
            return ResolvedCors.disallowed();
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Vary", requestedHeaders == null || requestedHeaders.isBlank()
            ? "Origin"
            : "Origin,Access-Control-Request-Headers");
        headers.put("Access-Control-Allow-Origin", normalizedOrigin);
        headers.put("Access-Control-Allow-Methods", DEFAULT_ALLOWED_METHODS);
        headers.put("Access-Control-Allow-Headers", resolveAllowedHeaders(requestedHeaders));
        headers.put("Access-Control-Max-Age", DEFAULT_MAX_AGE_SECONDS);
        if (allowCredentials) {
            headers.put("Access-Control-Allow-Credentials", "true");
        }
        return new ResolvedCors(true, headers);
    }

    private static String readSetting(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String normalizeEnvironment(String environment) {
        return environment == null || environment.isBlank()
            ? "dev"
            : environment.trim().toLowerCase(Locale.ROOT);
    }

    private static String defaultOriginsFor(String environment) {
        if ("dev".equals(environment)) {
            return String.join(",", DEFAULT_DEV_ORIGINS);
        }
        return "";
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static Set<String> parseOrigins(String allowedOriginsValue) {
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        if (allowedOriginsValue == null || allowedOriginsValue.isBlank()) {
            return origins;
        }
        for (String candidate : allowedOriginsValue.split(",")) {
            String normalized = normalizeOrigin(candidate);
            if (normalized != null) {
                origins.add(normalized);
            }
        }
        return origins;
    }

    private static String resolveAllowedHeaders(String requestedHeaders) {
        if (requestedHeaders == null || requestedHeaders.isBlank()) {
            return String.join(",", DEFAULT_ALLOWED_HEADERS);
        }

        List<String> filteredHeaders = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String requestedHeader : requestedHeaders.split(",")) {
            String trimmedHeader = requestedHeader == null ? null : requestedHeader.trim();
            if (trimmedHeader == null || trimmedHeader.isBlank()) {
                continue;
            }
            String normalizedHeader = trimmedHeader.toLowerCase(Locale.ROOT);
            if (DEFAULT_ALLOWED_HEADERS_LOWER.contains(normalizedHeader) && seen.add(normalizedHeader)) {
                filteredHeaders.add(trimmedHeader);
            }
        }

        if (filteredHeaders.isEmpty()) {
            return String.join(",", DEFAULT_ALLOWED_HEADERS);
        }
        return String.join(",", filteredHeaders);
    }

    private static String normalizeOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return null;
        }
        String trimmedOrigin = stripTrailingSlash(origin.trim());
        if (trimmedOrigin.isBlank() || "null".equalsIgnoreCase(trimmedOrigin)) {
            return null;
        }
        try {
            URI uri = URI.create(trimmedOrigin);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return trimmedOrigin;
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return uri.getPort() < 0 ? scheme + "://" + host : scheme + "://" + host + ":" + uri.getPort();
        } catch (IllegalArgumentException ignored) {
            return trimmedOrigin;
        }
    }

    private static String stripTrailingSlash(String value) {
        String normalizedValue = value;
        while (normalizedValue.endsWith("/")) {
            normalizedValue = normalizedValue.substring(0, normalizedValue.length() - 1);
        }
        return normalizedValue;
    }

    private interface SettingLookup {
        String read(String key);
    }

    static final class ResolvedCors {

        private final boolean allowed;
        private final Map<String, String> headers;

        private ResolvedCors(boolean allowed, Map<String, String> headers) {
            this.allowed = allowed;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        }

        static ResolvedCors disallowed() {
            return new ResolvedCors(false, Collections.emptyMap());
        }

        boolean isAllowed() {
            return allowed;
        }

        Map<String, String> getHeaders() {
            return headers;
        }
    }
}
