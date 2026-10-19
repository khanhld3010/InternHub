package org.example.aiservice.repository;

import org.example.aiservice.entity.AiFeatureProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiFeatureProfileRepository extends JpaRepository<AiFeatureProfile, Long> {

    Optional<AiFeatureProfile> findByFeatureKey(String featureKey);

    boolean existsByFeatureKey(String featureKey);
}
