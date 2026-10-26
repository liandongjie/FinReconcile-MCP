package com.example.finreconcile.domain;

import java.util.List;

public record ReconcileResult(
        String batchId,
        int checkedOrders,
        int anomalyCount,
        List<Anomaly> anomalies,
        String status) {
}
