package fr.expand.project.importdata.access;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.*;

public class SavedViewStoreTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path path;

    @Before
    public void setup() throws Exception {
        path = temp.newFile("views.sqlite").toPath();
        try (AccessControlStore access = new AccessControlStore(path, "test-only-password")) {
            access.upsertUser("alice", "Alice", true, false, false, "alice-test-password");
            access.upsertUser("bob", "Bob", true, false, false, "bob-test-password");
        }
    }

    @Test
    public void privateAndSharedViewsAreOwnerScopedAndPersisted() {
        String privateId, sharedId;
        try (SavedViewStore store = new SavedViewStore(path)) {
            privateId =
                    (String)
                            store.create(
                                            "model",
                                            "alice",
                                            "Privée",
                                            false,
                                            Map.of("page", "table", "tableSearch", "O'Brien"))
                                    .get("id");
            sharedId =
                    (String)
                            store.create(
                                            "model",
                                            "alice",
                                            "Partagée",
                                            true,
                                            Map.of("page", "search"))
                                    .get("id");
            store.create("other", "alice", "Autre modèle", true, Map.of());
            assertEquals(2, store.list("model", "alice").size());
            assertEquals(1, store.list("model", "bob").size());
            assertNull(store.find(privateId, "bob"));
            assertEquals(false, store.find(sharedId, "bob").get("canEdit"));
            assertNull(store.update(sharedId, "bob", "Volée", false, Map.of()));
            assertFalse(store.delete(sharedId, "bob"));
        }
        try (SavedViewStore store = new SavedViewStore(path)) {
            assertEquals(
                    "O'Brien",
                    ((Map<?, ?>) store.find(privateId, "alice").get("state")).get("tableSearch"));
            store.update(sharedId, "alice", "Privatisée", false, Map.of());
            assertNull(store.find(sharedId, "bob"));
            assertTrue(store.delete(sharedId, "alice"));
        }
    }

    @Test
    public void deletingUserCleansUpOwnedViews() {
        try (SavedViewStore store = new SavedViewStore(path)) {
            store.create("model", "alice", "Vue", true, Map.of());
            try (AccessControlStore access = new AccessControlStore(path, null)) {
                access.deleteUser("alice");
            }
            assertTrue(store.list("model", "bob").isEmpty());
        }
    }

    @Test
    public void unsafeOrMalformedStateIsRejected() {
        for (Map<String, Object> state :
                List.of(
                        Map.<String, Object>of("url", "javascript:alert(1)"),
                        Map.<String, Object>of("columns", List.of("secret")),
                        Map.<String, Object>of("sortBy", List.of(Map.of("key", "id"))),
                        Map.<String, Object>of("tableSearch", "x".repeat(513)))) {
            assertThrows(IllegalArgumentException.class, () -> SavedViewStore.state(state));
        }
        Map<String, Object> nullState = new HashMap<>();
        nullState.put("page", null);
        assertThrows(IllegalArgumentException.class, () -> SavedViewStore.state(nullState));
    }

    @Test
    public void columnsAndSortRoundTrip() {
        var state =
                Map.<String, Object>of(
                        "page",
                        "table",
                        "columns",
                        List.of("type", "preview"),
                        "sortBy",
                        List.of(Map.of("key", "id", "order", "desc")),
                        "tableTypeFilter",
                        List.of("PERSONNE"));
        try (SavedViewStore store = new SavedViewStore(path)) {
            var created = store.create("model", "alice", "Tri décroissant", false, state);
            assertEquals(state, created.get("state"));
        }
    }
}
