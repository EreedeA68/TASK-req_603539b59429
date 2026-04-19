package com.meridianmart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {
    private Long refundId;
    private String receiptNumber;
    private BigDecimal amount;
    private String updatedOrderStatus;
    private String processedAt;
}
