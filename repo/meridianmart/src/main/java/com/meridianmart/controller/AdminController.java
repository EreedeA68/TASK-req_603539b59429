package com.meridianmart.controller;

import com.meridianmart.audit.AuditService;
import com.meridianmart.dto.ApiResponse;
import com.meridianmart.dto.AppConfigResponseDto;
import com.meridianmart.dto.FeatureFlagDto;
import com.meridianmart.dto.UpdateConfigRequest;
import com.meridianmart.dto.UpdateFeatureFlagRequest;
import jakarta.validation.Valid;
import com.meridianmart.model.AppConfig;
import com.meridianmart.model.User;
import com.meridianmart.payment.PaymentService;
import com.meridianmart.security.AesEncryptionService;
import com.meridianmart.service.AppConfigService;
import com.meridianmart.service.FeatureFlagService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final FeatureFlagService featureFlagService;
    private final PaymentService paymentService;
    private final AuditService auditService;
    private final AppConfigService appConfigService;
    private final AesEncryptionService aesEncryptionService;

    private static final java.util.Set<String> SENSITIVE_KEY_PATTERNS =
            java.util.Set.of("secret", "password", "key", "token", "credential");

    private boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase();
        return SENSITIVE_KEY_PATTERNS.stream().anyMatch(lower::contains);
    }

    private AppConfigResponseDto toConfigDto(AppConfig config) {
        String rawValue = config.getConfigValue();
        String displayValue;
        if (isSensitiveKey(config.getConfigKey())) {
            String plaintext;
            try {
                plaintext = aesEncryptionService.decrypt(rawValue);
            } catch (Exception e) {
                plaintext = rawValue; // pre-encryption legacy fallback
            }
            displayValue = aesEncryptionService.mask(plaintext);
        } else {
            displayValue = rawValue;
        }
        return AppConfigResponseDto.builder()
                .id(config.getId())
                .configKey(config.getConfigKey())
                .configValue(displayValue)
                .description(config.getDescription())
                .updatedBy(config.getUpdatedBy())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    @GetMapping("/feature-flags")
    public ResponseEntity<ApiResponse<List<FeatureFlagDto>>> getFeatureFlags() {
        List<FeatureFlagDto> flags = featureFlagService.getAllFlags();
        return ResponseEntity.ok(ApiResponse.success(flags));
    }

    @PutMapping("/feature-flags/{id}")
    public ResponseEntity<ApiResponse<FeatureFlagDto>> updateFeatureFlag(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFeatureFlagRequest body,
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        FeatureFlagDto flag = featureFlagService.toggleFlag(id, body.getIsEnabled(), user.getUsername(), request.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(flag));
    }

    @GetMapping("/compliance-reports")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getComplianceReports() {
        Map<String, Object> reconciliation = paymentService.getDailyReconciliation();
        List<com.meridianmart.model.AuditLog> auditLogs = auditService.getAllLogs();

        Map<String, Object> report = Map.of(
                "paymentReconciliation", reconciliation,
                "auditLogCount", auditLogs.size(),
                "recentAuditLogs", auditLogs.stream()
                        .limit(20)
                        .map(log -> Map.of(
                                "id", log.getId(),
                                "action", log.getAction(),
                                "createdAt", log.getCreatedAt().toString()
                        ))
                        .toList(),
                "generatedAt", java.time.LocalDateTime.now().toString()
        );

        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<List<AppConfigResponseDto>>> getConfigs() {
        List<AppConfigResponseDto> dtos = appConfigService.getAllConfigs().stream()
                .map(this::toConfigDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @PutMapping("/config/{key}")
    public ResponseEntity<ApiResponse<AppConfigResponseDto>> upsertConfig(
            @PathVariable String key,
            @Valid @RequestBody UpdateConfigRequest body,
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        AppConfig config = appConfigService.upsertConfig(key, body.getValue(), user.getUsername(), request.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(toConfigDto(config)));
    }
}
