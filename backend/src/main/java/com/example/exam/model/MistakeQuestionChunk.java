package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "mistake_question_chunk",
        uniqueConstraints = @UniqueConstraint(columnNames = {"mistake_id", "chunk_id"})
)
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
