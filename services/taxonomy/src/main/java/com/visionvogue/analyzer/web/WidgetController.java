package com.visionvogue.analyzer.web;

import com.visionvogue.analyzer.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class WidgetController {

    private static final Logger log = LoggerFactory.getLogger(WidgetController.class);

    private final AppProperties appProperties;

    public WidgetController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    // Serve product images
    @GetMapping("/api/images/{partnerId}/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable("partnerId") String partnerId, @PathVariable("filename") String filename) {
        try {
            log.debug("Requesting image: partnerId={}, filename={}", partnerId, filename);

            Path processedBase = Paths.get(appProperties.getProcessedDir()).toAbsolutePath().normalize();
            log.debug("Resolved processed base directory: {}", processedBase);

            Path imagePath = resolveInside(processedBase, partnerId, filename);
            log.debug("Checking path: {}", imagePath);

            if (!Files.exists(imagePath)) {
                imagePath = resolveInside(processedBase, filename);
                log.debug("Trying fallback path: {}", imagePath);
            }

            if (!Files.exists(imagePath)) {
                log.warn("Image not found: partnerId={}, filename={}", partnerId, filename);
                return ResponseEntity.notFound().build();
            }

            log.debug("Image found at: {}", imagePath);

            Resource resource = new FileSystemResource(imagePath);

            // Determine content type
            String contentType = getContentType(imagePath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (IllegalArgumentException e) {
            log.warn("Rejected image request {}/{}: {}", partnerId, filename, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error serving image {}/{}: {}", partnerId, filename, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Path resolveInside(Path base, String... segments) {
        Path resolved = base;
        for (String segment : segments) {
            resolved = resolved.resolve(segment);
        }
        resolved = resolved.normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Path escapes base directory");
        }
        return resolved;
    }

    private String getContentType(Path imagePath) {
        try {
            String contentType = Files.probeContentType(imagePath);
            if (contentType != null) {
                return contentType;
            }
        } catch (IOException e) {
            log.warn("Could not probe content type for {}: {}", imagePath, e.getMessage());
        }

        // Fallback to extension-based detection
        String filename = imagePath.getFileName().toString().toLowerCase();
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.endsWith(".png")) {
            return "image/png";
        } else if (filename.endsWith(".gif")) {
            return "image/gif";
        } else if (filename.endsWith(".webp")) {
            return "image/webp";
        } else if (filename.endsWith(".svg")) {
            return "image/svg+xml";
        }

        return "application/octet-stream";
    }
}
