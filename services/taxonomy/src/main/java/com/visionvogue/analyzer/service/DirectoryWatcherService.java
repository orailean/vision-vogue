package com.visionvogue.analyzer.service;

import com.visionvogue.analyzer.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

@Component
public class DirectoryWatcherService {
    private static final Logger log = LoggerFactory.getLogger(DirectoryWatcherService.class);

    // How often (seconds) the polling fallback scans for new files missed by inotify
    private static final long POLL_INTERVAL_SECONDS = 10;

    private final AppProperties props;
    private final FileProcessingService processingService;

    // Single-thread executor for the watch loop
    private final ExecutorService watcherExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "income-watcher");
        t.setDaemon(true);
        return t;
    });
    // Single-thread executor for sequential file processing
    private final ExecutorService processingExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "income-processor");
        t.setDaemon(true);
        return t;
    });
    // Scheduler for polling fallback (handles cp / bind-mount cases where inotify events are missed)
    private final ScheduledExecutorService pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "income-poller");
        t.setDaemon(true);
        return t;
    });
    // Track files already submitted to avoid double-processing between inotify and polling
    private final Set<Path> submittedFiles = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private WatchService watchService;
    private final Map<WatchKey, Path> keyDirMap = new ConcurrentHashMap<>();

    public DirectoryWatcherService(AppProperties props, FileProcessingService processingService) {
        this.props = props;
        this.processingService = processingService;
    }

    @PostConstruct
    public void start() throws IOException {
        Path income = Paths.get(props.getIncomeDir());
        Files.createDirectories(income);
        watchService = FileSystems.getDefault().newWatchService();
        registerDirectory(income);
        // register existing first-level subdirectories
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(income, Files::isDirectory)) {
            for (Path d : dirs) {
                registerDirectory(d);
            }
        }
        running.set(true);

        // Process any existing files on startup (sequentially)
        watcherExecutor.submit(() -> {
            try {
                log.info("Initial scan of income directory: {}", income.toAbsolutePath());
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(income, p -> !Files.isDirectory(p) && processingService.isSupportedImage(p))) {
                    for (Path p : stream) {
                        if (!processingService.isKnownPartnerFile(p)) {
                            log.info("Ignoring {}: no matching partner in DB", p.toAbsolutePath());
                            continue;
                        }
                        submitFile(p);
                    }
                }
                // Also scan first-level subdirectories for existing files
                try (DirectoryStream<Path> dirs = Files.newDirectoryStream(income, Files::isDirectory)) {
                    for (Path d : dirs) {
                        try (DirectoryStream<Path> files = Files.newDirectoryStream(d, p -> !Files.isDirectory(p) && processingService.isSupportedImage(p))) {
                            for (Path f : files) {
                                if (!processingService.isKnownPartnerFile(f)) {
                                    log.info("Ignoring {}: no matching partner in DB", f.toAbsolutePath());
                                    continue;
                                }
                                submitFile(f);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Initial scan failed: {}", e.getMessage());
            }
        });

        // Start watch loop
        watcherExecutor.submit(this::watchLoop);

        // Polling fallback: catches files dropped via cp/bind-mount where inotify events are missed
        pollScheduler.scheduleWithFixedDelay(this::pollIncomeDirectory,
                POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);

        log.info("Started directory watcher on {} (polling every {}s as fallback)",
                income.toAbsolutePath(), POLL_INTERVAL_SECONDS);
    }

    private void watchLoop() {
        while (running.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            Path baseDir = keyDirMap.get(key);
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                Path fullPath = baseDir.resolve(filename);
                if (Files.isDirectory(fullPath)) {
                    // Register newly created subdirectory
                    try { registerDirectory(fullPath); } catch (IOException ignored) {}
                    continue;
                }
                if (!processingService.isSupportedImage(fullPath)) {
                    continue;
                }
                if (!processingService.isKnownPartnerFile(fullPath)) {
                    log.info("Ignoring {}: no matching partner in DB", fullPath.toAbsolutePath());
                    continue;
                }
                log.info("Detected new file: {}", fullPath.toAbsolutePath());
                submitFile(fullPath);
            }
            boolean valid = key.reset();
            if (!valid) {
                log.error("Watch key no longer valid; stopping watcher");
                break;
            }
        }
    }

    private void registerDirectory(Path dir) throws IOException {
        WatchKey key = dir.register(watchService, ENTRY_CREATE);
        keyDirMap.put(key, dir);
        log.info("Watching directory: {}", dir.toAbsolutePath());
    }

    /**
     * Submit a file for processing only if it hasn't been submitted already.
     * The entry is removed from the set once processing completes (success or failure),
     * so a file that reappears after being moved away will be picked up again.
     */
    private void submitFile(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        if (!submittedFiles.add(normalized)) {
            return; // already in-flight
        }
        processingExecutor.submit(() -> {
            try {
                processingService.processSingleFile(file);
            } catch (Exception ex) {
                log.error("Failed processing {}: {}", file.getFileName(), ex.getMessage());
            } finally {
                submittedFiles.remove(normalized);
            }
        });
    }

    /**
     * Polling fallback: scans the income directory for files that are present but were
     * never picked up by inotify (e.g. files dropped via {@code cp} through a Docker
     * bind-mount on macOS where the host kernel events are not forwarded to the container).
     */
    private void pollIncomeDirectory() {
        try {
            Path income = Paths.get(props.getIncomeDir());
            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(income, Files::isDirectory)) {
                for (Path dir : dirs) {
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(dir,
                            p -> !Files.isDirectory(p) && processingService.isSupportedImage(p))) {
                        for (Path f : files) {
                            if (!processingService.isKnownPartnerFile(f)) continue;
                            submitFile(f);
                        }
                    } catch (IOException ex) {
                        log.warn("Poll scan error in {}: {}", dir, ex.getMessage());
                    }
                }
            }
            // also top-level files
            try (DirectoryStream<Path> files = Files.newDirectoryStream(income,
                    p -> !Files.isDirectory(p) && processingService.isSupportedImage(p))) {
                for (Path f : files) {
                    if (!processingService.isKnownPartnerFile(f)) continue;
                    submitFile(f);
                }
            }
        } catch (Exception e) {
            log.warn("Polling scan failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {}
        watcherExecutor.shutdownNow();
        processingExecutor.shutdown();
        pollScheduler.shutdownNow();
    }
}
