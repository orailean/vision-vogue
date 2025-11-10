package com.visionvogue.analyzer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionvogue.analyzer.model.AnalysisRecord;
import com.visionvogue.analyzer.repo.AnalysisRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SemanticSearchService {
    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);

    private final AnalysisRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String embeddingApiUrl;
    private final double minSimilarityThreshold;

    public SemanticSearchService(AnalysisRecordRepository repository,
                                  ObjectMapper objectMapper,
                                  @Value("${embedding.api.url:http://127.0.0.1:8000/embed}") String embeddingApiUrl,
                                  @Value("${semantic.search.min-similarity-threshold:0.35}") double minSimilarityThreshold) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.embeddingApiUrl = embeddingApiUrl;
        this.minSimilarityThreshold = minSimilarityThreshold;
    }

    public List<SearchResult> search(UUID partnerId, String prompt, int topK, double simWeight) {
        if (partnerId == null || prompt == null || prompt.isBlank()) return List.of();
        topK = Math.max(1, Math.min(topK, 100));
        simWeight = Math.max(0.0, Math.min(simWeight, 1.0));

        List<AnalysisRecord> records = repository.findByPartnerId(partnerId);
        if (records.isEmpty()) return List.of();

        // Embed the query
        float[] q = embed(prompt);

        List<SearchResult> scored = new ArrayList<>();
        for (AnalysisRecord ar : records) {
            String text = buildSearchText(ar);
            if (text.isBlank()) continue;

            float[] v = embed(text);
            double sim = cosine(q, v);

            // Filter out results with low semantic similarity
            if (sim < minSimilarityThreshold) {
                log.debug("Filtered out {} (similarity: {}) for query: {}",
                    ar.getFilename(), sim, prompt.substring(0, Math.min(30, prompt.length())));
                continue;
            }

            double catConf = Optional.ofNullable(ar.getTopCategoryConfidence()).orElse(0.0);
            double combined = simWeight * sim + (1.0 - simWeight) * catConf;
            scored.add(toResult(ar, sim, catConf, combined, text));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble((SearchResult r) -> r.combinedScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private float[] embed(String text) {
        try {
            EmbedRequest request = new EmbedRequest();
            request.texts = List.of(text);
            request.normalize = true;
            request.batch_size = 32;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<EmbedRequest> entity = new HttpEntity<>(request, headers);

            EmbedResponse response = restTemplate.postForObject(embeddingApiUrl, entity, EmbedResponse.class);

            if (response == null || response.embeddings == null || response.embeddings.isEmpty()) {
                log.warn("Empty embedding response for text: {}", text.substring(0, Math.min(50, text.length())));
                return new float[0];
            }

            List<Double> embedding = response.embeddings.get(0);
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;

        } catch (Exception e) {
            log.error("Failed to embed text via API: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding API call failed", e);
        }
    }


    private String buildSearchText(AnalysisRecord ar) {
        StringBuilder sb = new StringBuilder();
        if (ar.getTopCategoryLabel() != null) sb.append("category: ").append(ar.getTopCategoryLabel()).append('\n');
        if (ar.getFilename() != null) sb.append("filename: ").append(ar.getFilename()).append('\n');

        // attributes JSON -> flatten as key: label set
        try {
            if (ar.getAttributesJson() != null && !ar.getAttributesJson().isBlank()) {
                Map<String, List<Map<String, Object>>> attrs = objectMapper.readValue(
                        ar.getAttributesJson(), new TypeReference<>() {});
                for (Map.Entry<String, List<Map<String, Object>>> e : attrs.entrySet()) {
                    List<String> labels = e.getValue() == null ? List.of() : e.getValue().stream()
                            .map(m -> Objects.toString(m.get("label"), ""))
                            .filter(s -> !s.isBlank())
                            .toList();
                    if (!labels.isEmpty()) {
                        sb.append(e.getKey()).append(": ").append(String.join(", ", labels)).append('\n');
                    }
                }
            }
        } catch (Exception ignored) {}

        // colors JSON -> include hexes
        try {
            if (ar.getColorsJson() != null && !ar.getColorsJson().isBlank()) {
                List<Map<String, Object>> colors = objectMapper.readValue(
                        ar.getColorsJson(), new TypeReference<>() {});
                List<String> hexes = colors.stream()
                        .map(m -> Objects.toString(m.get("hex"), ""))
                        .filter(s -> !s.isBlank())
                        .toList();
                if (!hexes.isEmpty()) {
                    sb.append("colors: ").append(String.join(" ", hexes)).append('\n');
                }
            }
        } catch (Exception ignored) {}

        return sb.toString().trim();
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static SearchResult toResult(AnalysisRecord ar, double sim, double catConf, double combined, String text) {
        SearchResult r = new SearchResult();
        r.recordId = ar.getId();
        r.filename = ar.getFilename();
        r.topCategoryLabel = ar.getTopCategoryLabel();
        r.topCategoryConfidence = catConf;
        r.similarity = sim;
        r.combinedScore = combined;
        r.text = text;
        return r;
    }

    public static class SearchResult {
        public UUID recordId;
        public String filename;
        public String topCategoryLabel;
        public Double topCategoryConfidence;
        public Double similarity;
        public Double combinedScore;
        public String text;
    }

    // DTO for embedding API request
    static class EmbedRequest {
        public List<String> texts;
        public Boolean normalize;
        public Integer batch_size;
    }

    // DTO for embedding API response
    static class EmbedResponse {
        public List<List<Double>> embeddings;
    }
}

