package org.example.internservice.contract.repository;

import org.example.internservice.contract.entity.ContractRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRevisionRepository extends JpaRepository<ContractRevision, Long> {
    Optional<ContractRevision> findByContractIdAndRevisionNumber(Long contractId, Integer revisionNumber);
    List<ContractRevision> findAllByContractIdOrderByRevisionNumberDesc(Long contractId);
}
