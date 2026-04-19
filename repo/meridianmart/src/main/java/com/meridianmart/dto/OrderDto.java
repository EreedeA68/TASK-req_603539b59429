package com.meridianmart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    private Long id;
    private String receiptNumber;
    private BigDecimal totalAmount;
    private String status;
    private String transactionTimestamp;
    private List<OrderItemDto> items;
    private Long userId;
}
