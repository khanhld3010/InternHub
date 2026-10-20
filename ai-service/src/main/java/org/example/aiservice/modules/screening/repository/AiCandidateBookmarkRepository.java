package org.example.aiservice.modules.screening.repository;

import org.example.aiservice.modules.screening.entity.AiCandidateBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiCandidateBookmarkRepository extends JpaRepository<AiCandidateBookmark, Long> {

    List<AiCandidateBookmark> findByProgramId(Long programId);

    Optional<AiCandidateBookmark> findByProgramIdAndInternId(Long programId, Long internId);

    boolean existsByProgramIdAndInternId(Long programId, Long internId);

    void deleteByProgramIdAndInternId(Long programId, Long internId);
}
