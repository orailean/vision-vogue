package com.visionvogue.analyzer.service;

import com.visionvogue.analyzer.dto.AnalysisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@Service
public class VisualSearchService {

    private static final Logger log = LoggerFactory.getLogger(VisualSearchService.class);

    private final AnalyzeClient analyzeClient;
    private final SemanticSearchService semanticSearchService;

    public VisualSearchService(AnalyzeClient analyzeClient, SemanticSearchService semanticSearchService) {
        this.analyzeClient = analyzeClient;
        this.semanticSearchService = semanticSearchService;
    }

    /**
     * Given an uploaded image and a partner, analyse the image with the ML model,
     * synthesise a rich text query from the analysis result, then run semantic search
     * against the partner's existing product catalog.
     */
    public VisualSearchResponse search(UUID partnerId, MultipartFile image, int topK, double simWeight)
            throws IOException {

        File tmp = storeTemp(image);
        try {
            log.info("Visual search: partnerId={}, file={}, size={}", partnerId,
                    image.getOriginalFilename(), image.getSize());

            // Analyse the uploaded image using the same ML pipeline used for ingestion
            AnalysisResponse analysis = analyzeClient.callAnalyze(tmp);
            log.debug("Visual search analysis: categories={}, colors={}",
                    analysis.getCategory(), analysis.getColors());

            // Build a rich text query from the analysis (mirrors buildSearchText in SemanticSearchService)
            String query = buildQueryFromAnalysis(analysis);
            log.debug("Visual search query: {}", query);

            List<SemanticSearchService.SearchResult> results =
                    semanticSearchService.search(partnerId, query, topK, simWeight);

            return new VisualSearchResponse(query, analysis, results);
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }

    // -------------------------------------------------------------------------
    // Build a descriptive text query from the analysis result.
    // Mirrors the document-construction logic in SemanticSearchService so query
    // and document vectors live in the same semantic space.
    // -------------------------------------------------------------------------
    private String buildQueryFromAnalysis(AnalysisResponse analysis) {
        StringBuilder sb = new StringBuilder();

        // Categories — top one repeated twice for emphasis
        if (analysis.getCategory() != null) {
            boolean first = true;
            for (AnalysisResponse.CategoryEntry cat : analysis.getCategory()) {
                if (cat.getLabel() == null || cat.getLabel().isBlank()) continue;
                sb.append("category: ").append(cat.getLabel()).append('\n');
                if (first) {
                    sb.append("category: ").append(cat.getLabel()).append('\n');
                    first = false;
                }
            }
        }

        // Attributes — ordered from highest to lowest semantic signal
        List<String> orderedGroups = List.of(
                "style", "gender", "occasion", "season",
                "color", "pattern", "material", "texture",
                "fit", "silhouette", "length",
                "sleeve", "neckline", "waist", "rise", "closure",
                "detail", "transparency");

        if (analysis.getAttributes() != null) {
            Set<String> emitted = new LinkedHashSet<>();
            List<String> groups = new ArrayList<>(orderedGroups);
            analysis.getAttributes().keySet().stream()
                    .filter(g -> !orderedGroups.contains(g))
                    .forEach(groups::add);

            for (String group : groups) {
                List<AnalysisResponse.LabelConfidence> entries = analysis.getAttributes().get(group);
                if (entries == null) continue;
                List<String> labels = entries.stream()
                        .map(e -> e.getLabel() == null ? "" : e.getLabel().trim().toLowerCase())
                        .filter(s -> !s.isBlank() && emitted.add(group + ":" + s))
                        .toList();
                if (!labels.isEmpty()) {
                    sb.append(group).append(": ").append(String.join(", ", labels)).append('\n');
                }
            }
        }

        // Colors — hex → name
        if (analysis.getColors() != null) {
            List<String> colorNames = analysis.getColors().stream()
                    .map(c -> SemanticSearchService.hexToColorName(c.getHex()))
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
            if (!colorNames.isEmpty()) {
                sb.append("dominant colors: ").append(String.join(", ", colorNames)).append('\n');
            }
        }

        return sb.toString().trim();
    }

    // -------------------------------------------------------------------------
    // Write the multipart upload to a temp file so AnalyzeClient can read it
    // -------------------------------------------------------------------------
    private File storeTemp(MultipartFile image) throws IOException {
        String original = image.getOriginalFilename();
        String suffix = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.'))
                : ".jpg";
        File tmp = Files.createTempFile("vv-visual-search-", suffix).toFile();
        image.transferTo(tmp);
        return tmp;
    }

    // -------------------------------------------------------------------------
    // Response DTO
    // -------------------------------------------------------------------------
    public record VisualSearchResponse(
            String queryText,
            AnalysisResponse analysis,
            List<SemanticSearchService.SearchResult> results
    ) {}
}

