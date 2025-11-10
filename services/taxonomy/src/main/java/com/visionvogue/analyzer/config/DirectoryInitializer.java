package com.visionvogue.analyzer.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Initializes required directories on application startup.
 */
@Component
public class DirectoryInitializer {
    private static final Logger log = LoggerFactory.getLogger(DirectoryInitializer.class);

    private final AppProperties appProperties;

    public DirectoryInitializer(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void initializeDirectories() {
        createDirectoryIfNotExists(appProperties.getIncomeDir());
        createDirectoryIfNotExists(appProperties.getProcessedDir());
        createDirectoryIfNotExists(appProperties.getFailedDir());
    }

    private void createDirectoryIfNotExists(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created directory: {}", path.toAbsolutePath());
            } else {
                log.debug("Directory already exists: {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create directory: {}", directoryPath, e);
            throw new IllegalStateException("Could not create required directory: " + directoryPath, e);
        }
    }
}

