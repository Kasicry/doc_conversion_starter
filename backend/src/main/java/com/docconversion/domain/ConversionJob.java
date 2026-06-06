package com.docconversion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversion_jobs")
public class ConversionJob {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_file_id", nullable = false)
    private StoredFile originalFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_file_id")
    private StoredFile resultFile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetFormat targetFormat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversionStatus status;

    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    protected ConversionJob() {
    }

    public ConversionJob(UUID id, StoredFile originalFile, TargetFormat targetFormat, Instant now) {
        this.id = id;
        this.originalFile = originalFile;
        this.targetFormat = targetFormat;
        this.status = ConversionStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public StoredFile getOriginalFile() {
        return originalFile;
    }

    public StoredFile getResultFile() {
        return resultFile;
    }

    public TargetFormat getTargetFormat() {
        return targetFormat;
    }

    public ConversionStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void markProcessing(Instant now) {
        this.status = ConversionStatus.PROCESSING;
        this.updatedAt = now;
    }

    public void markCompleted(StoredFile resultFile, Instant now) {
        this.status = ConversionStatus.COMPLETED;
        this.resultFile = resultFile;
        this.updatedAt = now;
        this.completedAt = now;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage, Instant now) {
        this.status = ConversionStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = now;
        this.completedAt = now;
    }
}
