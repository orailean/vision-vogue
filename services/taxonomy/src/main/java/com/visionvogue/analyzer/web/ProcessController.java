package com.visionvogue.analyzer.web;

import com.visionvogue.analyzer.service.FileProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/process")
public class ProcessController {
    private final FileProcessingService fileProcessingService;

    public ProcessController(FileProcessingService fileProcessingService) {
        this.fileProcessingService = fileProcessingService;
    }

    @PostMapping("/income")
    public ResponseEntity<FileProcessingService.ProcessingResult> processIncome() throws Exception {
        var result = fileProcessingService.processIncomeFolder();
        return ResponseEntity.ok(result);
    }
}

