package com.visionvogue.analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VisionVogueAnalyzerApplication {
    public static void main(String[] args) {
        SpringApplication.run(VisionVogueAnalyzerApplication.class, args);
    }
}

