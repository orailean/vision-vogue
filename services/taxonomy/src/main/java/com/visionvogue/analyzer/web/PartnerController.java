package com.visionvogue.analyzer.web;

import com.visionvogue.analyzer.model.Partner;
import com.visionvogue.analyzer.repo.PartnerRepository;
import com.visionvogue.analyzer.service.PartnerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/partners")
public class PartnerController {
    private final PartnerRepository partnerRepository;
    private final PartnerService partnerService;

    public PartnerController(PartnerRepository partnerRepository, PartnerService partnerService) {
        this.partnerRepository = partnerRepository;
        this.partnerService = partnerService;
    }

    @PostMapping
    public ResponseEntity<Partner> create(@Valid @RequestBody CreatePartnerRequest request) {
        Partner saved = partnerService.createPartner(request.name());
        return ResponseEntity.created(URI.create("/api/partners/" + saved.getId())).body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Partner> getById(@PathVariable("id") UUID id) {
        return partnerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record CreatePartnerRequest(@NotBlank String name) {}
}
