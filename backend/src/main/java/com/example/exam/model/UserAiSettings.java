package com.example.exam.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_ai_settings")
/**
 * [SEARCH:ENTITY_AI_SETTINGS] 用户私有的模型调用配置。
 *
 * <p>聊天模型与向量模型可以使用不同端点和密钥；预设以 JSON 保存以支持快速切换配置。</p>
 */
public class UserAiSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 80)
    private String aiRole;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(length = 120)
    private String chatModel;

    @Column(length = 500)
    private String chatEndpoint;

    @Column(length = 1000)
    private String chatApiKey;

    @Column(length = 120)
    private String embeddingModel;

    @Column(length = 500)
    private String embeddingEndpoint;

    @Column(length = 1000)
    private String embeddingApiKey;

    @Column(nullable = false)
    // 必须与外部向量模型和 Elasticsearch 索引映射保持一致。
    private int embeddingDimensions = 1536;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String presetsJson;

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getAiRole() {
        return aiRole;
    }

    public void setAiRole(String aiRole) {
        this.aiRole = aiRole;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getChatEndpoint() {
        return chatEndpoint;
    }

    public void setChatEndpoint(String chatEndpoint) {
        this.chatEndpoint = chatEndpoint;
    }

    public String getChatApiKey() {
        return chatApiKey;
    }

    public void setChatApiKey(String chatApiKey) {
        this.chatApiKey = chatApiKey;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getEmbeddingEndpoint() {
        return embeddingEndpoint;
    }

    public void setEmbeddingEndpoint(String embeddingEndpoint) {
        this.embeddingEndpoint = embeddingEndpoint;
    }

    public String getEmbeddingApiKey() {
        return embeddingApiKey;
    }

    public void setEmbeddingApiKey(String embeddingApiKey) {
        this.embeddingApiKey = embeddingApiKey;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public String getPresetsJson() {
        return presetsJson;
    }

    public void setPresetsJson(String presetsJson) {
        this.presetsJson = presetsJson;
    }
}
