package com.docconversion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_files")
public class StoredFile {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String storedName;

    @Column(nullable = false)
    private String storagePath;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean deleted;

    @Column(nullable = false)
    private Instant createdAt;

    protected StoredFile() {
    }

    public StoredFile(UUID id, String originalName, String storedName, String storagePath, String contentType, long sizeBytes, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.originalName = originalName;
        this.storedName = storedName;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.deleted = false;
    }

    public UUID getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
