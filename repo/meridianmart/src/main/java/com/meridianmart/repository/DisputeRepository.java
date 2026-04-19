package com.meridianmart.repository;

import com.meridianmart.model.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    @Query("SELECT DISTINCT d.user.id FROM Dispute d WHERE d.status = 'OPEN'")
    List<Long> findUserIdsWithOpenDisputes();
}
