package com.docconversion.service;

import com.docconversion.domain.ConversionJob;
import com.docconversion.domain.StoredFile;
import com.docconversion.domain.TargetFormat;
import com.docconversion.repository.ConversionJobRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ConversionJobService {

    private final FileStorageService fileStorageService;
    private final ConversionJobRepository conversionJobRepository;
    private final ConversionJobProcessor conversionJobProcessor;

    public ConversionJobService(FileStorageService fileStorageService, ConversionJobRepository conversionJobRepository, ConversionJobProcessor conversionJobProcessor) {
        this.fileStorageService = fileStorageService;
        this.conversionJobRepository = conversionJobRepository;
        this.conversionJobProcessor = conversionJobProcessor;
    }

    @Transactional
    public ConversionJob create(MultipartFile file, TargetFormat targetFormat) {
        if (targetFormat != TargetFormat.DOCX) {
            throw new IllegalArgumentException("현재 MVP에서는 DOCX 변환만 지원합니다.");
        }

        StoredFile storedFile = fileStorageService.saveUpload(file);
        ConversionJob job = new ConversionJob(UUID.randomUUID(), storedFile, targetFormat, Instant.now());
        ConversionJob savedJob = conversionJobRepository.save(job);
        enqueueAfterCommit(savedJob.getId());
        return savedJob;
    }

    @Transactional(readOnly = true)
    public ConversionJob get(UUID jobId) {
        return conversionJobRepository.findWithFilesById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("변환 작업을 찾을 수 없습니다."));
    }

    private void enqueueAfterCommit(UUID jobId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                conversionJobProcessor.processAsync(jobId);
            }
        });
    }
}
