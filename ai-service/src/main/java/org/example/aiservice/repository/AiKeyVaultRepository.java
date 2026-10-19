package org.example.aiservice.repository;

import org.example.aiservice.entity.AiKeyVaultEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiKeyVaultRepository extends JpaRepository<AiKeyVaultEntry, Long> {

    List<AiKeyVaultEntry> findByProviderAndIsDeletedFalseOrderByPriorityAscLastUsedAtAsc(String provider);

    List<AiKeyVaultEntry> findByIsDeletedFalseOrderByPriorityAscIdAsc();

    Optional<AiKeyVaultEntry> findByIdAndIsDeletedFalse(Long id);

    boolean existsByApiKeyHashAndIsDeletedFalse(String apiKeyHash);

    @Query("SELECT COUNT(k) FROM AiKeyVaultEntry k WHERE k.provider = :provider AND k.isDeleted = false AND k.status = 'ACTIVE'")
    long countActiveKeysByProvider(@Param("provider") String provider);
}
