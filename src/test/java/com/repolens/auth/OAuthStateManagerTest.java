package com.repolens.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class OAuthStateManagerTest {

    @Test
    void removesExpiredStatesAndPreservesUnexpiredStates() {
        MutableClock clock = new MutableClock(
                Instant.parse("2026-01-01T00:00:00Z")
        );
        OAuthStateManager manager = new OAuthStateManager(clock);
        String expired = manager.createState();
        clock.advance(Duration.ofMinutes(4));
        String active = manager.createState();
        clock.advance(Duration.ofMinutes(1));

        assertEquals(1, manager.removeExpiredStates());
        assertFalse(manager.validateAndConsume(expired));
        assertTrue(manager.validateAndConsume(active));
    }

    @Test
    void stateCanOnlyBeConsumedOnce() {
        OAuthStateManager manager = new OAuthStateManager(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        );
        String state = manager.createState();

        assertTrue(manager.validateAndConsume(state));
        assertFalse(manager.validateAndConsume(state));
    }

    @Test
    void concurrentValidationConsumesStateOnlyOnce() throws Exception {
        OAuthStateManager manager = new OAuthStateManager();
        String state = manager.createState();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return manager.validateAndConsume(state);
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return manager.validateAndConsume(state);
            });
            start.countDown();

            assertTrue(first.get() ^ second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCleanupDoesNotRemoveUnexpiredStates() throws Exception {
        OAuthStateManager manager = new OAuthStateManager(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        );
        ConcurrentLinkedQueue<String> states = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(5);
        try {
            var creators = new ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 4; worker++) {
                creators.add(executor.submit(() -> {
                    start.await();
                    for (int index = 0; index < 250; index++) {
                        states.add(manager.createState());
                    }
                    return null;
                }));
            }
            var cleanup = executor.submit(() -> {
                start.await();
                for (int index = 0; index < 1000; index++) {
                    manager.removeExpiredStates();
                }
                return null;
            });
            start.countDown();
            for (var creator : creators) creator.get();
            cleanup.get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1000, states.size());
        for (String state : states) {
            assertTrue(manager.validateAndConsume(state));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

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
            return instant;
        }
    }
}
