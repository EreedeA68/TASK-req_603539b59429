package com.meridianmart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagDto {
    private Long id;
    private String flagName;
    private boolean isEnabled;
    private String storeId;
    private String updatedBy;
    private String updatedAt;
}
