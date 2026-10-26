package com.example.finreconcile.domain;

import java.math.BigDecimal;

public record OrderRecord(String orderId, BigDecimal amount) {
}
