package com.visionvogue.analyzer.web;

import com.visionvogue.analyzer.model.Partner;
import com.visionvogue.analyzer.repo.PartnerRepository;
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

    public PartnerController(PartnerRepository partnerRepository) {
        this.partnerRepository = partnerRepository;
    }

    @PostMapping
    public ResponseEntity<Partner> create(@Valid @RequestBody CreatePartnerRequest request) {
        Partner p = new Partner();
        p.setName(request.name());
        Partner saved = partnerRepository.save(p);
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
