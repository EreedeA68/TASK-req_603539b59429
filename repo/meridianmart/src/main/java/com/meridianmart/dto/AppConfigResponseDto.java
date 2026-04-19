package com.meridianmart.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AppConfigResponseDto {
    private Long id;
    private String configKey;
    private String configValue;
    private String description;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
