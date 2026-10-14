package org.example.aiservice.repository;

import org.example.aiservice.entity.AiSystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiSystemConfigRepository extends JpaRepository<AiSystemConfig, Long> {
    Optional<AiSystemConfig> findFirstByOrderByIdAsc();
}
