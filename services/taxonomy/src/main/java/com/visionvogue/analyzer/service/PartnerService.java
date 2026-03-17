package com.visionvogue.analyzer.service;

import com.visionvogue.analyzer.config.AppProperties;
import com.visionvogue.analyzer.model.Partner;
import com.visionvogue.analyzer.repo.AnalysisRecordRepository;
import com.visionvogue.analyzer.repo.PartnerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.UUID;

@Service
public class PartnerService {
    private static final Logger log = LoggerFactory.getLogger(PartnerService.class);

    private final PartnerRepository partnerRepository;
    private final AnalysisRecordRepository analysisRecordRepository;
    private final AppProperties appProperties;

    public PartnerService(PartnerRepository partnerRepository,
                          AnalysisRecordRepository analysisRecordRepository,
                          AppProperties appProperties) {
        this.partnerRepository = partnerRepository;
        this.analysisRecordRepository = analysisRecordRepository;
        this.appProperties = appProperties;
    }

    @Transactional
    public Partner createPartner(String name) {
        Partner partner = new Partner();
        partner.setName(name);
        Partner saved = partnerRepository.save(partner);

        // Create income directory for the partner
        createPartnerDirectories(saved.getId());

        return saved;
    }

    @Transactional
    public boolean deletePartner(UUID id) {
        Optional<Partner> existing = partnerRepository.findById(id);
        if (existing.isEmpty()) {
            return false;
        }

        // Delete all analysis records for this partner first
        analysisRecordRepository.deleteAll(analysisRecordRepository.findByPartnerId(id));
        log.info("Deleted analysis records for partner {}", id);

        // Delete the partner row
        partnerRepository.deleteById(id);
        log.info("Deleted partner {}", id);

        // Remove partner directories (best-effort; do not roll back the DB transaction on failure)
        deletePartnerDirectories(id);

        return true;
    }

    private void createPartnerDirectories(UUID partnerId) {
        try {
            Path incomeDir = Paths.get(appProperties.getIncomeDir(), partnerId.toString());
            Path processedDir = Paths.get(appProperties.getProcessedDir(), partnerId.toString());
            Path failedDir = Paths.get(appProperties.getFailedDir(), partnerId.toString());

            Files.createDirectories(incomeDir);
            Files.createDirectories(processedDir);
            Files.createDirectories(failedDir);

            log.info("Created directories for partner {}: income={}, processed={}, failed={}",
                    partnerId, incomeDir, processedDir, failedDir);
        } catch (IOException e) {
            log.error("Failed to create directories for partner {}: {}", partnerId, e.getMessage(), e);
            throw new RuntimeException("Failed to create partner directories", e);
        }
    }

    private void deletePartnerDirectories(UUID partnerId) {
        String[] roots = {
                appProperties.getIncomeDir(),
                appProperties.getProcessedDir(),
                appProperties.getFailedDir()
        };
        for (String root : roots) {
            Path dir = Paths.get(root, partnerId.toString());
            if (Files.exists(dir)) {
                try {
                    Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }
                        @Override
                        public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                            Files.delete(d);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                    log.info("Deleted directory {} for partner {}", dir, partnerId);
                } catch (IOException e) {
                    log.warn("Could not fully delete directory {} for partner {}: {}", dir, partnerId, e.getMessage());
                }
            }
        }
    }
}


