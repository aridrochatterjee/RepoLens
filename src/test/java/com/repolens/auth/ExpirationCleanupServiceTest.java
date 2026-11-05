package com.repolens.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.repolens.controller.OAuthController;
import com.repolens.controller.RepositoryController;
import com.repolens.server.WebServer;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ExpirationCleanupServiceTest {

    @Test
    void schedulesCleanupOnceAndStopsIdempotently() {
        var stateManager = new OAuthStateManager();
        var sessionManager = new SessionManager();
        var schedulers = new ArrayList<CapturingScheduler>();
        Supplier<CapturingScheduler> factory = () -> {
            CapturingScheduler scheduler = new CapturingScheduler();
            schedulers.add(scheduler);
            return scheduler;
        };
        var service = new ExpirationCleanupService(
                stateManager,
                sessionManager,
                factory::get
        );

        service.start();
        service.start();

        assertEquals(1, schedulers.size());
        CapturingScheduler scheduler = schedulers.getFirst();
        assertEquals(60, scheduler.initialDelay);
        assertEquals(60, scheduler.delay);
        assertEquals(TimeUnit.SECONDS, scheduler.unit);
        assertNotNull(scheduler.cleanupTask);
        assertFalse(scheduler.isShutdown());

        scheduler.cleanupTask.run();
        service.stop();
        service.stop();

        assertTrue(scheduler.isTerminated());
    }

    @Test
    void continuesRunningCleanupWhenOneManagerThrows() {
        var stateManager = new FailingOnceStateManager();
        var sessionManager = new CountingSessionManager();
        CapturingScheduler scheduler = new CapturingScheduler();
        var service = new ExpirationCleanupService(
                stateManager,
                sessionManager,
                () -> scheduler
        );
        try {
            service.start();
            scheduler.cleanupTask.run();
            scheduler.cleanupTask.run();

            assertEquals(2, stateManager.cleanupCalls.get());
            assertEquals(2, sessionManager.cleanupCalls.get());
        } finally {
            service.stop();
        }
    }

    @Test
    void webServerStopsCleanupAndCanRestartWithANewScheduler() throws Exception {
        var stateManager = new OAuthStateManager();
        var sessionManager = new SessionManager();
        List<CapturingScheduler> schedulers = new ArrayList<>();
        var service = new ExpirationCleanupService(
                stateManager,
                sessionManager,
                () -> {
                    CapturingScheduler scheduler = new CapturingScheduler();
                    schedulers.add(scheduler);
                    return scheduler;
                }
        );
        var server = new WebServer(
                0,
                InetAddress.getLoopbackAddress(),
                new OAuthController(null, sessionManager, stateManager, true),
                new RepositoryController(null),
                sessionManager,
                service
        );

        try {
            server.start();
            assertEquals(1, schedulers.size());
            CapturingScheduler first = schedulers.getFirst();
            assertNotNull(first.cleanupTask);

            server.stop();
            server.stop();
            assertTrue(first.isTerminated());

            server.start();
            assertEquals(2, schedulers.size());
            server.stop();
            assertTrue(schedulers.getLast().isTerminated());
        } finally {
            server.stop();
        }
    }

    private static final class CapturingScheduler
            extends ScheduledThreadPoolExecutor {
        private Runnable cleanupTask;
        private long initialDelay;
        private long delay;
        private TimeUnit unit;

        private CapturingScheduler() {
            super(1);
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command,
                long initialDelay,
                long delay,
                TimeUnit unit
        ) {
            this.cleanupTask = command;
            this.initialDelay = initialDelay;
            this.delay = delay;
            this.unit = unit;
            return super.schedule(command, 1, TimeUnit.DAYS);
        }
    }

    private static final class FailingOnceStateManager
            extends OAuthStateManager {
        private final AtomicInteger cleanupCalls = new AtomicInteger();

        @Override
        public int removeExpiredStates() {
            if (cleanupCalls.getAndIncrement() == 0) {
                throw new IllegalStateException("test failure");
            }
            return 0;
        }
    }

    private static final class CountingSessionManager extends SessionManager {
        private final AtomicInteger cleanupCalls = new AtomicInteger();

        private CountingSessionManager() {
            super(
                    Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                    Duration.ofHours(24)
            );
        }

        @Override
        public int removeExpiredSessions() {
            cleanupCalls.incrementAndGet();
            return super.removeExpiredSessions();
        }
    }
}
