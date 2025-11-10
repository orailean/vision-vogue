package com.visionvogue.analyzer.repo;

import com.visionvogue.analyzer.model.AnalysisRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface AnalysisRecordRepository extends JpaRepository<AnalysisRecord, UUID> {
    List<AnalysisRecord> findByPartnerId(UUID partnerId);
}
