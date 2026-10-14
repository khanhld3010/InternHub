package org.example.internservice.contract.repository;

import org.example.internservice.contract.entity.ContractAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractAuditLogRepository extends JpaRepository<ContractAuditLog, Long> {
    List<ContractAuditLog> findAllByContractIdOrderByTimestampDesc(Long contractId);
    List<ContractAuditLog> findAllByContractRevisionIdOrderByTimestampDesc(Long contractRevisionId);
}
