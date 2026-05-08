package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class MistakeAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private MistakeQuestion mistake;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MistakeAttachmentType type;

    @Column(nullable = false, length = 180)
    private String originalName;

    @Column(nullable = false, length = 180)
    private String displayName;

    @Column(nullable = false)
    private String storedPath;

    @Column(nullable = false, length = 120)
    private String contentType;

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

    public MistakeAttachmentType getType() {
        return type;
    }

    public void setType(MistakeAttachmentType type) {
        this.type = type;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
