package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
/**
 * [SEARCH:ENTITY_KNOWLEDGE_EVENT] 知识片段行为事件明细。
 *
 * <p>与片段上的累计计数互补，用于按时间窗口计算趋势、近期正确率和复习压力。</p>
 */
public class KnowledgeChunkEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long chunkId;

    @Column(nullable = false)
    private Long fileId;

    @Column(nullable = false)
    private Long folderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    // 区分引用、主观反馈、练习答对和练习答错等行为来源。
    private KnowledgeChunkEventType eventType;

    @Column
    // 引用事件没有正误含义，因此该字段允许为空。
    private Boolean correct;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Long getFolderId() {
        return folderId;
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }

    public KnowledgeChunkEventType getEventType() {
        return eventType;
    }

    public void setEventType(KnowledgeChunkEventType eventType) {
        this.eventType = eventType;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
