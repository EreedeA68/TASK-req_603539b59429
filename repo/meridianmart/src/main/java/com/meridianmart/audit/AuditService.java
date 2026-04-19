package com.meridianmart.audit;

import com.meridianmart.model.AuditLog;
import com.meridianmart.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String details, String ipAddress) {
        AuditLog entry = AuditLog.builder()
                .userId(userId)
                .action(action)
                .details(details)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(entry);
        log.info("AUDIT: userId={} action={}", userId, action);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String details, String ipAddress) {
        log(null, action, details, ipAddress);
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditLog> getLogsForUser(Long userId) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditLog> getLogsByAction(String action) {
        return auditLogRepository.findByActionOrderByCreatedAtDesc(action);
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditLog> getAllLogs() {
        return auditLogRepository.findAll();
    }
}
