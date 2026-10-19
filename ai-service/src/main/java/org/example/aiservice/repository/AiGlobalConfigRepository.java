package org.example.aiservice.repository;

import org.example.aiservice.entity.AiGlobalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiGlobalConfigRepository extends JpaRepository<AiGlobalConfig, Long> {

    Optional<AiGlobalConfig> findFirstByOrderByIdAsc();
}
