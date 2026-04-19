package com.meridianmart.payment;

import com.meridianmart.model.DistributedLock;
import com.meridianmart.repository.DistributedLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final DistributedLockRepository lockRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String acquireLock(String lockName, int ttlSeconds) {
        lockRepository.deleteExpiredLocks(LocalDateTime.now());

        Optional<DistributedLock> existing = lockRepository.findByLockName(lockName);
        if (existing.isPresent()) {
            DistributedLock lock = existing.get();
            if (lock.getExpiresAt().isAfter(LocalDateTime.now())) {
                log.warn("Lock {} already held by {}", lockName, lock.getLockedBy());
                return null;
            }
            lockRepository.delete(lock);
        }

        String lockId = UUID.randomUUID().toString();
        DistributedLock lock = DistributedLock.builder()
                .lockName(lockName)
                .lockedBy(lockId)
                .lockedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(ttlSeconds))
                .build();

        try {
            lockRepository.saveAndFlush(lock);
            log.info("Acquired lock: {} by {}", lockName, lockId);
            return lockId;
        } catch (Exception e) {
            log.warn("Failed to acquire lock {}: {}", lockName, e.getMessage());
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseLock(String lockName, String lockId) {
        lockRepository.releaseByNameAndOwner(lockName, lockId);
        log.info("Released lock: {} by {}", lockName, lockId);
    }
}
