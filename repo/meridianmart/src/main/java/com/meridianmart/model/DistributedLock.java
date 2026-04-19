package com.meridianmart.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "distributed_locks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistributedLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lock_name", nullable = false, unique = true, length = 255)
    private String lockName;

    @Column(name = "locked_by", nullable = false, length = 255)
    private String lockedBy;

    @Builder.Default
    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
