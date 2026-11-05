package com.repolens.auth;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ExpirationCleanupService {

    private static final long CLEANUP_INTERVAL_SECONDS = 60;

    private final OAuthStateManager stateManager;
    private final SessionManager sessionManager;
    private final Supplier<ScheduledExecutorService> schedulerFactory;

    private ScheduledExecutorService scheduler;

    public ExpirationCleanupService(
            OAuthStateManager stateManager,
            SessionManager sessionManager
    ) {
        this(
                stateManager,
                sessionManager,
                () -> Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(
                            task,
                            "repolens-expiration-cleanup"
                    );
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    ExpirationCleanupService(
            OAuthStateManager stateManager,
            SessionManager sessionManager,
            Supplier<ScheduledExecutorService> schedulerFactory
    ) {
        this.stateManager = Objects.requireNonNull(stateManager);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.schedulerFactory = Objects.requireNonNull(schedulerFactory);
    }

    public synchronized void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        ScheduledExecutorService newScheduler =
                Objects.requireNonNull(schedulerFactory.get());
        try {
            newScheduler.scheduleWithFixedDelay(
                    this::cleanupExpiredEntries,
                    CLEANUP_INTERVAL_SECONDS,
                    CLEANUP_INTERVAL_SECONDS,
                    TimeUnit.SECONDS
            );
            scheduler = newScheduler;
        } catch (RuntimeException exception) {
            newScheduler.shutdownNow();
            throw exception;
        }
    }

    public synchronized void stop() {
        if (scheduler == null) {
            return;
        }

        ScheduledExecutorService stoppingScheduler = scheduler;
        scheduler = null;
        stoppingScheduler.shutdownNow();
        try {
            if (!stoppingScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println(
                        "Expiration cleanup scheduler did not stop in time."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void cleanupExpiredEntries() {
        try {
            stateManager.removeExpiredStates();
        } catch (RuntimeException exception) {
            System.err.println(
                    "OAuth state cleanup failed: "
                            + exception.getClass().getSimpleName()
            );
        }

        try {
            sessionManager.removeExpiredSessions();
        } catch (RuntimeException exception) {
            System.err.println(
                    "Session cleanup failed: "
                            + exception.getClass().getSimpleName()
            );
        }
    }
}
