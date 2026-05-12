package com.example.exam.service;

import com.example.exam.dto.AiSettingsDtos.AiSettingsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {
    private static final Duration EMBEDDING_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public List<Float> embed(String input, AiSettingsResponse settings) {
        if (input == null || input.isBlank() || settings.embeddingApiKey() == null || settings.embeddingApiKey().isBlank()) {
            return List.of();
        }
        try {
            String endpoint = normalizeEmbeddingEndpoint(settings.embeddingEndpoint());
            Map<String, Object> payload = Map.of(
                    "model", blank(settings.embeddingModel()) ? AiSettingsService.DEFAULT_EMBEDDING_MODEL : settings.embeddingModel(),
                    "input", input
            );
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(EMBEDDING_TIMEOUT)
                    .header("Authorization", "Bearer " + settings.embeddingApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }
            JsonNode embedding = mapper.readTree(response.body()).at("/data/0/embedding");
            if (!embedding.isArray()) {
                return List.of();
            }
            List<Float> values = new ArrayList<>(embedding.size());
            for (JsonNode value : embedding) {
                values.add((float) value.asDouble());
            }
            return values;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeEmbeddingEndpoint(String endpoint) {
        String value = blank(endpoint) ? AiSettingsService.DEFAULT_EMBEDDING_ENDPOINT : endpoint.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/embeddings")) {
            return value;
        }
        if (value.endsWith("/v1") || value.endsWith("/compatible-mode/v1")) {
            return value + "/embeddings";
        }
        return value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
