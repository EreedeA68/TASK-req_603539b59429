package com.meridianmart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DepositRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;
}
