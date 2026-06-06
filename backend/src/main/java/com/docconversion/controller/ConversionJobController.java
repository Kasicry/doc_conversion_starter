package com.docconversion.controller;

import com.docconversion.domain.ConversionJob;
import com.docconversion.domain.ConversionStatus;
import com.docconversion.domain.StoredFile;
import com.docconversion.domain.TargetFormat;
import com.docconversion.dto.ConversionJobResponse;
import com.docconversion.service.ConversionJobService;
import com.docconversion.service.FileStorageService;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ConversionJobController {

    private final ConversionJobService conversionJobService;
    private final FileStorageService fileStorageService;

    public ConversionJobController(ConversionJobService conversionJobService, FileStorageService fileStorageService) {
        this.conversionJobService = conversionJobService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(value = "/api/conversion-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ConversionJobResponse create(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "targetFormat", defaultValue = "DOCX") TargetFormat targetFormat
    ) {
        return ConversionJobResponse.from(conversionJobService.create(file, targetFormat));
    }

    @GetMapping("/api/conversion-jobs/{jobId}")
    public ConversionJobResponse get(@PathVariable UUID jobId) {
        return ConversionJobResponse.from(conversionJobService.get(jobId));
    }

    @GetMapping("/api/conversion-jobs/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID jobId) {
        ConversionJob job = conversionJobService.get(jobId);
        if (job.getStatus() != ConversionStatus.COMPLETED || job.getResultFile() == null) {
            return ResponseEntity.notFound().build();
        }

        StoredFile resultFile = job.getResultFile();
        Resource resource = fileStorageService.loadAsResource(resultFile);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(resultFile.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(resultFile.getOriginalName())
                .build()
                .toString())
            .body(resource);
    }
}
