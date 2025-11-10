package com.visionvogue.analyzer.web;

import com.visionvogue.analyzer.model.AnalysisRecord;
import com.visionvogue.analyzer.repo.AnalysisRecordRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/results")
public class ResultsController {
    private final AnalysisRecordRepository repo;

    public ResultsController(AnalysisRecordRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<AnalysisRecord> list() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisRecord> get(@PathVariable("id") UUID id) {
        return repo.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}

