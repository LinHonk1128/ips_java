package com.example.exam.service;

import com.example.exam.dto.ChatDtos.ChunkFeedbackResponse;
import com.example.exam.dto.ChatDtos.ChunkFeedbackType;
import com.example.exam.dto.ChatDtos.Source;
import com.example.exam.model.KnowledgeChunk;
import com.example.exam.model.KnowledgeChunkEvent;
import com.example.exam.model.KnowledgeChunkEventType;
import com.example.exam.repository.KnowledgeChunkEventRepository;
import com.example.exam.repository.KnowledgeChunkRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * [SEARCH:CHUNK_INTERACTION] 知识片段行为回写服务。
 *
 * <p>集中处理引用、答题反馈和错题练习事件，确保片段累计指标与事件明细同步更新。</p>
 */
public class KnowledgeChunkInteractionService {
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeChunkEventRepository eventRepository;

    public KnowledgeChunkInteractionService(KnowledgeChunkRepository chunkRepository,
                                            KnowledgeChunkEventRepository eventRepository) {
        this.chunkRepository = chunkRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    // [SEARCH:CHUNK_CITATION] 记录答案实际展示的引用来源，并更新片段最近访问时间。
    public List<Source> recordCitations(Long userId, List<Source> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        return sources.stream()
                .map(source -> {
                    KnowledgeChunk chunk = requireOwnedChunk(source.chunkId(), userId);
                    chunk.setCiteCount(chunk.getCiteCount() + 1);
                    chunk.setLastAccessedAt(now);
                    recordEvent(chunk, KnowledgeChunkEventType.CITED, null, now);
                    return toSource(source.citationIndex(), chunk, source.excerpt());
                })
                .toList();
    }

    @Transactional
    // [SEARCH:CHUNK_FEEDBACK] 将“很清晰/忘记了”反馈转换为片段掌握指标和事件。
    public ChunkFeedbackResponse recordFeedback(Long userId, Long chunkId, ChunkFeedbackType type) {
        KnowledgeChunk chunk = requireOwnedChunk(chunkId, userId);
        Instant now = Instant.now();
        if (type == ChunkFeedbackType.CLEAR) {
            chunk.setCorrectHitCount(chunk.getCorrectHitCount() + 1);
            recordEvent(chunk, KnowledgeChunkEventType.FEEDBACK_CLEAR, true, now);
        } else if (type == ChunkFeedbackType.FORGOT) {
            chunk.setWrongHitCount(chunk.getWrongHitCount() + 1);
            recordEvent(chunk, KnowledgeChunkEventType.FEEDBACK_FORGOT, false, now);
        } else {
            throw new IllegalArgumentException("Unsupported feedback type");
        }
        touchPractice(chunk, now);
        return toFeedbackResponse(chunk);
    }

    @Transactional
    // [SEARCH:CHUNK_PRACTICE_RESULT] 把一次练习结果批量回写到去重后的关联片段。
    public int recordPracticeResult(Long userId, Collection<Long> chunkIds, boolean correct) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return 0;
        }
        int updated = 0;
        Instant now = Instant.now();
        for (Long chunkId : chunkIds.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            KnowledgeChunk chunk = requireOwnedChunk(chunkId, userId);
            if (correct) {
                chunk.setCorrectHitCount(chunk.getCorrectHitCount() + 1);
            } else {
                chunk.setWrongHitCount(chunk.getWrongHitCount() + 1);
            }
            recordEvent(chunk, correct ? KnowledgeChunkEventType.PRACTICE_CORRECT : KnowledgeChunkEventType.PRACTICE_WRONG, correct, now);
            touchPractice(chunk, now);
            updated++;
        }
        return updated;
    }

    @Transactional
    public void recordCitation(KnowledgeChunk chunk) {
        chunk.setCiteCount(chunk.getCiteCount() + 1);
        Instant now = Instant.now();
        chunk.setLastAccessedAt(now);
        recordEvent(chunk, KnowledgeChunkEventType.CITED, null, now);
    }

    public KnowledgeChunk requireOwnedChunk(Long chunkId, Long userId) {
        if (chunkId == null) {
            throw new IllegalArgumentException("Knowledge chunk is required");
        }
        KnowledgeChunk chunk = chunkRepository.findById(chunkId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge chunk not found"));
        Long ownerId = chunk.getFolder().getOwner().getId();
        if (!ownerId.equals(userId)) {
            throw new IllegalArgumentException("Knowledge chunk not found or access denied");
        }
        return chunk;
    }

    public Source toSource(Integer citationIndex, KnowledgeChunk chunk, String excerpt) {
        return new Source(
                citationIndex,
                chunk.getId(),
                chunk.getFile().getId(),
                chunk.getFolder().getId(),
                chunk.getFile().getOriginalName(),
                chunk.getPageNumber(),
                excerpt,
                chunk.getCiteCount(),
                chunk.getCorrectHitCount(),
                chunk.getWrongHitCount(),
                chunk.getMasteryRate(),
                chunk.getLastAccessedAt(),
                chunk.getLastPracticedAt()
        );
    }

    public ChunkFeedbackResponse toFeedbackResponse(KnowledgeChunk chunk) {
        return new ChunkFeedbackResponse(
                chunk.getId(),
                chunk.getCiteCount(),
                chunk.getCorrectHitCount(),
                chunk.getWrongHitCount(),
                chunk.getMasteryRate(),
                chunk.getLastAccessedAt(),
                chunk.getLastPracticedAt()
        );
    }

    private void touchPractice(KnowledgeChunk chunk, Instant now) {
        chunk.setLastPracticedAt(now);
        chunk.setLastAccessedAt(now);
    }

    private void recordEvent(KnowledgeChunk chunk, KnowledgeChunkEventType type, Boolean correct, Instant createdAt) {
        KnowledgeChunkEvent event = new KnowledgeChunkEvent();
        event.setOwnerId(chunk.getFolder().getOwner().getId());
        event.setChunkId(chunk.getId());
        event.setFileId(chunk.getFile().getId());
        event.setFolderId(chunk.getFolder().getId());
        event.setEventType(type);
        event.setCorrect(correct);
        event.setCreatedAt(createdAt);
        eventRepository.save(event);
    }
}
