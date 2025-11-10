package com.visionvogue.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisResponse {
    private List<CategoryEntry> category;
    private Map<String, List<LabelConfidence>> attributes;
    private List<ColorEntry> colors;

    public List<CategoryEntry> getCategory() {
        return category;
    }

    public void setCategory(List<CategoryEntry> category) {
        this.category = category;
    }

    public Map<String, List<LabelConfidence>> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, List<LabelConfidence>> attributes) {
        this.attributes = attributes;
    }

    public List<ColorEntry> getColors() {
        return colors;
    }

    public void setColors(List<ColorEntry> colors) {
        this.colors = colors;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CategoryEntry {
        private String label;
        private double confidence;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LabelConfidence {
        private String label;
        private double confidence;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColorEntry {
        private String hex;
        private double percent;

        public String getHex() {
            return hex;
        }

        public void setHex(String hex) {
            this.hex = hex;
        }

        public double getPercent() {
            return percent;
        }

        public void setPercent(double percent) {
            this.percent = percent;
        }
    }
}

