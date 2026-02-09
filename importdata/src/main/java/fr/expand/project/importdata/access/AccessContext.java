package fr.expand.project.importdata.access;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccessContext {

    private final String sessionToken;
    private final String actorUsername;
    private final String actorDisplayName;
    private final boolean actorPlatformAdmin;
    private final String username;
    private final String displayName;
    private final boolean portalUser;
    private final boolean portalModelAdmin;
    private final boolean platformAdmin;
    private final boolean impersonating;
    private final long expiresAt;
    private final Map<String, ModelPermission> permissionsByModelKey;

    public AccessContext(String sessionToken, Map<String, Object> actorUser, Map<String, Object> effectiveUser,
        List<Map<String, Object>> permissions, boolean impersonating, long expiresAt) {
        this.sessionToken = sessionToken;
        this.actorUsername = getString(actorUser, "username");
        this.actorDisplayName = getString(actorUser, "displayName");
        this.actorPlatformAdmin = getBoolean(actorUser, "platformAdmin");

        this.username = getString(effectiveUser, "username");
        this.displayName = getString(effectiveUser, "displayName");
        this.portalUser = getBoolean(effectiveUser, "portalUser");
        this.portalModelAdmin = getBoolean(effectiveUser, "portalModelAdmin");
        this.platformAdmin = getBoolean(effectiveUser, "platformAdmin");

        this.impersonating = impersonating;
        this.expiresAt = expiresAt;
        this.permissionsByModelKey = new HashMap<>();

        if (permissions != null) {
            for (Map<String, Object> permissionRow : permissions) {
                if (permissionRow == null) {
                    continue;
                }
                String modelKey = getString(permissionRow, "modelKey");
                if (modelKey == null || modelKey.isBlank()) {
                    continue;
                }
                ModelPermission permission = new ModelPermission(
                    getBoolean(permissionRow, "visible"),
                    getBoolean(permissionRow, "canRead"),
                    getBoolean(permissionRow, "canCreate"),
                    getBoolean(permissionRow, "canUpdate"),
                    getBoolean(permissionRow, "canDelete")
                );
                permissionsByModelKey.put(modelKey, permission);
            }
        }
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActorDisplayName() {
        return actorDisplayName;
    }

    public boolean isActorPlatformAdmin() {
        return actorPlatformAdmin;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isImpersonating() {
        return impersonating;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isPortalUser() {
        return portalUser || platformAdmin;
    }

    public boolean isPortalModelAdmin() {
        return portalModelAdmin || platformAdmin;
    }

    public boolean isPlatformAdmin() {
        return platformAdmin;
    }

    public boolean canViewModel(String modelKey) {
        if (platformAdmin) {
            return true;
        }
        ModelPermission permission = permissionsByModelKey.get(modelKey);
        return permission != null && permission.visible;
    }

    public boolean canReadData(String modelKey) {
        if (platformAdmin) {
            return true;
        }
        ModelPermission permission = permissionsByModelKey.get(modelKey);
        return permission != null && permission.visible && permission.canRead;
    }

    public boolean canCreateData(String modelKey) {
        if (platformAdmin) {
            return true;
        }
        ModelPermission permission = permissionsByModelKey.get(modelKey);
        return permission != null && permission.visible && permission.canCreate;
    }

    public boolean canUpdateData(String modelKey) {
        if (platformAdmin) {
            return true;
        }
        ModelPermission permission = permissionsByModelKey.get(modelKey);
        return permission != null && permission.visible && permission.canUpdate;
    }

    public boolean canDeleteData(String modelKey) {
        if (platformAdmin) {
            return true;
        }
        ModelPermission permission = permissionsByModelKey.get(modelKey);
        return permission != null && permission.visible && permission.canDelete;
    }

    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String value = authorizationHeader.trim();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = value.substring(7).trim();
        return token.isBlank() ? null : token;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return "";
        }
        Object value = map.get(key);
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    private static boolean getBoolean(Map<String, Object> map, String key) {
        if (map == null) {
            return false;
        }
        Object value = map.get(key);
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

    private static class ModelPermission {
        private final boolean visible;
        private final boolean canRead;
        private final boolean canCreate;
        private final boolean canUpdate;
        private final boolean canDelete;

        private ModelPermission(boolean visible, boolean canRead, boolean canCreate, boolean canUpdate,
            boolean canDelete) {
            this.visible = visible;
            this.canRead = canRead;
            this.canCreate = canCreate;
            this.canUpdate = canUpdate;
            this.canDelete = canDelete;
        }
    }
}
