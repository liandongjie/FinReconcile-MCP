package com.example.finreconcile.domain;

import java.math.BigDecimal;

public record PaymentRecord(String paymentId, String orderId, BigDecimal amount) {
}
