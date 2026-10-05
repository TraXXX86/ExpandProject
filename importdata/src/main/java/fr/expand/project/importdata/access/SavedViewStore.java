package fr.expand.project.importdata.access;

import com.google.gson.Gson;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Saved queries contain presentation preferences only; access is checked on every request. */
public final class SavedViewStore implements AutoCloseable {
    private static final Gson JSON = new Gson();
    private final String url;

    public SavedViewStore() {
        this(
                Path.of(
                        setting(
                                "ACCESS_DB_PATH",
                                Path.of(
                                                System.getProperty("user.home"),
                                                ".expandproject",
                                                "access.sqlite")
                                        .toString())));
    }

    public SavedViewStore(Path path) {
        url = "jdbc:sqlite:" + path;
        try (Connection c = open();
                Statement s = c.createStatement()) {
            s.execute(
                    "CREATE TABLE IF NOT EXISTS saved_views (id TEXT PRIMARY KEY, model_key TEXT"
                        + " NOT NULL, owner TEXT NOT NULL REFERENCES users(username) ON DELETE"
                        + " CASCADE, name TEXT NOT NULL, shared INTEGER NOT NULL, state TEXT NOT"
                        + " NULL, updated_at TEXT NOT NULL)");
            s.execute(
                    "CREATE INDEX IF NOT EXISTS saved_views_model ON saved_views(model_key,"
                            + " owner)");
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public List<Map<String, Object>> list(String modelKey, String user) {
        try (Connection c = open();
                PreparedStatement s =
                        c.prepareStatement(
                                "SELECT * FROM saved_views WHERE model_key=? AND (owner=? OR"
                                        + " shared=1) ORDER BY name COLLATE NOCASE,id LIMIT 501")) {
            s.setString(1, modelKey);
            s.setString(2, user);
            try (ResultSet r = s.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (r.next()) rows.add(row(r, user));
                return rows;
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public Map<String, Object> find(String id, String user) {
        try (Connection c = open()) {
            return find(c, id, user);
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public Map<String, Object> create(
            String modelKey, String user, String name, boolean shared, Map<String, Object> state) {
        String cleanName = name(name);
        Map<String, Object> cleanState = state(state);
        try (Connection c = open();
                Statement transaction = c.createStatement()) {
            transaction.execute("BEGIN IMMEDIATE");
            try {
                try (PreparedStatement count =
                        c.prepareStatement(
                                "SELECT COUNT(*) FROM saved_views WHERE model_key=? AND owner=?")) {
                    count.setString(1, modelKey);
                    count.setString(2, user);
                    try (ResultSet r = count.executeQuery()) {
                        r.next();
                        if (r.getInt(1) >= 100)
                            throw new IllegalArgumentException(
                                    "Limite de 100 vues par utilisateur et modèle atteinte");
                    }
                }
                String id = UUID.randomUUID().toString();
                try (PreparedStatement s =
                        c.prepareStatement(
                                "INSERT INTO"
                                    + " saved_views(id,model_key,owner,name,shared,state,updated_at)"
                                    + " VALUES(?,?,?,?,?,?,?)")) {
                    s.setString(1, id);
                    s.setString(2, modelKey);
                    s.setString(3, user);
                    s.setString(4, cleanName);
                    s.setBoolean(5, shared);
                    s.setString(6, JSON.toJson(cleanState));
                    s.setString(7, Instant.now().toString());
                    s.executeUpdate();
                }
                Map<String, Object> result = find(c, id, user);
                transaction.execute("COMMIT");
                return result;
            } catch (Exception e) {
                transaction.execute("ROLLBACK");
                throw e;
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public Map<String, Object> update(
            String id, String owner, String name, boolean shared, Map<String, Object> state) {
        String cleanName = name(name);
        Map<String, Object> cleanState = state(state);
        try (Connection c = open();
                PreparedStatement s =
                        c.prepareStatement(
                                "UPDATE saved_views SET name=?,shared=?,state=?,updated_at=? WHERE"
                                        + " id=? AND owner=?")) {
            s.setString(1, cleanName);
            s.setBoolean(2, shared);
            s.setString(3, JSON.toJson(cleanState));
            s.setString(4, Instant.now().toString());
            s.setString(5, id);
            s.setString(6, owner);
            return s.executeUpdate() == 0 ? null : find(c, id, owner);
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public boolean delete(String id, String owner) {
        try (Connection c = open();
                PreparedStatement s =
                        c.prepareStatement("DELETE FROM saved_views WHERE id=? AND owner=?")) {
            s.setString(1, id);
            s.setString(2, owner);
            return s.executeUpdate() == 1;
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    private Map<String, Object> find(Connection c, String id, String user) throws SQLException {
        try (PreparedStatement s =
                c.prepareStatement(
                        "SELECT * FROM saved_views WHERE id=? AND (owner=? OR shared=1)")) {
            s.setString(1, id);
            s.setString(2, user);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? row(r, user) : null;
            }
        }
    }

    private static Map<String, Object> row(ResultSet r, String user) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getString("id"));
        row.put("modelKey", r.getString("model_key"));
        row.put("owner", r.getString("owner"));
        row.put("name", r.getString("name"));
        row.put("shared", r.getBoolean("shared"));
        row.put("state", JSON.fromJson(r.getString("state"), Map.class));
        row.put("updatedAt", r.getString("updated_at"));
        row.put("canEdit", user.equals(r.getString("owner")));
        return row;
    }

    public static Map<String, Object> state(Map<String, Object> input) {
        if (input == null) throw new IllegalArgumentException("Configuration de vue requise");
        if (JSON.toJson(input).length() > 16384)
            throw new IllegalArgumentException("Vue trop volumineuse");
        Set<String> strings =
                Set.of(
                        "tableSearch",
                        "tableAttributeKey",
                        "tableAttributeValue",
                        "fullTextQuery",
                        "workflowId",
                        "workflowStatus");
        Set<String> operators =
                Set.of(
                        "contains",
                        "equals",
                        "startsWith",
                        "endsWith",
                        "notContains",
                        "notEquals",
                        "exists",
                        "empty");
        Map<String, Object> result = new LinkedHashMap<>();
        for (var e : input.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (value == null) throw new IllegalArgumentException("Option de vue vide: " + key);
            if (strings.contains(key)) {
                if (!(value instanceof String text) || text.length() > 512)
                    throw new IllegalArgumentException("Filtre invalide: " + key);
                result.put(key, value);
            } else if (key.equals("page")) {
                if (!Set.of("table", "search").contains(value))
                    throw new IllegalArgumentException("Page de vue invalide");
                result.put(key, value);
            } else if (key.equals("searchMode")) {
                if (!Set.of("contains", "fulltext").contains(value))
                    throw new IllegalArgumentException("Mode de recherche invalide");
                result.put(key, value);
            } else if (key.equals("tableAttributeKeyOperator")
                    || key.equals("tableAttributeValueOperator")) {
                if (!operators.contains(value))
                    throw new IllegalArgumentException("Opérateur invalide");
                result.put(key, value);
            } else if (Set.of("tableTypeFilter", "fullTextTypeFilter", "columns").contains(key)) {
                if (!(value instanceof List<?> list) || list.size() > 100)
                    throw new IllegalArgumentException("Liste de filtres invalide");
                for (Object item : list)
                    if (!(item instanceof String text)
                            || text.length() > 128
                            || (key.equals("columns")
                                    && !Set.of(
                                                    "type",
                                                    "id",
                                                    "preview",
                                                    "attributes",
                                                    "actions",
                                                    "workflowStatus")
                                            .contains(text)))
                        throw new IllegalArgumentException("Colonne ou type invalide");
                result.put(key, List.copyOf(list));
            } else if (key.equals("sortBy")) {
                if (!(value instanceof List<?> list) || list.size() > 4)
                    throw new IllegalArgumentException("Tri invalide");
                for (Object item : list)
                    if (!(item instanceof Map<?, ?> sort)
                            || !(sort.get("key") instanceof String)
                            || !(sort.get("order") instanceof String)
                            || !Set.of("type", "id", "preview", "attributes", "workflowStatus")
                                    .contains(sort.get("key"))
                            || !Set.of("asc", "desc").contains(sort.get("order"))
                            || sort.size() != 2) throw new IllegalArgumentException("Tri invalide");
                result.put(key, List.copyOf(list));
            } else throw new IllegalArgumentException("Option de vue inconnue: " + key);
        }
        result.putIfAbsent("page", "table");
        return result;
    }

    private static String name(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 120)
            throw new IllegalArgumentException("Nom de vue requis (120 caractères maximum)");
        return value.strip();
    }

    private Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("PRAGMA foreign_keys=ON");
        } catch (SQLException e) {
            c.close();
            throw e;
        }
        return c;
    }

    private static RuntimeException failure(SQLException e) {
        return new IllegalStateException("Impossible de gérer les vues enregistrées", e);
    }

    private static String setting(String key, String fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public void close() {}
}
