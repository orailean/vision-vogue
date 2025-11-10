package com.visionvogue.analyzer.web;

import com.visionvogue.analyzer.service.SemanticSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SemanticSearchService semanticSearchService;

    public SearchController(SemanticSearchService semanticSearchService) {
        this.semanticSearchService = semanticSearchService;
    }

    @GetMapping("/semantic")
    public ResponseEntity<List<SemanticSearchService.SearchResult>> search(
            @RequestParam("partnerId") UUID partnerId,
            @RequestParam("q") String prompt,
            @RequestParam(value = "topK", defaultValue = "5") int topK,
            @RequestParam(value = "simWeight", defaultValue = "0.8") double simWeight
    ) {
        var results = semanticSearchService.search(partnerId, prompt, topK, simWeight);
        return ResponseEntity.ok(results);
    }
}

