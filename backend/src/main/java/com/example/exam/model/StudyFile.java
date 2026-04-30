package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class StudyFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private StudyFolder folder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FileTag tag = FileTag.OTHER;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String storedPath;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public StudyFolder getFolder() {
        return folder;
    }

    public void setFolder(StudyFolder folder) {
        this.folder = folder;
    }

    public FileTag getTag() {
        return tag;
    }

    public void setTag(FileTag tag) {
        this.tag = tag;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
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

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
