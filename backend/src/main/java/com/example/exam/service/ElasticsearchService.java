package com.example.exam.service;

import com.example.exam.dto.AiSettingsDtos.AiSettingsResponse;
import com.example.exam.model.KnowledgeChunk;
import com.example.exam.model.StudyFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ElasticsearchService {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchService.class);
    private static final int CANDIDATE_SIZE = 20;
    private static final int FUSED_RESULT_SIZE = 20;
    private static final int RRF_K = 60;
    private static final Duration ES_REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private static final long FAILURE_BACKOFF_MILLIS = Duration.ofSeconds(30).toMillis();

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final EmbeddingService embeddingService;
    private final String baseUrl;
    private final String indexName;
    private final boolean enabled;
    private volatile boolean indexChecked = false;
    private volatile long unavailableUntilMillis = 0;

    public ElasticsearchService(EmbeddingService embeddingService,
                                @Value("${app.elasticsearch.url:http://localhost:9200}") String baseUrl,
                                @Value("${app.elasticsearch.index:smart_exam_chunks}") String indexName,
                                @Value("${app.elasticsearch.enabled:true}") boolean enabled) {
        this.embeddingService = embeddingService;
        this.baseUrl = trimRight(baseUrl);
        this.indexName = indexName;
        this.enabled = enabled;
    }

    public void reindexFile(Long userId, StudyFile file, List<KnowledgeChunk> chunks, AiSettingsResponse settings) {
        if (!enabled) return;
        IndexedFile fileSnapshot = new IndexedFile(
                file.getId(),
                file.getFolder().getId(),
                userId,
                file.getOriginalName(),
                file.getUploadedAt().toString()
        );
        List<IndexedChunk> chunkSnapshots = chunks.stream()
                .map(chunk -> new IndexedChunk(chunk.getId(), chunk.getChunkIndex(), chunk.getContent()))
                .toList();
        CompletableFuture.runAsync(() -> reindexFileNow(fileSnapshot, chunkSnapshots, settings));
    }

    private void reindexFileNow(IndexedFile file, List<IndexedChunk> chunks, AiSettingsResponse settings) {
        try {
            ensureIndex(settings.embeddingDimensions());
            deleteByFileIdNow(file.userId(), file.fileId());
            if (chunks.isEmpty()) return;
            StringBuilder bulk = new StringBuilder();
            for (IndexedChunk chunk : chunks) {
                Map<String, Object> action = Map.of("index", Map.of("_index", indexName, "_id", chunk.chunkId().toString()));
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("chunkId", chunk.chunkId());
                source.put("fileId", file.fileId());
                source.put("folderId", file.folderId());
                source.put("userId", file.userId());
                source.put("fileName", file.fileName());
                source.put("chunkIndex", chunk.chunkIndex());
                source.put("content", chunk.content());
                source.put("uploadedAt", file.uploadedAt());
                List<Float> embedding = embeddingService.embed(chunk.content(), settings);
                if (!embedding.isEmpty()) {
                    source.put("embedding", embedding);
                }
                bulk.append(mapper.writeValueAsString(action)).append('\n');
                bulk.append(mapper.writeValueAsString(source)).append('\n');
            }
            request("POST", "/_bulk", bulk.toString() + "\n");
        } catch (Exception ignored) {
            // Elasticsearch is an acceleration index. Database chunks remain the source of truth.
        }
    }

    public void deleteByFileId(Long userId, Long fileId) {
        if (!enabled) return;
        CompletableFuture.runAsync(() -> deleteByFileIdNow(userId, fileId));
    }

    private void deleteByFileIdNow(Long userId, Long fileId) {
        try {
            Map<String, Object> query = Map.of(
                    "query", Map.of("bool", Map.of("filter", List.of(
                            Map.of("term", Map.of("userId", userId)),
                            Map.of("term", Map.of("fileId", fileId))
                    )))
            );
            request("POST", "/" + indexName + "/_delete_by_query?conflicts=proceed", mapper.writeValueAsString(query));
        } catch (Exception ignored) {
        }
    }

    public List<Long> hybridSearch(Long userId, List<Long> folderIds, String question, AiSettingsResponse settings) {
        if (!enabled || folderIds.isEmpty() || isTemporarilyUnavailable()) return List.of();
        try {
            ensureIndex(settings.embeddingDimensions());
        } catch (Exception ex) {
            markTemporarilyUnavailable(ex);
            return List.of();
        }
        CompletableFuture<List<Long>> keywordFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return keywordSearch(userId, folderIds, question);
            } catch (Exception ex) {
                markTemporarilyUnavailable(ex);
                return List.of();
            }
        });
        CompletableFuture<List<Long>> vectorFuture = CompletableFuture.supplyAsync(() -> {
            try {
                List<Float> queryEmbedding = embeddingService.embed(question, settings);
                return queryEmbedding.isEmpty()
                        ? List.of()
                        : vectorSearch(userId, folderIds, queryEmbedding);
            } catch (Exception ex) {
                markTemporarilyUnavailable(ex);
                return List.of();
            }
        });
        List<Long> keywordIds = keywordFuture.join();
        List<Long> vectorIds = vectorFuture.join();
        return reciprocalRankFusion(keywordIds, vectorIds);
    }

    private List<Long> keywordSearch(Long userId, List<Long> folderIds, String question) throws Exception {
        Map<String, Object> body = Map.of(
                "size", CANDIDATE_SIZE,
                "_source", List.of("chunkId"),
                "query", Map.of("bool", Map.of(
                        "filter", filters(userId, folderIds),
                        "must", Map.of("multi_match", Map.of(
                                "query", question == null ? "" : question,
                                "fields", List.of("content^3", "fileName"),
                                "type", "best_fields"
                        ))
                ))
        );
        return hitChunkIds(request("POST", "/" + indexName + "/_search", mapper.writeValueAsString(body)));
    }

    private List<Long> vectorSearch(Long userId, List<Long> folderIds, List<Float> queryEmbedding) throws Exception {
        Map<String, Object> body = Map.of(
                "size", CANDIDATE_SIZE,
                "_source", List.of("chunkId"),
                "knn", Map.of(
                        "field", "embedding",
                        "query_vector", queryEmbedding,
                        "k", CANDIDATE_SIZE,
                        "num_candidates", 100,
                        "filter", filters(userId, folderIds)
                )
        );
        return hitChunkIds(request("POST", "/" + indexName + "/_search", mapper.writeValueAsString(body)));
    }

    private List<Map<String, Object>> filters(Long userId, List<Long> folderIds) {
        return List.of(
                Map.of("term", Map.of("userId", userId)),
                Map.of("terms", Map.of("folderId", folderIds))
        );
    }

    private List<Long> reciprocalRankFusion(List<Long> keywordIds, List<Long> vectorIds) {
        Map<Long, Double> scores = new HashMap<>();
        addRrfScores(scores, keywordIds);
        addRrfScores(scores, vectorIds);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .limit(FUSED_RESULT_SIZE)
                .toList();
    }

    private void addRrfScores(Map<Long, Double> scores, List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            scores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
        }
    }

    private List<Long> hitChunkIds(String responseBody) throws Exception {
        List<Long> ids = new ArrayList<>();
        JsonNode hits = mapper.readTree(responseBody).at("/hits/hits");
        if (!hits.isArray()) return ids;
        for (JsonNode hit : hits) {
            long chunkId = hit.at("/_source/chunkId").asLong(0);
            if (chunkId > 0) {
                ids.add(chunkId);
            }
        }
        return ids;
    }

    private void ensureIndex(Integer dimensions) throws Exception {
        if (indexChecked) return;
        int dims = dimensions == null || dimensions <= 0 ? AiSettingsService.DEFAULT_EMBEDDING_DIMENSIONS : dimensions;
        Map<String, Object> body = Map.of(
                "mappings", Map.of("properties", Map.of(
                        "chunkId", Map.of("type", "long"),
                        "fileId", Map.of("type", "long"),
                        "folderId", Map.of("type", "long"),
                        "userId", Map.of("type", "long"),
                        "fileName", Map.of("type", "keyword"),
                        "chunkIndex", Map.of("type", "integer"),
                        "uploadedAt", Map.of("type", "date"),
                        "content", Map.of("type", "text"),
                        "embedding", Map.of(
                                "type", "dense_vector",
                                "dims", dims,
                                "index", true,
                                "similarity", "cosine"
                        )
                ))
        );
        HttpResponse<String> response = requestRaw("PUT", "/" + indexName, mapper.writeValueAsString(body));
        if (response.statusCode() == 200 || response.statusCode() == 201
                || response.body().contains("resource_already_exists_exception")) {
            indexChecked = true;
        }
    }

    private String request(String method, String path, String body) throws Exception {
        HttpResponse<String> response = requestRaw(method, path, body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Elasticsearch request failed: " + response.statusCode());
        }
        return response.body();
    }

    private HttpResponse<String> requestRaw(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + encodePath(path)))
                .timeout(ES_REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private boolean isTemporarilyUnavailable() {
        return System.currentTimeMillis() < unavailableUntilMillis;
    }

    private void markTemporarilyUnavailable(Exception ex) {
        unavailableUntilMillis = System.currentTimeMillis() + FAILURE_BACKOFF_MILLIS;
        log.warn("Elasticsearch unavailable; skipping ES retrieval for {}ms: {}", FAILURE_BACKOFF_MILLIS, ex.toString());
    }

    private String encodePath(String path) {
        int queryIndex = path.indexOf('?');
        if (queryIndex < 0) return path;
        return path.substring(0, queryIndex) + "?" + URLEncoder.encode(path.substring(queryIndex + 1), StandardCharsets.UTF_8)
                .replace("%3D", "=")
                .replace("%26", "&");
    }

    private String trimRight(String value) {
        String result = value == null || value.isBlank() ? "http://localhost:9200" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private record IndexedFile(Long fileId, Long folderId, Long userId, String fileName, String uploadedAt) {
    }

    private record IndexedChunk(Long chunkId, int chunkIndex, String content) {
    }
}
