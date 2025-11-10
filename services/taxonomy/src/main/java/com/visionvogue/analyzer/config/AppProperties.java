package com.visionvogue.analyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String analyzeUrl = "http://127.0.0.1:8000/analyze";
    private String incomeDir = "data/income";
    private String processedDir = "data/processed";
    private String failedDir = "data/failed";
    private int topKCategory = 3;
    private int topPerAttribute = 1;
    private int nColors = 5;

    public String getAnalyzeUrl() {
        return analyzeUrl;
    }

    public void setAnalyzeUrl(String analyzeUrl) {
        this.analyzeUrl = analyzeUrl;
    }

    public String getIncomeDir() {
        return incomeDir;
    }

    public void setIncomeDir(String incomeDir) {
        this.incomeDir = incomeDir;
    }

    public String getProcessedDir() {
        return processedDir;
    }

    public void setProcessedDir(String processedDir) {
        this.processedDir = processedDir;
    }

    public String getFailedDir() {
        return failedDir;
    }

    public void setFailedDir(String failedDir) {
        this.failedDir = failedDir;
    }

    public int getTopKCategory() {
        return topKCategory;
    }

    public void setTopKCategory(int topKCategory) {
        this.topKCategory = topKCategory;
    }

    public int getTopPerAttribute() {
        return topPerAttribute;
    }

    public void setTopPerAttribute(int topPerAttribute) {
        this.topPerAttribute = topPerAttribute;
    }

    public int getNColors() {
        return nColors;
    }

    public void setNColors(int nColors) {
        this.nColors = nColors;
    }
}

