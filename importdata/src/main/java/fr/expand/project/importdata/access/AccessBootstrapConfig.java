package fr.expand.project.importdata.access;

/**
 * Environment-driven bootstrap configuration for the initial platform admin.
 */
final class AccessBootstrapConfig {

    private static final String DEFAULT_MODE = "production";
    private static final String DEFAULT_ADMIN_DISPLAY_NAME = "Administrateur";

    private final String mode;
    private final String adminDisplayName;
    private final String adminPassword;

    private AccessBootstrapConfig(String mode, String adminDisplayName, String adminPassword) {
        this.mode = normalizeMode(mode);
        this.adminDisplayName = normalizeDisplayName(adminDisplayName);
        this.adminPassword = normalizeSecret(adminPassword);
    }

    static AccessBootstrapConfig load() {
        return new AccessBootstrapConfig(
            readSetting("ACCESS_BOOTSTRAP_MODE", null, DEFAULT_MODE),
            readSetting("ACCESS_BOOTSTRAP_ADMIN_DISPLAY_NAME", null, DEFAULT_ADMIN_DISPLAY_NAME),
            readSetting("ACCESS_BOOTSTRAP_ADMIN_PASSWORD", null, null)
        );
    }

    String getMode() {
        return mode;
    }

    boolean isDevelopmentMode() {
        return "development".equals(mode);
    }

    boolean isProductionMode() {
        return !isDevelopmentMode();
    }

    String getAdminDisplayName() {
        return adminDisplayName;
    }

    String getAdminPassword() {
        return adminPassword;
    }

    boolean hasConfiguredPassword() {
        return adminPassword != null && !adminPassword.isBlank();
    }

    private static String normalizeMode(String rawMode) {
        if (rawMode == null) {
            return DEFAULT_MODE;
        }
        String value = rawMode.trim().toLowerCase();
        if (value.isEmpty()) {
            return DEFAULT_MODE;
        }
        if ("dev".equals(value)) {
            return "development";
        }
        if ("prod".equals(value)) {
            return "production";
        }
        if ("development".equals(value) || "production".equals(value)) {
            return value;
        }
        return DEFAULT_MODE;
    }

    private static String normalizeDisplayName(String rawDisplayName) {
        if (rawDisplayName == null || rawDisplayName.isBlank()) {
            return DEFAULT_ADMIN_DISPLAY_NAME;
        }
        return rawDisplayName.trim();
    }

    private static String normalizeSecret(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            return null;
        }
        return rawSecret;
    }

    private static String readSetting(String envKey, String fallbackEnvKey, String defaultValue) {
        String value = System.getProperty(envKey);
        if (value == null || value.isBlank()) {
            value = System.getenv(envKey);
        }
        if ((value == null || value.isBlank()) && fallbackEnvKey != null) {
            value = System.getProperty(fallbackEnvKey);
            if (value == null || value.isBlank()) {
                value = System.getenv(fallbackEnvKey);
            }
        }
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
