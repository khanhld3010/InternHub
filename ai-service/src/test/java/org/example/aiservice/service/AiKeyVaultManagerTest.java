package org.example.aiservice.service;

import org.example.aiservice.entity.AiKeyVaultEntry;
import org.example.aiservice.repository.AiKeyVaultRepository;
import org.example.aiservice.security.AesGcmEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiKeyVaultManagerTest {

    @Mock
    private AiKeyVaultRepository vaultRepository;

    @Mock
    private AesGcmEncryptionUtil encryptionUtil;

    private AiKeyVaultManager vaultManager;

    @BeforeEach
    void setUp() {
        vaultManager = new AiKeyVaultManager(vaultRepository, encryptionUtil);
    }

    @Test
    @DisplayName("TDD 1: Chọn Key theo Round-Robin dựa trên lastUsedAt khi cùng priority")
    void shouldSelectKeyUsingRoundRobin_whenMultipleKeysHaveSamePriority() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        AiKeyVaultEntry key1 = AiKeyVaultEntry.builder()
                .id(1L)
                .keyName("Key 1")
                .provider("GEMINI")
                .apiKeyEncrypted("enc1")
                .priority(1)
                .status("ACTIVE")
                .isDeleted(false)
                .lastUsedAt(now.minusMinutes(10))
                .build();

        AiKeyVaultEntry key2 = AiKeyVaultEntry.builder()
                .id(2L)
                .keyName("Key 2")
                .provider("GEMINI")
                .apiKeyEncrypted("enc2")
                .priority(1)
                .status("ACTIVE")
                .isDeleted(false)
                .lastUsedAt(now.minusMinutes(5))
                .build();

        when(vaultRepository.findByProviderAndIsDeletedFalseOrderByPriorityAscLastUsedAtAsc("GEMINI"))
                .thenReturn(new ArrayList<>(List.of(key1, key2)));

        // Act
        AiKeyVaultEntry selected = vaultManager.acquireActiveKey("GEMINI");

        // Assert
        assertThat(selected).isNotNull();
        assertThat(selected.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("TDD 2: Tự động bỏ qua Key đang trong Cooldown 60s và chọn Key khả dụng tiếp theo")
    void shouldSkipCooldownKey_andSelectNextActiveKey() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        AiKeyVaultEntry keyCooldown = AiKeyVaultEntry.builder()
                .id(1L)
                .keyName("Key 1")
                .provider("GEMINI")
                .apiKeyEncrypted("enc1")
                .priority(1)
                .status("COOLDOWN")
                .cooldownUntil(now.plusSeconds(45))
                .isDeleted(false)
                .build();

        AiKeyVaultEntry keyActive = AiKeyVaultEntry.builder()
                .id(2L)
                .keyName("Key 2")
                .provider("GEMINI")
                .apiKeyEncrypted("enc2")
                .priority(1)
                .status("ACTIVE")
                .isDeleted(false)
                .build();

        when(vaultRepository.findByProviderAndIsDeletedFalseOrderByPriorityAscLastUsedAtAsc("GEMINI"))
                .thenReturn(new ArrayList<>(List.of(keyCooldown, keyActive)));

        // Act
        AiKeyVaultEntry selected = vaultManager.acquireActiveKey("GEMINI");

        // Assert
        assertThat(selected).isNotNull();
        assertThat(selected.getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("TDD 3: Tự động tái kích hoạt Key khi thời gian Cooldown đã hết")
    void shouldReactivateKey_whenCooldownHasExpired() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        AiKeyVaultEntry keyExpiredCooldown = AiKeyVaultEntry.builder()
                .id(1L)
                .keyName("Key 1")
                .provider("GEMINI")
                .apiKeyEncrypted("enc1")
                .priority(1)
                .status("COOLDOWN")
                .cooldownUntil(now.minusSeconds(10))
                .isDeleted(false)
                .build();

        when(vaultRepository.findByProviderAndIsDeletedFalseOrderByPriorityAscLastUsedAtAsc("GEMINI"))
                .thenReturn(new ArrayList<>(List.of(keyExpiredCooldown)));

        // Act
        AiKeyVaultEntry selected = vaultManager.acquireActiveKey("GEMINI");

        // Assert
        assertThat(selected).isNotNull();
        assertThat(selected.getStatus()).isEqualTo("ACTIVE");
        assertThat(selected.getCooldownUntil()).isNull();
        verify(vaultRepository).save(keyExpiredCooldown);
    }

    @Test
    @DisplayName("TDD 4: Đánh dấu COOLDOWN 60s khi báo cáo lỗi HTTP 429")
    void shouldSetCooldown60Seconds_whenReportingHttp429() {
        // Arrange
        AiKeyVaultEntry key = AiKeyVaultEntry.builder()
                .id(1L)
                .keyName("Key 1")
                .status("ACTIVE")
                .failureCount(0)
                .build();

        when(vaultRepository.findById(1L)).thenReturn(java.util.Optional.of(key));

        // Act
        vaultManager.reportExecutionResult(1L, 429);

        // Assert
        assertThat(key.getStatus()).isEqualTo("COOLDOWN");
        assertThat(key.getCooldownUntil()).isNotNull();
        assertThat(key.getCooldownUntil()).isAfter(LocalDateTime.now().plusSeconds(50));
        assertThat(key.getFailureCount()).isEqualTo(1);
        verify(vaultRepository).save(key);
    }

    @Test
    @DisplayName("TDD 5: Vô hiệu hóa DISABLED vĩnh viễn khi gặp lỗi HTTP 401 hoặc 403")
    void shouldDisableKey_whenReportingHttp401Or403() {
        // Arrange
        AiKeyVaultEntry key = AiKeyVaultEntry.builder()
                .id(1L)
                .keyName("Key 1")
                .status("ACTIVE")
                .build();

        when(vaultRepository.findById(1L)).thenReturn(java.util.Optional.of(key));

        // Act
        vaultManager.reportExecutionResult(1L, 401);

        // Assert
        assertThat(key.getStatus()).isEqualTo("DISABLED");
        verify(vaultRepository).save(key);
    }
}
