package org.example.employeeservice.intern.repository;

import org.example.employeeservice.intern.entity.InternDocument;
import org.example.employeeservice.intern.entity.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InternDocumentRepository extends JpaRepository<InternDocument, Long> {

    List<InternDocument> findByInternProfileIdOrderByCreatedAtDesc(Long internId);

    List<InternDocument> findByInternProfileInternCodeOrderByCreatedAtDesc(String internCode);

    Optional<InternDocument> findTopByInternProfileInternCodeAndDocumentTypeOrderByCreatedAtDesc(
            String internCode, DocumentType documentType
    );
}
