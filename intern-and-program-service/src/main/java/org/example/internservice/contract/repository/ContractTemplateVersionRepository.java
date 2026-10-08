package org.example.internservice.contract.repository;

import org.example.internservice.contract.entity.ContractTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContractTemplateVersionRepository extends JpaRepository<ContractTemplateVersion, Long> {
    Optional<ContractTemplateVersion> findByContractTemplateIdAndVersionNumber(Long templateId, Integer versionNumber);
}
