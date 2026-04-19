package com.meridianmart.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "feature_flags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flag_name", nullable = false, length = 100)
    private String flagName;

    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "store_id", length = 50)
    private String storeId;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Builder.Default
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
