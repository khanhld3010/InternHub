package org.example.aiservice.repository;

import org.example.aiservice.entity.AiPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, Long> {

    Optional<AiPromptTemplate> findByTemplateCodeAndIsActiveTrue(String templateCode);

    List<AiPromptTemplate> findByTemplateCodeOrderByVersionDesc(String templateCode);

    List<AiPromptTemplate> findByFeatureGroupOrderByTemplateCodeAscVersionDesc(String featureGroup);

    List<AiPromptTemplate> findByIsActiveTrueOrderByFeatureGroupAscTemplateCodeAsc();

    @Query("SELECT MAX(t.version) FROM AiPromptTemplate t WHERE t.templateCode = :templateCode")
    Integer findMaxVersionByTemplateCode(@Param("templateCode") String templateCode);

    @Modifying
    @Query("UPDATE AiPromptTemplate t SET t.isActive = false WHERE t.templateCode = :templateCode")
    void deactivateAllVersions(@Param("templateCode") String templateCode);
}
