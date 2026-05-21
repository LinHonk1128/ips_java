package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
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
    private KnowledgeChunkEventType eventType;

    @Column
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
