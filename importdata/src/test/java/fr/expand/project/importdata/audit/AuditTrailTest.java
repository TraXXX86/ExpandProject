package fr.expand.project.importdata.audit;

import static org.junit.Assert.*;

import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.Map;

public class AuditTrailTest {
    @Test
    public void boundedSnapshotsAreExplicitRatherThanSilentlyTruncated() {
        String json = AuditTrail.snapshot(Map.of("xml", "a".repeat(1024 * 1024 + 1)));
        var snapshot = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(snapshot.get("snapshotOmitted").getAsBoolean());
        assertEquals(64, snapshot.get("sha256").getAsString().length());
        assertTrue(json.length() < 300);
    }

    @Test
    public void regularSnapshotsPreserveTypedValuesAndNull() {
        assertEquals("null", AuditTrail.snapshot(null));
        var json =
                JsonParser.parseString(AuditTrail.snapshot(Map.of("id", 0L, "directed", true)))
                        .getAsJsonObject();
        assertTrue(json.get("id").getAsJsonPrimitive().isNumber());
        assertTrue(json.get("directed").getAsBoolean());
    }
}
