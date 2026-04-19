package com.meridianmart.service;

import com.meridianmart.audit.AuditService;
import com.meridianmart.dto.FeatureFlagDto;
import com.meridianmart.model.FeatureFlag;
import com.meridianmart.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final AuditService auditService;
    private final ChangeHistoryService changeHistoryService;

    @Transactional(readOnly = true)
    public List<FeatureFlagDto> getAllFlags() {
        return featureFlagRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public FeatureFlagDto toggleFlag(Long id, boolean enabled, String updatedBy, String ipAddress) {
        FeatureFlag flag = featureFlagRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature flag not found"));

        boolean previousState = flag.isEnabled();
        flag.setEnabled(enabled);
        flag.setUpdatedBy(updatedBy);
        flag.setUpdatedAt(LocalDateTime.now());
        flag = featureFlagRepository.save(flag);

        auditService.log(null, "CONFIG_CHANGE",
                String.format("Feature flag '%s' changed from %s to %s by %s",
                        flag.getFlagName(), previousState, enabled, updatedBy),
                ipAddress);

        changeHistoryService.record("FeatureFlag", String.valueOf(flag.getId()),
                "isEnabled", String.valueOf(previousState), String.valueOf(enabled),
                updatedBy, ipAddress);

        log.info("Feature flag {} toggled to {} by {}", flag.getFlagName(), enabled, updatedBy);
        return toDto(flag);
    }

    private FeatureFlagDto toDto(FeatureFlag flag) {
        return FeatureFlagDto.builder()
                .id(flag.getId())
                .flagName(flag.getFlagName())
                .isEnabled(flag.isEnabled())
                .storeId(flag.getStoreId())
                .updatedBy(flag.getUpdatedBy())
                .updatedAt(flag.getUpdatedAt() != null ? flag.getUpdatedAt().toString() : null)
                .build();
    }
}
