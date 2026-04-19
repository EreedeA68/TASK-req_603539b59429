package com.meridianmart.service;

import com.meridianmart.model.ChangeHistory;
import com.meridianmart.repository.ChangeHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChangeHistoryService {

    private final ChangeHistoryRepository changeHistoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String entityType, String entityId, String fieldName,
                       String oldValue, String newValue, String changedBy, String ipAddress) {
        ChangeHistory entry = ChangeHistory.builder()
                .entityType(entityType)
                .entityId(entityId)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(changedBy)
                .ipAddress(ipAddress)
                .build();
        changeHistoryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<ChangeHistory> getHistoryFor(String entityType, String entityId) {
        return changeHistoryRepository.findByEntityTypeAndEntityIdOrderByChangedAtDesc(entityType, entityId);
    }
}
