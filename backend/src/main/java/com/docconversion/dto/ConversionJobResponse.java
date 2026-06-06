package com.docconversion.dto;

import com.docconversion.domain.ConversionJob;

public record ConversionJobResponse(
    String jobId,
    String status,
    String targetFormat,
    String originalFileName,
    String resultFileName,
    String errorMessage
) {
    public static ConversionJobResponse from(ConversionJob job) {
        return new ConversionJobResponse(
            job.getId().toString(),
            job.getStatus().name(),
            job.getTargetFormat().name(),
            job.getOriginalFile().getOriginalName(),
            job.getResultFile() == null ? null : job.getResultFile().getOriginalName(),
            job.getErrorMessage()
        );
    }
}
