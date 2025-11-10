package com.visionvogue.analyzer.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "analysis_records")
public class AnalysisRecord {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status = Status.SUCCESS;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "top_category_label")
    private String topCategoryLabel;

    @Column(name = "top_category_confidence")
    private Double topCategoryConfidence;

    @Column(name = "category_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String categoryJson;

    @Column(name = "attributes_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String attributesJson;

    @Column(name = "colors_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String colorsJson;

    @Column(name = "raw_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawJson;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "partner_id", columnDefinition = "uuid")
    private UUID partnerId;

    public enum Status { SUCCESS, FAILED }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getTopCategoryLabel() {
        return topCategoryLabel;
    }

    public void setTopCategoryLabel(String topCategoryLabel) {
        this.topCategoryLabel = topCategoryLabel;
    }

    public Double getTopCategoryConfidence() {
        return topCategoryConfidence;
    }

    public void setTopCategoryConfidence(Double topCategoryConfidence) {
        this.topCategoryConfidence = topCategoryConfidence;
    }

    public String getCategoryJson() {
        return categoryJson;
    }

    public void setCategoryJson(String categoryJson) {
        this.categoryJson = categoryJson;
    }

    public String getAttributesJson() {
        return attributesJson;
    }

    public void setAttributesJson(String attributesJson) {
        this.attributesJson = attributesJson;
    }

    public String getColorsJson() {
        return colorsJson;
    }

    public void setColorsJson(String colorsJson) {
        this.colorsJson = colorsJson;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public UUID getPartnerId() { return partnerId; }
    public void setPartnerId(UUID partnerId) { this.partnerId = partnerId; }
}
