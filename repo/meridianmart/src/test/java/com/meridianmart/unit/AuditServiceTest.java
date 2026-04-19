package com.meridianmart.unit;

import com.meridianmart.audit.AuditService;
import com.meridianmart.model.AuditLog;
import com.meridianmart.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditLogRepository auditLogRepository;
    @InjectMocks AuditService auditService;

    @Test
    void loginFailureWritesImmutableAuditLog() {
        when(auditLogRepository.save(any())).thenAnswer(inv -> {
            AuditLog log = inv.getArgument(0);
            log.setId(1L);
            return log;
        });

        auditService.log(1L, "LOGIN_FAILURE", "Failed login attempt", "127.0.0.1");

        verify(auditLogRepository).save(argThat(log ->
                "LOGIN_FAILURE".equals(log.getAction()) &&
                log.getUserId().equals(1L) &&
                "127.0.0.1".equals(log.getIpAddress())
        ));
    }

    @Test
    void refundWritesAuditLog() {
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditService.log(2L, "REFUND_PROCESSED", "Refund for order RCP-001", "10.0.0.1");

        verify(auditLogRepository).save(argThat(log ->
                "REFUND_PROCESSED".equals(log.getAction()) &&
                log.getUserId().equals(2L)
        ));
    }

    @Test
    void configChangeWritesAuditLog() {
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditService.log(3L, "CONFIG_CHANGE", "Feature flag toggled", "192.168.1.1");

        verify(auditLogRepository).save(argThat(log ->
                "CONFIG_CHANGE".equals(log.getAction())
        ));
    }

    @Test
    void auditLogEntriesReturnedCorrectly() {
        AuditLog log1 = AuditLog.builder().id(1L).action("LOGIN_FAILURE").build();
        AuditLog log2 = AuditLog.builder().id(2L).action("REFUND_PROCESSED").build();

        when(auditLogRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(log1, log2));

        List<AuditLog> logs = auditService.getLogsForUser(1L);
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getAction()).isEqualTo("LOGIN_FAILURE");
    }

    @Test
    void auditLogRepositoryExposesNoDeleteMethods() {
        // AuditLogRepository extends bare Repository (not CrudRepository) to enforce
        // immutability — verify no delete method is declared on the interface.
        java.lang.reflect.Method[] methods = AuditLogRepository.class.getDeclaredMethods();
        for (java.lang.reflect.Method method : methods) {
            assertThat(method.getName()).doesNotContainIgnoringCase("delete");
        }
    }
}
