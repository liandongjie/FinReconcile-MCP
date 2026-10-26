package com.example.finreconcile.domain;

import java.math.BigDecimal;
import java.util.List;

public record Anomaly(
        String ruleCode,
        String orderId,
        BigDecimal orderAmount,
        List<String> paymentIds,
        BigDecimal actualAmount,
        String message) {
}
