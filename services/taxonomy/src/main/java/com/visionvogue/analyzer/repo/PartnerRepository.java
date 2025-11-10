package com.visionvogue.analyzer.repo;

import com.visionvogue.analyzer.model.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {
}
