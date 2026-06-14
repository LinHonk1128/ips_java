package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
/**
 * [SEARCH:ENTITY_STUDY_FILE] 用户上传或创建的一份学习资料。
 *
 * <p>保存原文件元数据和可编辑的抽取正文；开启知识库后，正文会派生为多个知识片段。</p>
 */
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
    // PDF、Word、OCR 或编辑器产生的统一正文，是重新切片时的输入。
    private String extractedText;

    @Column(nullable = false, columnDefinition = "boolean default true")
    // 关闭后文件仍保留，但对应知识片段和检索索引会被移除。
    private boolean knowledgeEnabled = true;

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

    public boolean isKnowledgeEnabled() {
        return knowledgeEnabled;
    }

    public void setKnowledgeEnabled(boolean knowledgeEnabled) {
        this.knowledgeEnabled = knowledgeEnabled;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
