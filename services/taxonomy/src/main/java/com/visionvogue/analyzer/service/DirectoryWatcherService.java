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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

@Component
public class DirectoryWatcherService {
    private static final Logger log = LoggerFactory.getLogger(DirectoryWatcherService.class);

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
                        processingExecutor.submit(() -> {
                            try { processingService.processSingleFile(p); }
                            catch (Exception ex) { log.error("Failed processing {}: {}", p.getFileName(), ex.getMessage()); }
                        });
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
                                processingExecutor.submit(() -> {
                                    try { processingService.processSingleFile(f); }
                                    catch (Exception ex) { log.error("Failed processing {}: {}", f.getFileName(), ex.getMessage()); }
                                });
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
        log.info("Started directory watcher on {}", income.toAbsolutePath());
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
                processingExecutor.submit(() -> {
                    try {
                        processingService.processSingleFile(fullPath);
                    } catch (Exception ex) {
                        log.error("Failed processing {}: {}", fullPath.getFileName(), ex.getMessage());
                    }
                });
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

    @PreDestroy
    public void stop() {
        running.set(false);
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {}
        watcherExecutor.shutdownNow();
        processingExecutor.shutdown();
    }
}
