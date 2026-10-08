package org.example.internservice.contract.repository;

import org.example.internservice.contract.entity.ContractSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContractSignatureRepository extends JpaRepository<ContractSignature, Long> {
    Optional<ContractSignature> findByContractRevisionId(Long contractRevisionId);
    boolean existsByContractRevisionId(Long contractRevisionId);
}
