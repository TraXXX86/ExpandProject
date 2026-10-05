package fr.expand.project.importdata.access;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bounded process-local login limiter. Call before password verification, including unknown
 * accounts.
 */
public final class LoginThrottle {
    private final int accountLimit;
    private final int clientLimit;
    private final int maxEntries;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Bucket> accounts = new HashMap<>();
    private final Map<String, Bucket> clients = new HashMap<>();

    public LoginThrottle() {
        this(10, 30, Duration.ofMinutes(5), 10_000, Clock.systemUTC());
    }

    public LoginThrottle(
            int accountLimit, int clientLimit, Duration window, int maxEntries, Clock clock) {
        if (accountLimit < 1 || clientLimit < 1 || maxEntries < 2 || window.toMillis() < 1) {
            throw new IllegalArgumentException("Throttle limits and window must be positive");
        }
        this.accountLimit = accountLimit;
        this.clientLimit = clientLimit;
        this.windowMillis = window.toMillis();
        this.maxEntries = maxEntries;
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public synchronized boolean tryAcquire(String clientKey, String username) {
        long now = clock.millis();
        prune(now);
        String accountKey = accountKey(username);
        String client = clientKey(clientKey);
        Bucket accountBucket = accounts.get(accountKey);
        Bucket clientBucket = clients.get(client);
        if (blocked(accountBucket, accountLimit) || blocked(clientBucket, clientLimit)) {
            return false;
        }
        int additional = (accountBucket == null ? 1 : 0) + (clientBucket == null ? 1 : 0);
        // At capacity, fail closed instead of evicting active limits that attackers can bypass.
        if (accounts.size() + clients.size() + additional > maxEntries) {
            return false;
        }
        accounts.computeIfAbsent(accountKey, key -> new Bucket(now + windowMillis)).attempts++;
        clients.computeIfAbsent(client, key -> new Bucket(now + windowMillis)).attempts++;
        return true;
    }

    /**
     * A successful login clears its account failures while preserving the client request budget.
     */
    public synchronized void clear(String clientKey, String username) {
        accounts.remove(accountKey(username));
    }

    public synchronized long retryAfterSeconds(String clientKey, String username) {
        long now = clock.millis();
        prune(now);
        Bucket account = accounts.get(accountKey(username));
        Bucket client = clients.get(clientKey(clientKey));
        long until = now;
        if (blocked(account, accountLimit)) {
            until = Math.max(until, account.until);
        }
        if (blocked(client, clientLimit)) {
            until = Math.max(until, client.until);
        }
        if (until == now && accounts.size() + clients.size() >= maxEntries) {
            until = now + windowMillis;
        }
        return Math.max(1, (until - now + 999) / 1000);
    }

    private void prune(long now) {
        accounts.values().removeIf(bucket -> bucket.until <= now);
        clients.values().removeIf(bucket -> bucket.until <= now);
    }

    private static boolean blocked(Bucket bucket, int limit) {
        return bucket != null && bucket.attempts >= limit;
    }

    private static String accountKey(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String clientKey(String clientKey) {
        return clientKey == null ? "unknown" : clientKey;
    }

    private static final class Bucket {
        private final long until;
        private int attempts;

        private Bucket(long until) {
            this.until = until;
        }
    }
}
