package com.visionvogue.analyzer.service;

import com.visionvogue.analyzer.config.AppProperties;
import com.visionvogue.analyzer.model.Partner;
import com.visionvogue.analyzer.repo.PartnerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class PartnerService {
    private static final Logger log = LoggerFactory.getLogger(PartnerService.class);

    private final PartnerRepository partnerRepository;
    private final AppProperties appProperties;

    public PartnerService(PartnerRepository partnerRepository, AppProperties appProperties) {
        this.partnerRepository = partnerRepository;
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
}

