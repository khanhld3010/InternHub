package org.example.internservice.intern.repository;

import org.example.internservice.intern.entity.InternContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InternContractRepository extends JpaRepository<InternContract, Long> {

    boolean existsByContractNumber(String contractNumber);

    Optional<InternContract> findByContractNumber(String contractNumber);

    @Query("SELECT c FROM InternContract c JOIN FETCH c.internProfile WHERE c.internProfile.internCode = :internCode ORDER BY c.createdAt DESC")
    List<InternContract> findByInternCodeWithProfile(@Param("internCode") String internCode);

    @Query("SELECT c FROM InternContract c JOIN FETCH c.internProfile WHERE c.id = :id")
    Optional<InternContract> findByIdWithProfile(@Param("id") Long id);

    @Query("SELECT c.contractNumber FROM InternContract c WHERE c.contractNumber LIKE :prefixPattern ORDER BY c.contractNumber DESC")
    List<String> findContractNumbersByPrefix(@Param("prefixPattern") String prefixPattern);
}
