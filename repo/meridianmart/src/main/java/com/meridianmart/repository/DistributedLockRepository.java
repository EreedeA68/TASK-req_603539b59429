package com.meridianmart.repository;

import com.meridianmart.model.DistributedLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface DistributedLockRepository extends JpaRepository<DistributedLock, Long> {

    Optional<DistributedLock> findByLockName(String lockName);

    @Modifying
    @Query("DELETE FROM DistributedLock dl WHERE dl.expiresAt < :now")
    void deleteExpiredLocks(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM DistributedLock dl WHERE dl.lockName = :lockName AND dl.lockedBy = :lockedBy")
    void releaseByNameAndOwner(@Param("lockName") String lockName, @Param("lockedBy") String lockedBy);
}
