package com.visionvogue.analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionvogue.analyzer.config.AppProperties;
import com.visionvogue.analyzer.dto.AnalysisResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;

@Component
public class AnalyzeClient {
    private final RestTemplate restTemplate;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public AnalyzeClient(RestTemplate restTemplate, AppProperties props, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public AnalysisResponse callAnalyze(File file) {
        String url = String.format("%s?top_k_category=%d&top_per_attribute=%d&n_colors=%d",
                props.getAnalyzeUrl(), props.getTopKCategory(), props.getTopPerAttribute(), props.getNColors());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        FileSystemResource resource = new FileSystemResource(file);
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(detectContentType(file));
        HttpEntity<FileSystemResource> filePart = new HttpEntity<>(resource, fileHeaders);
        body.add("file", filePart);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Analyzer API returned status: " + response.getStatusCode());
        }
        try {
            return objectMapper.readValue(response.getBody(), AnalysisResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse analyzer response", e);
        }
    }

    private MediaType detectContentType(File file) {
        try {
            String mime = java.nio.file.Files.probeContentType(file.toPath());
            if (mime != null) return MediaType.parseMediaType(mime);
        } catch (Exception ignored) {}
        String name = file.getName().toLowerCase();
        if (name.endsWith(".avif")) return MediaType.parseMediaType("image/avif");
        if (name.endsWith(".heic")) return MediaType.parseMediaType("image/heic");
        if (name.endsWith(".heif")) return MediaType.parseMediaType("image/heif");
        if (name.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (name.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".jpe") || name.endsWith(".jfif") || name.endsWith(".pjpeg")) return MediaType.IMAGE_JPEG;
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (name.endsWith(".tif") || name.endsWith(".tiff")) return MediaType.parseMediaType("image/tiff");
        if (name.endsWith(".bmp")) return MediaType.parseMediaType("image/bmp");
        if (name.endsWith(".svg")) return MediaType.parseMediaType("image/svg+xml");
        if (name.endsWith(".ico")) return MediaType.parseMediaType("image/x-icon");
        if (name.endsWith(".jp2") || name.endsWith(".j2k")) return MediaType.parseMediaType("image/jp2");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
