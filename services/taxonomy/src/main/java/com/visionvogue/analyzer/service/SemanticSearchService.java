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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SemanticSearchService {
    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "for", "the", "with", "in", "on", "of", "to",
            "item", "items", "clothing", "clothes", "garment", "garments"
    );
    private static final Map<String, String> COLOR_ALIASES = buildColorAliases();
    private static final Map<String, String> GENDER_ALIASES = Map.ofEntries(
            Map.entry("man", "men"),
            Map.entry("men", "men"),
            Map.entry("mens", "men"),
            Map.entry("male", "men"),
            Map.entry("males", "men"),
            Map.entry("woman", "women"),
            Map.entry("women", "women"),
            Map.entry("womens", "women"),
            Map.entry("female", "women"),
            Map.entry("females", "women"),
            Map.entry("unisex", "unisex"),
            Map.entry("gender neutral", "unisex"),
            Map.entry("genderless", "unisex"),
            Map.entry("boy", "boys"),
            Map.entry("boys", "boys"),
            Map.entry("girl", "girls"),
            Map.entry("girls", "girls"),
            Map.entry("kid", "kids"),
            Map.entry("kids", "kids"),
            Map.entry("child", "kids"),
            Map.entry("children", "kids")
    );

    // -------------------------------------------------------------------------
    // Nearest CSS/common color names for hex-to-name conversion
    // -------------------------------------------------------------------------
    private static final int[][] COLOR_PALETTE_RGB = {
        {0,0,0},{255,255,255},{128,128,128},{192,192,192},{255,0,0},{139,0,0},
        {128,0,0},{220,20,60},{255,165,0},{255,127,80},{255,218,185},{255,255,0},
        {255,215,0},{255,193,7},{255,248,220},{245,245,220},{0,128,0},{0,100,0},
        {80,200,120},{50,205,50},{152,251,152},{0,128,128},{0,139,139},{64,224,208},
        {0,255,255},{0,0,255},{0,0,139},{65,105,225},{135,206,235},{100,149,237},
        {128,0,128},{75,0,130},{238,130,238},{255,0,255},{255,192,203},{255,105,180},
        {255,20,147},{139,69,19},{160,82,45},{210,180,140},{245,222,179},{245,245,220},
        {255,228,196},{255,239,213},{253,245,230},{107,142,35},{154,205,50},{240,230,140},
        {189,183,107},{47,79,79},{105,105,105},{169,169,169},{211,211,211},{220,220,220},
    };
    private static final String[] COLOR_PALETTE_NAMES = {
        "black","white","gray","silver","red","dark red",
        "maroon","crimson","orange","coral","peach","yellow",
        "gold","amber","cream","beige","green","dark green",
        "emerald","lime green","light green","teal","dark teal","turquoise",
        "cyan","blue","dark blue","royal blue","sky blue","cornflower blue",
        "purple","indigo","violet","magenta","pink","hot pink",
        "deep pink","brown","saddle brown","tan","wheat","beige",
        "bisque","papaya","floral white","olive","yellow green","khaki",
        "dark khaki","dark slate gray","dim gray","dark gray","light gray","gainsboro",
    };

    // -------------------------------------------------------------------------
    // Embedding cache: recordId → (contentHash, embedding)
    // Invalidated when the record's JSON content changes.
    // -------------------------------------------------------------------------
    private record CachedEmbedding(int contentHash, float[] vector) {}
    private record SearchIntent(
            String originalQuery,
            String normalizedQuery,
            Set<String> colors,
            Set<String> genders,
            List<String> freeTextTokens
    ) {
        boolean hasStructuredTerms() {
            return !colors.isEmpty() || !genders.isEmpty();
        }

        boolean hasFreeTextTerms() {
            return !freeTextTokens.isEmpty();
        }

        boolean hasOnlyStructuredTerms() {
            return hasStructuredTerms() && !hasFreeTextTerms();
        }
    }

    private record RecordMetadata(
            Set<String> attributeColors,
            Set<String> dominantColors,
            Set<String> genders,
            Set<String> searchableTerms
    ) {
        Set<String> allColors() {
            Set<String> all = new LinkedHashSet<>(attributeColors);
            all.addAll(dominantColors);
            return all;
        }
    }

    private final Map<UUID, CachedEmbedding> embeddingCache = new ConcurrentHashMap<>();

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

    // -------------------------------------------------------------------------
    // Public search entry point
    // -------------------------------------------------------------------------
    public List<SearchResult> search(UUID partnerId, String prompt, int topK, double simWeight) {
        if (partnerId == null || prompt == null || prompt.isBlank()) return List.of();
        topK = Math.max(1, Math.min(topK, 100));
        simWeight = Math.max(0.0, Math.min(simWeight, 1.0));
        SearchIntent intent = parseIntent(prompt);

        List<AnalysisRecord> records = repository.findByPartnerId(partnerId)
                .stream()
                .filter(r -> r.getStatus() == AnalysisRecord.Status.SUCCESS)
                .toList();
        if (records.isEmpty()) return List.of();

        // Build document texts for each record
        Map<UUID, String> docTexts = new LinkedHashMap<>();
        Map<UUID, RecordMetadata> metadataByRecord = new LinkedHashMap<>();
        for (AnalysisRecord ar : records) {
            String text = buildSearchText(ar);
            if (!text.isBlank()) {
                docTexts.put(ar.getId(), text);
                metadataByRecord.put(ar.getId(), extractMetadata(ar, text));
            }
        }
        if (docTexts.isEmpty()) return List.of();

        // Expand the query to match the document style
        String expandedQuery = expandQuery(intent);
        log.debug("Query expanded: '{}' → '{}'", prompt, expandedQuery);

        // Collect texts that need a fresh embedding (cache miss or content changed)
        List<UUID> toEmbed = new ArrayList<>();
        List<String> toEmbedTexts = new ArrayList<>();
        for (AnalysisRecord ar : records) {
            String text = docTexts.get(ar.getId());
            if (text == null) continue;
            int hash = text.hashCode();
            CachedEmbedding cached = embeddingCache.get(ar.getId());
            if (cached == null || cached.contentHash() != hash) {
                toEmbed.add(ar.getId());
                toEmbedTexts.add(text);
            }
        }

        // Single batch API call: query + all uncached documents
        List<String> batchTexts = new ArrayList<>();
        batchTexts.add(expandedQuery);
        batchTexts.addAll(toEmbedTexts);

        List<float[]> batchVectors = embedBatch(batchTexts);
        if (batchVectors.isEmpty()) return List.of();

        float[] queryVec = batchVectors.get(0);

        // Store freshly computed document embeddings in cache
        for (int i = 0; i < toEmbed.size(); i++) {
            float[] vec = batchVectors.get(i + 1);
            String text = toEmbedTexts.get(i);
            embeddingCache.put(toEmbed.get(i), new CachedEmbedding(text.hashCode(), vec));
        }

        // Score every record
        List<SearchResult> scored = new ArrayList<>();
        for (AnalysisRecord ar : records) {
            String text = docTexts.get(ar.getId());
            if (text == null) continue;
            CachedEmbedding cached = embeddingCache.get(ar.getId());
            if (cached == null) continue;
            RecordMetadata metadata = metadataByRecord.get(ar.getId());
            if (metadata == null) continue;

            if (!matchesStructuredFilters(intent, metadata)) {
                continue;
            }

            double sim = cosine(queryVec, cached.vector());
            double taxonomyScore = computeTaxonomyScore(intent, metadata);
            double minAllowedSimilarity = intent.hasOnlyStructuredTerms()
                    ? Math.max(0.15, minSimilarityThreshold * 0.6)
                    : minSimilarityThreshold;
            if (sim < minAllowedSimilarity && taxonomyScore < 0.7) {
                log.debug("Filtered out '{}' (sim={}) for query '{}'",
                        ar.getFilename(), String.format("%.3f", sim), prompt.substring(0, Math.min(40, prompt.length())));
                continue;
            }

            // Combined score: primarily similarity; category confidence gives a small nudge
            // when the model is very confident (>=0.7) to break ties between near-equal items.
            double catConf = Optional.ofNullable(ar.getTopCategoryConfidence()).orElse(0.0);
            double confidenceBoost = catConf >= 0.7 ? (1.0 - simWeight) * catConf : 0.0;
            double semanticScore = simWeight * sim + confidenceBoost;
            double combined = blendScores(intent, semanticScore, taxonomyScore);
            scored.add(toResult(ar, sim, catConf, combined, text));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble((SearchResult r) -> r.combinedScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Query expansion: turn a short user query into a fashion-style sentence
    // that better matches the document embeddings
    // -------------------------------------------------------------------------
    private String expandQuery(SearchIntent intent) {
        String q = intent.originalQuery().trim();
        // If already a long/descriptive phrase don't re-wrap it
        if (q.split("\\s+").length >= 6) return q;
        if (intent.hasOnlyStructuredTerms()) {
            List<String> descriptors = new ArrayList<>();
            if (!intent.colors().isEmpty()) {
                descriptors.add("color " + String.join(" ", intent.colors()));
            }
            if (!intent.genders().isEmpty()) {
                descriptors.add("for " + String.join(" ", intent.genders()));
            }
            return "a fashion garment with " + String.join(" and ", descriptors);
        }
        return "a fashion garment: " + q + ". clothing style color material pattern";
    }

    // -------------------------------------------------------------------------
    // Document construction
    // -------------------------------------------------------------------------
    private String buildSearchText(AnalysisRecord ar) {
        StringBuilder sb = new StringBuilder();

        // --- Categories (all of them, weighted by confidence) ---
        try {
            if (ar.getCategoryJson() != null && !ar.getCategoryJson().isBlank()) {
                List<Map<String, Object>> cats = objectMapper.readValue(
                        ar.getCategoryJson(), new TypeReference<>() {});
                for (Map<String, Object> cat : cats) {
                    String label = Objects.toString(cat.get("label"), "").trim();
                    if (label.isBlank()) continue;
                    double conf = toDouble(cat.get("confidence"));
                    // Repeat the top category proportional to confidence so it
                    // carries more semantic weight in the embedding
                    int repeats = conf >= 0.6 ? 3 : (conf >= 0.35 ? 2 : 1);
                    for (int i = 0; i < repeats; i++) {
                        sb.append("category: ").append(label).append('\n');
                    }
                }
            } else if (ar.getTopCategoryLabel() != null) {
                sb.append("category: ").append(ar.getTopCategoryLabel()).append('\n');
                sb.append("category: ").append(ar.getTopCategoryLabel()).append('\n');
            }
        } catch (Exception ignored) {
            if (ar.getTopCategoryLabel() != null) {
                sb.append("category: ").append(ar.getTopCategoryLabel()).append('\n');
            }
        }

        // --- Attributes (key groups with human-friendly labels) ---
        // High-signal groups are listed first and with a descriptive prefix
        try {
            if (ar.getAttributesJson() != null && !ar.getAttributesJson().isBlank()) {
                Map<String, List<Map<String, Object>>> attrs = objectMapper.readValue(
                        ar.getAttributesJson(), new TypeReference<>() {});

                // Ordered from highest to lowest semantic signal
                List<String> orderedGroups = List.of(
                        "style", "gender", "occasion", "season",
                        "color", "pattern", "material", "texture",
                        "fit", "silhouette", "length",
                        "sleeve", "neckline", "waist", "rise", "closure",
                        "detail", "transparency");

                Set<String> seen = new LinkedHashSet<>();
                // Emit ordered groups first, then any remaining
                List<String> allGroups = new ArrayList<>(orderedGroups);
                attrs.keySet().stream()
                        .filter(g -> !orderedGroups.contains(g))
                        .forEach(allGroups::add);

                for (String group : allGroups) {
                    List<Map<String, Object>> entries = attrs.get(group);
                    if (entries == null) continue;
                    List<String> labels = entries.stream()
                            .map(m -> Objects.toString(m.get("label"), "").trim().toLowerCase())
                            .filter(s -> !s.isBlank() && seen.add(group + ":" + s))
                            .toList();
                    if (labels.isEmpty()) continue;
                    sb.append(group).append(": ").append(String.join(", ", labels)).append('\n');
                }
            }
        } catch (Exception ignored) {}

        // --- Colors (convert hex → nearest color name) ---
        try {
            if (ar.getColorsJson() != null && !ar.getColorsJson().isBlank()) {
                List<Map<String, Object>> colors = objectMapper.readValue(
                        ar.getColorsJson(), new TypeReference<>() {});
                List<String> colorNames = colors.stream()
                        .map(m -> hexToColorName(Objects.toString(m.get("hex"), "")))
                        .filter(s -> !s.isBlank())
                        .distinct()
                        .toList();
                if (!colorNames.isEmpty()) {
                    sb.append("dominant colors: ").append(String.join(", ", colorNames)).append('\n');
                }
            }
        } catch (Exception ignored) {}

        return sb.toString().trim();
    }

    private SearchIntent parseIntent(String query) {
        String normalized = normalizeText(query);
        Set<String> matchedPhrases = new LinkedHashSet<>();
        Set<String> colors = detectAliases(normalized, COLOR_ALIASES, matchedPhrases);
        Set<String> genders = detectAliases(normalized, GENDER_ALIASES, matchedPhrases);
        Set<String> structuredTokens = matchedPhrases.stream()
                .flatMap(phrase -> Arrays.stream(phrase.split("\\s+")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> freeTextTokens = tokenize(normalized).stream()
                .filter(token -> !STOP_WORDS.contains(token))
                .filter(token -> !structuredTokens.contains(token))
                .toList();
        return new SearchIntent(query, normalized, colors, genders, freeTextTokens);
    }

    private RecordMetadata extractMetadata(AnalysisRecord ar, String searchText) {
        Set<String> attributeColors = new LinkedHashSet<>();
        Set<String> dominantColors = new LinkedHashSet<>();
        Set<String> genders = new LinkedHashSet<>();

        try {
            if (ar.getAttributesJson() != null && !ar.getAttributesJson().isBlank()) {
                Map<String, List<Map<String, Object>>> attrs = objectMapper.readValue(
                        ar.getAttributesJson(), new TypeReference<>() {});
                for (Map.Entry<String, List<Map<String, Object>>> entry : attrs.entrySet()) {
                    if (entry.getValue() == null) continue;
                    for (Map<String, Object> value : entry.getValue()) {
                        String label = normalizeText(Objects.toString(value.get("label"), ""));
                        if (label.isBlank()) continue;
                        if ("color".equals(entry.getKey())) {
                            String canonical = canonicalColor(label);
                            if (!canonical.isBlank()) attributeColors.add(canonical);
                        } else if ("gender".equals(entry.getKey())) {
                            String canonical = canonicalGender(label);
                            if (!canonical.isBlank()) genders.add(canonical);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            if (ar.getColorsJson() != null && !ar.getColorsJson().isBlank()) {
                List<Map<String, Object>> colors = objectMapper.readValue(
                        ar.getColorsJson(), new TypeReference<>() {});
                for (Map<String, Object> color : colors) {
                    String canonical = canonicalColor(hexToColorName(Objects.toString(color.get("hex"), "")));
                    if (!canonical.isBlank()) dominantColors.add(canonical);
                }
            }
        } catch (Exception ignored) {}

        Set<String> searchableTerms = tokenize(normalizeText(searchText)).stream()
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new RecordMetadata(attributeColors, dominantColors, genders, searchableTerms);
    }

    private boolean matchesStructuredFilters(SearchIntent intent, RecordMetadata metadata) {
        if (!intent.colors().isEmpty()) {
            Set<String> recordColors = metadata.allColors();
            if (!recordColors.isEmpty() && Collections.disjoint(intent.colors(), recordColors)) {
                return false;
            }
        }
        if (!intent.genders().isEmpty() && !metadata.genders().isEmpty()) {
            boolean genderMatch = intent.genders().stream().anyMatch(queryGender -> matchesGender(queryGender, metadata.genders()));
            if (!genderMatch) {
                return false;
            }
        }
        return true;
    }

    private double computeTaxonomyScore(SearchIntent intent, RecordMetadata metadata) {
        double totalWeight = 0.0;
        double score = 0.0;

        if (!intent.colors().isEmpty()) {
            Double colorScore = scoreColorMatch(intent.colors(), metadata);
            if (colorScore != null) {
                totalWeight += 0.55;
                score += 0.55 * colorScore;
            }
        }

        if (!intent.genders().isEmpty()) {
            Double genderScore = scoreGenderMatch(intent.genders(), metadata.genders());
            if (genderScore != null) {
                totalWeight += 0.45;
                score += 0.45 * genderScore;
            }
        }

        if (totalWeight == 0.0) {
            return 0.0;
        }
        return score / totalWeight;
    }

    private double blendScores(SearchIntent intent, double semanticScore, double taxonomyScore) {
        if (!intent.hasStructuredTerms()) {
            return semanticScore;
        }
        double taxonomyWeight = intent.hasOnlyStructuredTerms() ? 0.7 : 0.45;
        return (1.0 - taxonomyWeight) * semanticScore + taxonomyWeight * taxonomyScore;
    }

    private Double scoreColorMatch(Set<String> queryColors, RecordMetadata metadata) {
        if (metadata.attributeColors().isEmpty() && metadata.dominantColors().isEmpty()) {
            return null;
        }
        if (!Collections.disjoint(queryColors, metadata.attributeColors())) {
            return 1.0;
        }
        if (!Collections.disjoint(queryColors, metadata.dominantColors())) {
            return 0.8;
        }
        return 0.0;
    }

    private Double scoreGenderMatch(Set<String> queryGenders, Set<String> recordGenders) {
        if (recordGenders.isEmpty()) {
            return null;
        }
        double best = 0.0;
        for (String queryGender : queryGenders) {
            if (recordGenders.contains(queryGender)) {
                best = Math.max(best, 1.0);
            } else if ((queryGender.equals("men") || queryGender.equals("women")) && recordGenders.contains("unisex")) {
                best = Math.max(best, 0.75);
            } else if (queryGender.equals("kids") && (recordGenders.contains("boys") || recordGenders.contains("girls"))) {
                best = Math.max(best, 0.9);
            }
        }
        return best;
    }

    private boolean matchesGender(String queryGender, Set<String> recordGenders) {
        Double score = scoreGenderMatch(Set.of(queryGender), recordGenders);
        return score != null && score > 0.0;
    }

    // -------------------------------------------------------------------------
    // Hex color → nearest named color
    // -------------------------------------------------------------------------
    static String hexToColorName(String hex) {
        if (hex == null || hex.isBlank()) return "";
        String h = hex.trim().replaceFirst("^#", "");
        if (h.length() != 6) return "";
        try {
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            int best = 0;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < COLOR_PALETTE_RGB.length; i++) {
                int[] c = COLOR_PALETTE_RGB[i];
                double dist = Math.pow(r - c[0], 2) + Math.pow(g - c[1], 2) + Math.pow(b - c[2], 2);
                if (dist < bestDist) { bestDist = dist; best = i; }
            }
            return COLOR_PALETTE_NAMES[best];
        } catch (NumberFormatException e) {
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // Batch embedding — single HTTP round-trip for multiple texts
    // -------------------------------------------------------------------------
    private List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        try {
            EmbedRequest request = new EmbedRequest();
            request.texts = texts;
            request.normalize = true;
            request.batch_size = Math.min(texts.size(), 64);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<EmbedRequest> entity = new HttpEntity<>(request, headers);
            EmbedResponse response = restTemplate.postForObject(embeddingApiUrl, entity, EmbedResponse.class);

            if (response == null || response.embeddings == null || response.embeddings.size() != texts.size()) {
                log.warn("Unexpected embedding response size: expected {}, got {}",
                        texts.size(), response == null ? 0 : (response.embeddings == null ? 0 : response.embeddings.size()));
                return List.of();
            }

            return response.embeddings.stream()
                    .map(embedding -> {
                        float[] v = new float[embedding.size()];
                        for (int i = 0; i < embedding.size(); i++) v[i] = embedding.get(i).floatValue();
                        return v;
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Batch embedding API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding API call failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Cosine similarity (vectors assumed already L2-normalised by the API)
    // -------------------------------------------------------------------------
    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) return 0.0;
        double dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        // Clamp to [-1, 1] to guard against floating-point drift
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(Objects.toString(o, "0")); } catch (Exception e) { return 0.0; }
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replace("'", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static List<String> tokenize(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normalizedText.split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static Set<String> detectAliases(String normalizedText, Map<String, String> aliases, Set<String> matchedPhrases) {
        Set<String> values = new LinkedHashSet<>();
        aliases.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey(Comparator.comparingInt(String::length)).reversed())
                .forEach(entry -> {
                    if (containsPhrase(normalizedText, entry.getKey())) {
                        values.add(entry.getValue());
                        matchedPhrases.add(entry.getKey());
                    }
                });
        return values;
    }

    private static boolean containsPhrase(String normalizedText, String phrase) {
        return (" " + normalizedText + " ").contains(" " + phrase + " ");
    }

    private static String canonicalColor(String value) {
        return COLOR_ALIASES.getOrDefault(normalizeText(value), "");
    }

    private static String canonicalGender(String value) {
        return GENDER_ALIASES.getOrDefault(normalizeText(value), "");
    }

    private static Map<String, String> buildColorAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        registerColorAliases(aliases, "black", "black");
        registerColorAliases(aliases, "white", "white", "floral white");
        registerColorAliases(aliases, "gray", "gray", "grey", "silver", "gainsboro", "light gray", "dark gray", "dim gray", "dark slate gray");
        registerColorAliases(aliases, "red", "red", "dark red", "maroon", "burgundy", "crimson", "coral");
        registerColorAliases(aliases, "orange", "orange", "amber", "peach", "papaya");
        registerColorAliases(aliases, "yellow", "yellow", "gold", "mustard", "khaki");
        registerColorAliases(aliases, "green", "green", "dark green", "emerald", "lime", "lime green", "light green", "mint", "olive", "yellow green", "sage");
        registerColorAliases(aliases, "blue", "blue", "dark blue", "royal blue", "sky blue", "cornflower blue", "navy", "cyan", "aqua", "teal", "dark teal", "turquoise", "denim blue");
        registerColorAliases(aliases, "purple", "purple", "indigo", "violet", "lavender", "plum");
        registerColorAliases(aliases, "pink", "pink", "hot pink", "deep pink", "magenta", "fuchsia", "rose", "blush");
        registerColorAliases(aliases, "brown", "brown", "saddle brown", "tan", "beige", "cream", "ivory", "wheat", "bisque");
        registerColorAliases(aliases, "metallic", "metallic");
        registerColorAliases(aliases, "neon", "neon");
        registerColorAliases(aliases, "pastel", "pastel");
        registerColorAliases(aliases, "multicolor", "multicolor");
        return aliases;
    }

    private static void registerColorAliases(Map<String, String> aliases, String canonical, String... labels) {
        for (String label : labels) {
            aliases.put(normalizeText(label), canonical);
        }
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

    // -------------------------------------------------------------------------
    // Evict a record from the embedding cache (call after record is updated)
    // -------------------------------------------------------------------------
    public void evictCache(UUID recordId) {
        embeddingCache.remove(recordId);
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

    static class EmbedRequest {
        public List<String> texts;
        public Boolean normalize;
        public Integer batch_size;
    }

    static class EmbedResponse {
        public List<List<Double>> embeddings;
    }
}
