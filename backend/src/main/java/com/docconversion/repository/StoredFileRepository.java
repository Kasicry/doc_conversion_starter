package com.docconversion.repository;

import com.docconversion.domain.StoredFile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
}
