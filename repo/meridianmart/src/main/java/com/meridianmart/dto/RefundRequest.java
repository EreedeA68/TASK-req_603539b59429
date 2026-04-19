package com.meridianmart.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefundRequest {

    @NotBlank(message = "Receipt number is required")
    private String receiptNumber;

    private String reason;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}
