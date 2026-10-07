package org.example.internservice.program.repository;

import org.example.internservice.program.entity.InternGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InternGroupRepository extends JpaRepository<InternGroup, Long> {

    List<InternGroup> findByProgramId(Long programId);

    Optional<InternGroup> findByIdAndProgramId(Long id, Long programId);

    boolean existsByProgramIdAndName(Long programId, String name);
}
