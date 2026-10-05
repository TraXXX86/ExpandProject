package fr.expand.project.importdata.access;

import static org.junit.Assert.*;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class LoginThrottleTest {
    @Test
    public void accountAndClientLimitsApplyBeforeVerificationAndRecoverAfterWindow() {
        MutableClock clock = new MutableClock();
        LoginThrottle limiter = new LoginThrottle(2, 3, Duration.ofSeconds(60), 100, clock);
        assertTrue(limiter.tryAcquire("client1", "alice"));
        assertTrue(limiter.tryAcquire("client2", " ALICE "));
        assertFalse(limiter.tryAcquire("client3", "alice"));
        assertEquals(60, limiter.retryAfterSeconds("client3", "alice"));
        assertTrue(limiter.tryAcquire("client1", "bob"));
        assertTrue(limiter.tryAcquire("client1", "carol"));
        assertFalse(limiter.tryAcquire("client1", "dave"));
        clock.now += 60_000;
        assertTrue(limiter.tryAcquire("client1", "alice"));
    }

    @Test
    public void successfulLoginsDoNotEraseClientBudget() {
        LoginThrottle limiter =
                new LoginThrottle(1, 2, Duration.ofMinutes(5), 100, new MutableClock());
        assertTrue(limiter.tryAcquire("client", "alice"));
        limiter.clear("client", "alice");
        assertTrue(limiter.tryAcquire("client", "alice"));
        limiter.clear("client", "alice");
        assertFalse(limiter.tryAcquire("client", "alice"));
    }

    @Test
    public void capacityFailsClosedWithoutEvictingActiveLimits() {
        MutableClock clock = new MutableClock();
        LoginThrottle limiter = new LoginThrottle(1, 2, Duration.ofSeconds(1), 2, clock);
        assertTrue(limiter.tryAcquire("client1", "alice"));
        assertFalse(limiter.tryAcquire("client2", "bob"));
        assertFalse(limiter.tryAcquire("client1", "alice"));
        clock.now += 1000;
        assertTrue(limiter.tryAcquire("client2", "bob"));
    }

    private static final class MutableClock extends Clock {
        private long now;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now);
        }

        @Override
        public long millis() {
            return now;
        }
    }
}
