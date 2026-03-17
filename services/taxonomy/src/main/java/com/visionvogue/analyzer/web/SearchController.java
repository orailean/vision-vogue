package com.visionvogue.analyzer.web;

import com.visionvogue.analyzer.service.SemanticSearchService;
import com.visionvogue.analyzer.service.VisualSearchService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SemanticSearchService semanticSearchService;
    private final VisualSearchService visualSearchService;

    public SearchController(SemanticSearchService semanticSearchService,
                            VisualSearchService visualSearchService) {
        this.semanticSearchService = semanticSearchService;
        this.visualSearchService = visualSearchService;
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

    /**
     * Upload an image and receive the most visually similar products for the partner.
     * The image is analysed by the ML model; the resulting attributes drive the search.
     */
    @PostMapping(value = "/visual", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VisualSearchService.VisualSearchResponse> visualSearch(
            @RequestParam("partnerId") UUID partnerId,
            @RequestPart("image") MultipartFile image,
            @RequestParam(value = "topK", defaultValue = "8") int topK,
            @RequestParam(value = "simWeight", defaultValue = "0.85") double simWeight
    ) throws IOException {
        if (image.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var response = visualSearchService.search(partnerId, image, topK, simWeight);
        return ResponseEntity.ok(response);
    }
}

