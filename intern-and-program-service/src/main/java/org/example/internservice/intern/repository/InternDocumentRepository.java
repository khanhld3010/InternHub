package org.example.internservice.intern.repository;

import org.example.internservice.intern.entity.InternDocument;
import org.example.internservice.intern.entity.enums.DocumentType;
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

    boolean existsByInternProfileIdAndDocumentType(Long internId, DocumentType documentType);

    @org.springframework.data.jpa.repository.Query("SELECT d.internProfile.id FROM InternDocument d WHERE d.internProfile.id IN :internIds AND d.documentType = :documentType")
    java.util.Set<Long> findInternProfileIdsWithDocumentType(
            @org.springframework.data.repository.query.Param("internIds") java.util.Collection<Long> internIds,
            @org.springframework.data.repository.query.Param("documentType") DocumentType documentType
    );
}
