package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
/**
 * [SEARCH:ENTITY_USER] 系统账号实体，是资料、画像、错题和计划的所有权根节点。
 */
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false)
    // 仅保存密码哈希，认证过程不持久化明文密码。
    private String passwordHash;

    @Column(length = 128)
    private String displayName;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
