package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "mistake_question_chunk",
        uniqueConstraints = @UniqueConstraint(columnNames = {"mistake_id", "chunk_id"})
)
/**
 * [SEARCH:ENTITY_MISTAKE_CHUNK_LINK] 错题与知识片段的多对多关联实体。
 *
 * <p>除关联本身外，还记录手工选择或教师出题等来源，便于决定是否触发画像反馈。</p>
 */
public class MistakeQuestionChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "mistake_id")
    private MistakeQuestion mistake;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id")
    private KnowledgeChunk chunk;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    // 来源决定该关联是用户明确选择，还是由练题流程自动建立。
    private MistakeQuestionChunkSourceType sourceType = MistakeQuestionChunkSourceType.MANUAL;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public MistakeQuestion getMistake() {
        return mistake;
    }

    public void setMistake(MistakeQuestion mistake) {
        this.mistake = mistake;
    }

    public KnowledgeChunk getChunk() {
        return chunk;
    }

    public void setChunk(KnowledgeChunk chunk) {
        this.chunk = chunk;
    }

    public MistakeQuestionChunkSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(MistakeQuestionChunkSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
