package com.meridianmart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PreAuthorizeRequest {
    @NotNull
    private Long orderId;
    @NotBlank
    private String idempotencyKey;
}
