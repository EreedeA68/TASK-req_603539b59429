package com.meridianmart.repository;

import com.meridianmart.model.ChangeHistory;
import org.springframework.data.repository.Repository;

import java.util.List;

@org.springframework.stereotype.Repository
public interface ChangeHistoryRepository extends Repository<ChangeHistory, Long> {

    ChangeHistory save(ChangeHistory changeHistory);

    List<ChangeHistory> findByEntityTypeAndEntityIdOrderByChangedAtDesc(String entityType, String entityId);

    List<ChangeHistory> findByChangedByOrderByChangedAtDesc(String changedBy);
}
