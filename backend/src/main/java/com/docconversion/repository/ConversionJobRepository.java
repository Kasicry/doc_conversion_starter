package com.docconversion.repository;

import com.docconversion.domain.ConversionJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversionJobRepository extends JpaRepository<ConversionJob, UUID> {

    @EntityGraph(attributePaths = {"originalFile", "resultFile"})
    Optional<ConversionJob> findWithFilesById(UUID id);
}
