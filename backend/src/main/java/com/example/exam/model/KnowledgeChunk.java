package com.example.exam.model;

import jakarta.persistence.*;

@Entity
public class KnowledgeChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private StudyFile file;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private StudyFolder folder;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "integer default 1")
    private int pageNumber = 1;

    @Column(nullable = false, columnDefinition = "integer default 1")
    private int chunkingVersion = 1;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int correctHitCount = 0;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int wrongHitCount = 0;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public Long getId() {
        return id;
    }

    public StudyFile getFile() {
        return file;
    }

    public void setFile(StudyFile file) {
        this.file = file;
    }

    public StudyFolder getFolder() {
        return folder;
    }

    public void setFolder(StudyFolder folder) {
        this.folder = folder;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getChunkingVersion() {
        return chunkingVersion;
    }

    public void setChunkingVersion(int chunkingVersion) {
        this.chunkingVersion = chunkingVersion;
    }

    public int getCorrectHitCount() {
        return correctHitCount;
    }

    public void setCorrectHitCount(int correctHitCount) {
        this.correctHitCount = correctHitCount;
    }

    public int getWrongHitCount() {
        return wrongHitCount;
    }

    public void setWrongHitCount(int wrongHitCount) {
        this.wrongHitCount = wrongHitCount;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
