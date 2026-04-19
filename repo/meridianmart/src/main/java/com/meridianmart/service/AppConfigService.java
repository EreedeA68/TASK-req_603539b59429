package com.meridianmart.service;

import com.meridianmart.audit.AuditService;
import com.meridianmart.model.AppConfig;
import com.meridianmart.repository.AppConfigRepository;
import com.meridianmart.security.AesEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppConfigService {

    private final AppConfigRepository appConfigRepository;
    private final AuditService auditService;
    private final ChangeHistoryService changeHistoryService;
    private final AesEncryptionService aesEncryptionService;

    private static final Set<String> SENSITIVE_KEY_PATTERNS =
            Set.of("secret", "password", "key", "token", "credential");

    private boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase();
        return SENSITIVE_KEY_PATTERNS.stream().anyMatch(lower::contains);
    }

    private String maybeRedact(String key, String value) {
        return value == null ? null : (isSensitiveKey(key) ? aesEncryptionService.mask(value) : value);
    }

    private String encryptIfSensitive(String key, String value) {
        if (value == null || !isSensitiveKey(key)) return value;
        return aesEncryptionService.encrypt(value);
    }

    String decryptIfSensitive(String key, String stored) {
        if (stored == null || !isSensitiveKey(key)) return stored;
        try {
            return aesEncryptionService.decrypt(stored);
        } catch (Exception e) {
            // pre-encryption legacy value; return as-is
            return stored;
        }
    }

    @Transactional(readOnly = true)
    public List<AppConfig> getAllConfigs() {
        return appConfigRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AppConfig getConfig(String key) {
        return appConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Config not found: " + key));
    }

    @Transactional
    public AppConfig upsertConfig(String key, String value, String updatedBy, String ipAddress) {
        AppConfig config = appConfigRepository.findByConfigKey(key)
                .orElse(AppConfig.builder().configKey(key).build());
        String oldRaw = config.getConfigValue();
        String oldPlain = decryptIfSensitive(key, oldRaw);
        config.setConfigValue(encryptIfSensitive(key, value));
        config.setUpdatedBy(updatedBy);
        config.setUpdatedAt(LocalDateTime.now());
        AppConfig saved = appConfigRepository.save(config);
        auditService.log(null, "CONFIG_CHANGE",
                String.format("Config '%s' changed from '%s' to '%s' by %s",
                        key,
                        oldPlain != null ? maybeRedact(key, oldPlain) : "(new)",
                        maybeRedact(key, value),
                        updatedBy),
                ipAddress);
        changeHistoryService.record("AppConfig", key, "configValue",
                maybeRedact(key, oldPlain), maybeRedact(key, value), updatedBy, ipAddress);
        return saved;
    }

    @Transactional(readOnly = true)
    public int getIntConfig(String key, int defaultValue) {
        return appConfigRepository.findByConfigKey(key)
                .map(c -> {
                    String plain = decryptIfSensitive(c.getConfigKey(), c.getConfigValue());
                    try { return Integer.parseInt(plain); }
                    catch (NumberFormatException e) { return defaultValue; }
                })
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public String getStringConfig(String key, String defaultValue) {
        return appConfigRepository.findByConfigKey(key)
                .map(c -> decryptIfSensitive(c.getConfigKey(), c.getConfigValue()))
                .orElse(defaultValue);
    }
}
