package com.visionvogue.analyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionvogue.analyzer.config.AppProperties;
import com.visionvogue.analyzer.dto.AnalysisResponse;
import com.visionvogue.analyzer.model.AnalysisRecord;
import com.visionvogue.analyzer.repo.AnalysisRecordRepository;
import com.visionvogue.analyzer.repo.PartnerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class FileProcessingService {
    private static final Logger log = LoggerFactory.getLogger(FileProcessingService.class);

    private final AppProperties props;
    private final AnalyzeClient analyzeClient;
    private final AnalysisRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final PartnerRepository partnerRepository;

    public FileProcessingService(AppProperties props, AnalyzeClient analyzeClient,
                                 AnalysisRecordRepository repository, ObjectMapper objectMapper,
                                 PartnerRepository partnerRepository) {
        this.props = props;
        this.analyzeClient = analyzeClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.partnerRepository = partnerRepository;
    }

    public ProcessingResult processIncomeFolder() throws IOException {
        Path income = Paths.get(props.getIncomeDir());
        Path processed = Paths.get(props.getProcessedDir());
        Path failed = Paths.get(props.getFailedDir());

        Files.createDirectories(income);
        Files.createDirectories(processed);
        Files.createDirectories(failed);

        List<Path> files;
        try (var stream = Files.walk(income, 2)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedImage)
                    .toList();
        }

        List<FileOutcome> outcomes = new ArrayList<>();
        for (Path file : files) {
            if (!isKnownPartnerFile(file)) {
                log.info("Skipping {}: no known partner for path", file);
                continue;
            }
            FileOutcome outcome = processSingleFile(file);
            if (outcome != null) outcomes.add(outcome);
        }
        long success = outcomes.stream().filter(o -> o.status == AnalysisRecord.Status.SUCCESS).count();
        long failure = outcomes.size() - success;
        ProcessingResult result = new ProcessingResult();
        result.total = outcomes.size();
        result.success = (int) success;
        result.failed = (int) failure;
        result.files = outcomes;
        return result;
    }

    public FileOutcome processSingleFile(Path file) throws IOException {
        Path processed = Paths.get(props.getProcessedDir());
        Path failed = Paths.get(props.getFailedDir());
        Files.createDirectories(processed);
        Files.createDirectories(failed);

        FileOutcome outcome = new FileOutcome();
        outcome.filename = file.getFileName().toString();
        try {
            waitForStableFile(file);
            java.util.UUID partnerId = extractPartnerId(file);
            if (partnerId == null || !partnerRepository.existsById(partnerId)) {
                log.info("Skipping {}: partner not found in DB", file);
                return null;
            }
            AnalysisResponse resp = analyzeClient.callAnalyze(file.toFile());
            AnalysisRecord record = buildRecord(file.getFileName().toString(), resp);
            record.setPartnerId(partnerId);
            repository.save(record);
            moveFile(file, buildTargetPath(processed, file));
            outcome.status = AnalysisRecord.Status.SUCCESS;
            outcome.recordId = record.getId();
        } catch (Exception e) {
            AnalysisRecord record = new AnalysisRecord();
            record.setFilename(file.getFileName().toString());
            record.setStatus(AnalysisRecord.Status.FAILED);
            record.setCreatedAt(OffsetDateTime.now());
            record.setErrorMessage(e.getMessage());
            record.setPartnerId(extractPartnerId(file));
            repository.save(record);
            moveFile(file, buildTargetPath(failed, file));
            outcome.status = AnalysisRecord.Status.FAILED;
            outcome.error = e.getMessage();
        }
        return outcome;
    }

    public boolean isSupportedImage(Path path) {
        if (path == null || Files.isDirectory(path)) return false;
        try {
            String mime = Files.probeContentType(path);
            if (mime != null && mime.startsWith("image/")) return true;
        } catch (Exception ignored) {}
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".jpe") || name.endsWith(".jfif") || name.endsWith(".pjpeg") ||
               name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".avif") ||
               name.endsWith(".heic") || name.endsWith(".heif") || name.endsWith(".tif") || name.endsWith(".tiff") ||
               name.endsWith(".bmp") || name.endsWith(".svg") || name.endsWith(".ico") || name.endsWith(".jp2") || name.endsWith(".j2k");
    }

    private void moveFile(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        // If target exists, append a unique suffix
        Path finalTarget = target;
        if (Files.exists(target)) {
            String filename = target.getFileName().toString();
            int dot = filename.lastIndexOf('.');
            String base = dot > 0 ? filename.substring(0, dot) : filename;
            String ext = dot > 0 ? filename.substring(dot) : "";
            int i = 1;
            while (Files.exists(finalTarget)) {
                finalTarget = target.getParent().resolve(base + "_" + i + ext);
                i++;
            }
        }
        Files.move(source, finalTarget, StandardCopyOption.REPLACE_EXISTING);
    }

    private void waitForStableFile(Path file) throws IOException, InterruptedException {
        long lastSize = -1L;
        int stableChecks = 0;
        int maxWaitMs = 5000;
        int stepMs = 250;
        int waited = 0;
        while (waited < maxWaitMs) {
            long size = Files.size(file);
            if (size == lastSize) {
                stableChecks++;
                if (stableChecks >= 2) return; // stable across two checks
            } else {
                stableChecks = 0;
                lastSize = size;
            }
            Thread.sleep(stepMs);
            waited += stepMs;
        }
    }

    private java.util.UUID extractPartnerId(Path file) {
        try {
            Path income = Paths.get(props.getIncomeDir()).toAbsolutePath().normalize();
            Path fileAbs = file.toAbsolutePath().normalize();
            Path relative = income.relativize(fileAbs);
            if (relative.getNameCount() >= 2) {
                String idStr = relative.getName(0).toString();
                return java.util.UUID.fromString(idStr);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public boolean isKnownPartnerFile(Path file) {
        java.util.UUID partnerId = extractPartnerId(file);
        return partnerId != null && partnerRepository.existsById(partnerId);
    }

    private Path buildTargetPath(Path baseDir, Path sourceFile) {
        Path income = Paths.get(props.getIncomeDir()).toAbsolutePath().normalize();
        Path srcAbs = sourceFile.toAbsolutePath().normalize();
        Path rel = income.relativize(srcAbs);
        return baseDir.toAbsolutePath().normalize().resolve(rel);
    }

    private AnalysisRecord buildRecord(String filename, AnalysisResponse resp) throws JsonProcessingException {
        AnalysisRecord record = new AnalysisRecord();
        record.setFilename(filename);
        record.setStatus(AnalysisRecord.Status.SUCCESS);
        record.setCreatedAt(OffsetDateTime.now());

        if (resp.getCategory() != null && !resp.getCategory().isEmpty()) {
            var top = resp.getCategory().get(0);
            record.setTopCategoryLabel(top.getLabel());
            record.setTopCategoryConfidence(top.getConfidence());
        }
        record.setCategoryJson(objectMapper.writeValueAsString(resp.getCategory()));
        record.setAttributesJson(objectMapper.writeValueAsString(resp.getAttributes()));
        record.setColorsJson(objectMapper.writeValueAsString(resp.getColors()));
        record.setRawJson(objectMapper.writeValueAsString(resp));
        return record;
    }

    public static class ProcessingResult {
        public int total;
        public int success;
        public int failed;
        public List<FileOutcome> files;
    }

    public static class FileOutcome {
        public String filename;
        public java.util.UUID recordId;
        public AnalysisRecord.Status status;
        public String error;
    }
}
