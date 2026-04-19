package com.meridianmart.repository;

import com.meridianmart.model.AuditLog;
import org.springframework.data.repository.Repository;

import java.util.List;

@org.springframework.stereotype.Repository
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    List<AuditLog> findAll();

    List<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);
}
