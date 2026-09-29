package org.example.employeeservice.repository;

import org.example.employeeservice.entity.AccountActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountActivationTokenRepository extends JpaRepository<AccountActivationToken, Long> {

    Optional<AccountActivationToken> findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(Integer accountId);

    Optional<AccountActivationToken> findFirstByAccountIdAndActivationKeyAndConsumedAtIsNull(Integer accountId, String activationKey);

    List<AccountActivationToken> findByAccountIdAndConsumedAtIsNull(Integer accountId);
}
