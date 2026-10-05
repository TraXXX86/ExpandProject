package fr.expand.project.importdata.audit;

import fr.expand.project.importdata.access.AccessContext;

import java.util.Objects;
import java.util.UUID;

/** Explicit request/batch identity; never contains session credentials. */
public record AuditActor(String actor, String effectiveUser, String operationId) {
    public AuditActor {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(effectiveUser);
        Objects.requireNonNull(operationId);
    }

    public static AuditActor system() {
        return new AuditActor("system", "system", UUID.randomUUID().toString());
    }

    public static AuditActor from(AccessContext access) {
        Objects.requireNonNull(access, "Authenticated access context required");
        return new AuditActor(
                access.getActorUsername(), access.getUsername(), UUID.randomUUID().toString());
    }
}
