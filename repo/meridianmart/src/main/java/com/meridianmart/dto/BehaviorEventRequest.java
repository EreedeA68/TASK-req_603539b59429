package com.meridianmart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BehaviorEventRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Event type is required")
    private String eventType;
}
