package com.example.exam.service;

import com.example.exam.dto.ChatDtos.ChatRequest;
import com.example.exam.dto.ChatDtos.ChatResponse;
import com.example.exam.dto.ChatDtos.Source;
import com.example.exam.model.KnowledgeChunk;
import com.example.exam.model.QuestionMode;
import com.example.exam.repository.KnowledgeChunkRepository;
import com.example.exam.repository.StudyFolderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    private final KnowledgeChunkRepository chunkRepository;
    private final StudyFolderRepository folderRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ChatService(KnowledgeChunkRepository chunkRepository, StudyFolderRepository folderRepository) {
        this.chunkRepository = chunkRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional(readOnly = true)
    public ChatResponse ask(Long userId, ChatRequest request) {
        folderRepository.findByIdAndOwnerId(request.folderId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found or access denied"));
        List<KnowledgeChunk> chunks = retrieve(request.folderId(), request.question());
        List<Source> sources = chunks.stream()
                .limit(5)
                .map(chunk -> new Source(chunk.getFile().getId(), chunk.getFile().getOriginalName(), excerpt(chunk.getContent())))
                .toList();
        String prompt = buildPrompt(request.mode(), request.question(), chunks);
        String answer = callModel(request, prompt);
        if (answer == null || answer.isBlank()) {
            answer = localAnswer(request.mode(), chunks);
        }
        return new ChatResponse(answer, sources);
    }

    private List<KnowledgeChunk> retrieve(Long folderId, String question) {
        List<KnowledgeChunk> all = chunkRepository.findByFolderId(folderId);
        String[] terms = question.toLowerCase(Locale.ROOT).split("\\s+|,|\\.|;|:");
        return all.stream()
                .sorted(Comparator.comparingInt((KnowledgeChunk chunk) -> score(chunk.getContent(), terms)).reversed())
                .limit(8)
                .toList();
    }

    private int score(String content, String[] terms) {
        String lower = content.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (!term.isBlank() && lower.contains(term)) {
                score += Math.max(1, term.length());
            }
        }
        return score;
    }

    private String buildPrompt(QuestionMode mode, String question, List<KnowledgeChunk> chunks) {
        String context = String.join("\n---\n", chunks.stream().map(KnowledgeChunk::getContent).toList());
        if (mode == QuestionMode.TEACHER) {
            return """
                    You are a student in a postgraduate entrance exam review session, and the user is the teacher.
                    Based only on the knowledge base below, ask the user one progressive question, then keep probing after the user answers.

                    Knowledge base:
                    %s

                    User input:
                    %s
                    """.formatted(context, question);
        }
        return """
                You are a smart postgraduate entrance exam Q&A assistant.
                Answer only from the knowledge base below and mention traceable evidence.

                Knowledge base:
                %s

                User question:
                %s
                """.formatted(context, question);
    }

    private String callModel(ChatRequest request, String prompt) {
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            return null;
        }
        try {
            String endpoint = request.endpoint() == null || request.endpoint().isBlank()
                    ? "https://api.openai.com/v1/chat/completions"
                    : request.endpoint();
            Map<String, Object> payload = Map.of(
                    "model", request.model() == null || request.model().isBlank() ? "gpt-4o-mini" : request.model(),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.3
            );
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Authorization", "Bearer " + request.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = mapper.readTree(response.body());
                return root.at("/choices/0/message/content").asText();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String localAnswer(QuestionMode mode, List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) {
            return "The selected knowledge base has no content yet. Upload files, edit extracted text, and save them first.";
        }
        String first = excerpt(chunks.get(0).getContent());
        if (mode == QuestionMode.TEACHER) {
            return "Teacher mode: explain the core definition first, then describe how it is commonly tested.\n\nEvidence: " + first;
        }
        List<String> lines = new ArrayList<>();
        lines.add("Based on the selected knowledge base:");
        lines.add(first);
        lines.add("You can continue with: what are the common mistakes, or organize this as an exam-answer template.");
        return String.join("\n\n", lines);
    }

    private String excerpt(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() > 220 ? normalized.substring(0, 220) + "..." : normalized;
    }
}
