package com.docconversion.service;

import com.docconversion.config.StorageProperties;
import com.docconversion.domain.StoredFile;
import com.docconversion.repository.StoredFileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final Path rootPath;
    private final StoredFileRepository storedFileRepository;

    public FileStorageService(StorageProperties properties, StoredFileRepository storedFileRepository) {
        this.rootPath = Path.of(properties.getRoot()).toAbsolutePath().normalize();
        this.storedFileRepository = storedFileRepository;
    }

    public StoredFile saveUpload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("업로드 파일이 비어 있습니다.");
        }

        String originalName = sanitize(file.getOriginalFilename());
        if (!originalName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("PDF 파일만 업로드할 수 있습니다.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            return save(inputStream, originalName, contentType(file.getContentType()), file.getSize(), "uploads");
        } catch (IOException exception) {
            throw new IllegalStateException("업로드 파일 저장에 실패했습니다.", exception);
        }
    }

    public StoredFile saveResult(byte[] content, String originalName, String contentType) {
        return save(new ByteArrayInputStream(content), originalName, contentType, content.length, "results");
    }

    public Resource loadAsResource(StoredFile storedFile) {
        try {
            Path path = Path.of(storedFile.getStoragePath()).toAbsolutePath().normalize();
            Resource resource = new UrlResource(path.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new IllegalStateException("파일을 읽을 수 없습니다.");
        } catch (IOException exception) {
            throw new IllegalStateException("파일 로드에 실패했습니다.", exception);
        }
    }

    public Path pathOf(StoredFile storedFile) {
        return Path.of(storedFile.getStoragePath()).toAbsolutePath().normalize();
    }

    private StoredFile save(InputStream inputStream, String originalName, String contentType, long size, String directory) {
        try {
            Files.createDirectories(rootPath.resolve(directory));
            UUID id = UUID.randomUUID();
            String storedName = id + "-" + originalName;
            Path target = rootPath.resolve(directory).resolve(storedName).normalize();
            try (InputStream source = inputStream) {
                Files.copy(source, target);
            }

            Instant now = Instant.now();
            StoredFile storedFile = new StoredFile(
                id,
                originalName,
                storedName,
                target.toString(),
                contentType,
                size,
                now.plus(Duration.ofHours(24)),
                now
            );
            return storedFileRepository.save(storedFile);
        } catch (IOException exception) {
            throw new IllegalStateException("파일 저장에 실패했습니다.", exception);
        }
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document.pdf";
        }
        return Path.of(filename).getFileName().toString().replaceAll("[^a-zA-Z0-9가-힣._-]", "_");
    }

    private String contentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }
}
