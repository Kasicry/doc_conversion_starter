package com.docconversion.service;

import com.docconversion.domain.ConversionJob;
import com.docconversion.domain.StoredFile;
import com.docconversion.repository.ConversionJobRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversionJobProcessor {

    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ConversionJobRepository conversionJobRepository;
    private final FileStorageService fileStorageService;
    private final PdfToDocxConverter pdfToDocxConverter;

    public ConversionJobProcessor(ConversionJobRepository conversionJobRepository, FileStorageService fileStorageService, PdfToDocxConverter pdfToDocxConverter) {
        this.conversionJobRepository = conversionJobRepository;
        this.fileStorageService = fileStorageService;
        this.pdfToDocxConverter = pdfToDocxConverter;
    }

    @Async
    @Transactional
    public void processAsync(UUID jobId) {
        ConversionJob job = conversionJobRepository.findWithFilesById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("변환 작업을 찾을 수 없습니다."));

        try {
            job.markProcessing(Instant.now());
            conversionJobRepository.saveAndFlush(job);

            byte[] result = pdfToDocxConverter.convert(fileStorageService.pathOf(job.getOriginalFile()));
            String resultName = resultName(job.getOriginalFile().getOriginalName());
            StoredFile resultFile = fileStorageService.saveResult(result, resultName, DOCX_CONTENT_TYPE);

            job.markCompleted(resultFile, Instant.now());
            conversionJobRepository.save(job);
        } catch (RuntimeException exception) {
            job.markFailed(exception.getMessage(), Instant.now());
            conversionJobRepository.save(job);
        }
    }

    private String resultName(String originalName) {
        int dotIndex = originalName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? originalName.substring(0, dotIndex) : originalName;
        return baseName + ".docx";
    }
}
