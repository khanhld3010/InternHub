package org.example.internservice.contract.repository;

import org.example.internservice.contract.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DynamicContractRepository extends JpaRepository<Contract, Long> {
    Optional<Contract> findByContractNumber(String contractNumber);
    Optional<Contract> findFirstByInternProfileIdOrderByCreatedAtDesc(Long internProfileId);
    List<Contract> findAllByInternshipProgramId(Long internshipProgramId);
    List<Contract> findAllByOrderByCreatedAtDesc();
    boolean existsByContractNumber(String contractNumber);
}
