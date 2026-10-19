package org.example.aiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.aiservice.client.GeminiApiClient;
import org.example.aiservice.dto.request.TestProfileRequest;
import org.example.aiservice.dto.request.UpdateFeatureProfileRequest;
import org.example.aiservice.dto.request.UpdateGlobalKeyRequest;
import org.example.aiservice.dto.response.AiMultiConfigResponse;
import org.example.aiservice.dto.response.TestProfileResponse;
import org.example.aiservice.entity.AiFeatureProfile;
import org.example.aiservice.entity.AiGlobalConfig;
import org.example.aiservice.repository.AiFeatureProfileRepository;
import org.example.aiservice.repository.AiGlobalConfigRepository;
import org.example.aiservice.security.AesGcmEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMultiConfigServiceTest {

    @Mock
    private AiGlobalConfigRepository globalConfigRepository;

    @Mock
    private AiFeatureProfileRepository profileRepository;

    @Mock
    private AesGcmEncryptionUtil encryptionUtil;

    @Mock
    private GeminiApiClient geminiApiClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AiMultiConfigService multiConfigService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(multiConfigService, "currentKeyVersion", 1);
    }

    @Test
    @DisplayName("getMultiConfig - Trả về đủ Global Config và danh sách 4 Profiles mặc định")
    void testGetMultiConfig_Success() {
        AiGlobalConfig global = AiGlobalConfig.builder()
                .id(1L)
                .provider("GEMINI")
                .apiKeyEncrypted("encrypted_val")
                .apiKeyLast4("1234")
                .keyVersion(1)
                .isActive(true)
                .build();
        when(globalConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(global));

        AiFeatureProfile profile = AiFeatureProfile.builder()
                .id(1L)
                .featureKey("REPORT_ASSISTANT")
                .featureName("Trợ Lý Đánh Giá Báo Cáo Tuần")
                .modelName("gemini-3.5-flash-lite")
                .temperature(new BigDecimal("0.10"))
                .maxTokens(2048)
                .isActive(true)
                .build();
        when(profileRepository.findAll()).thenReturn(List.of(profile));
        when(profileRepository.findByFeatureKey(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return Optional.of(AiFeatureProfile.builder()
                    .featureKey(key)
                    .featureName("Preset " + key)
                    .modelName("gemini-3.5-flash-lite")
                    .temperature(new BigDecimal("0.20"))
                    .maxTokens(1024)
                    .isActive(true)
                    .build());
        });

        AiMultiConfigResponse response = multiConfigService.getMultiConfig();

        assertThat(response).isNotNull();
        assertThat(response.getGlobalConfig().isHasApiKey()).isTrue();
        assertThat(response.getGlobalConfig().getMaskedApiKey()).isEqualTo("••••••••1234");
        assertThat(response.getProfiles()).isNotEmpty();
        assertThat(response.getProfiles().size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("updateGlobalKey - Mã hóa thành công và lưu vết last4")
    void testUpdateGlobalKey_Success() {
        AiGlobalConfig global = AiGlobalConfig.builder().id(1L).build();
        when(globalConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(global));
        when(encryptionUtil.encrypt("AIzaSySecretKey99", 1)).thenReturn("encrypted_cipher");

        UpdateGlobalKeyRequest request = UpdateGlobalKeyRequest.builder()
                .apiKey("AIzaSySecretKey99")
                .build();

        multiConfigService.updateGlobalKey(request, "admin@internhub.com");

        verify(globalConfigRepository).save(argThat(saved ->
                "encrypted_cipher".equals(saved.getApiKeyEncrypted()) &&
                "ey99".equals(saved.getApiKeyLast4()) &&
                "admin@internhub.com".equals(saved.getUpdatedBy())
        ));
    }

    @Test
    @DisplayName("updateProfile - Cập nhật đúng các tham số kỹ thuật riêng biệt")
    void testUpdateProfile_Success() {
        AiFeatureProfile profile = AiFeatureProfile.builder()
                .id(1L)
                .featureKey("CV_SCREENING")
                .featureName("Sàng Lọc CV")
                .modelName("gemini-3.5-flash-lite")
                .temperature(new BigDecimal("0.00"))
                .maxTokens(2048)
                .isActive(true)
                .build();
        when(profileRepository.findByFeatureKey("CV_SCREENING")).thenReturn(Optional.of(profile));

        UpdateFeatureProfileRequest request = UpdateFeatureProfileRequest.builder()
                .modelName("gemini-3.5-flash-lite")
                .temperature(new BigDecimal("0.05"))
                .maxTokens(1500)
                .systemInstruction("Prompt mới cho CV")
                .isActive(false)
                .build();

        AiMultiConfigResponse.FeatureProfileDto result = multiConfigService.updateProfile("CV_SCREENING", request, "admin@internhub.com");

        assertThat(result).isNotNull();
        assertThat(result.getTemperature()).isEqualTo(new BigDecimal("0.05"));
        assertThat(result.getMaxTokens()).isEqualTo(1500);
        assertThat(result.isActive()).isFalse();
        verify(profileRepository).save(profile);
    }

    @Test
    @DisplayName("testProfile - Hard Block: Ném 403 Forbidden nếu Profile đang bị vô hiệu hóa")
    void testTestProfile_ThrowsForbidden_WhenInactive() {
        AiFeatureProfile profile = AiFeatureProfile.builder()
                .featureKey("REPORT_ASSISTANT")
                .featureName("Trợ Lý Báo Cáo")
                .isActive(false)
                .build();
        when(profileRepository.findByFeatureKey("REPORT_ASSISTANT")).thenReturn(Optional.of(profile));

        TestProfileRequest request = TestProfileRequest.builder()
                .testPrompt("Kiểm tra báo cáo tuần")
                .build();

        assertThatThrownBy(() -> multiConfigService.testProfile("REPORT_ASSISTANT", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    @DisplayName("testProfile - Thành công khi Profile active và có API Key")
    void testTestProfile_Success() {
        AiFeatureProfile profile = AiFeatureProfile.builder()
                .featureKey("REPORT_ASSISTANT")
                .featureName("Trợ Lý Báo Cáo")
                .modelName("gemini-3.5-flash-lite")
                .temperature(new BigDecimal("0.10"))
                .maxTokens(1024)
                .systemInstruction("System prompt")
                .isActive(true)
                .build();
        when(profileRepository.findByFeatureKey("REPORT_ASSISTANT")).thenReturn(Optional.of(profile));

        AiGlobalConfig global = AiGlobalConfig.builder()
                .apiKeyEncrypted("enc_key")
                .keyVersion(1)
                .build();
        when(globalConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(global));
        when(encryptionUtil.decrypt("enc_key", 1)).thenReturn("raw_gemini_key");

        when(geminiApiClient.generateContent(anyString(), anyString(), anyString(), anyList(), anyString(), any(), anyInt()))
                .thenReturn("Phản hồi mẫu từ Gemini 3.5 Flash Lite");

        TestProfileRequest request = TestProfileRequest.builder()
                .testPrompt("Kiểm tra báo cáo tuần")
                .build();

        TestProfileResponse response = multiConfigService.testProfile("REPORT_ASSISTANT", request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getOutput()).isEqualTo("Phản hồi mẫu từ Gemini 3.5 Flash Lite");
        assertThat(response.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }
}
