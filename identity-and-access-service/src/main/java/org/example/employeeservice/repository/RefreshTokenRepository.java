package org.example.employeeservice.repository;

import org.example.employeeservice.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Tìm bản ghi token theo mã băm SHA-256
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Thu hồi toàn bộ Refresh Tokens của một tài khoản (khi phát hiện Replay Attack hoặc bảo mật)
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now WHERE r.account.id = :accountId AND r.revoked = false")
    void revokeAllByAccountId(@Param("accountId") Integer accountId, @Param("now") LocalDateTime now);

    /**
     * Dọn dẹp các token đã hết hạn hoặc đã thu hồi quá thời gian quy định
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiryDate < :cutoffDate OR (r.revoked = true AND r.revokedAt < :cutoffDate)")
    void deleteExpiredTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
}
