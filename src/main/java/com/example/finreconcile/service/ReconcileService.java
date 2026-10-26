package com.example.finreconcile.service;

import com.example.finreconcile.domain.Anomaly;
import com.example.finreconcile.domain.BatchData;
import com.example.finreconcile.domain.OrderRecord;
import com.example.finreconcile.domain.PaymentRecord;
import com.example.finreconcile.domain.ReconcileResult;
import com.example.finreconcile.repository.BatchRepository;
import com.example.finreconcile.repository.ReconcileException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ReconcileService {

    private final BatchRepository repository;

    public ReconcileService(BatchRepository repository) {
        this.repository = repository;
    }

    public List<String> listBatches() {
        return repository.listBatchIds();
    }

    public ReconcileResult reconcile(String batchId) {
        BatchData batch = repository.load(batchId);
        validatePaymentReferences(batch);
        Map<String, List<PaymentRecord>> paymentsByOrder = batch.payments().stream()
                .collect(Collectors.groupingBy(PaymentRecord::orderId));
        List<Anomaly> anomalies = new ArrayList<>();

        batch.orders().stream()
                .sorted(Comparator.comparing(OrderRecord::orderId))
                .forEach(order -> addAnomaly(order, paymentsByOrder.getOrDefault(order.orderId(), List.of()), anomalies));

        return new ReconcileResult(
                batchId,
                batch.orders().size(),
                anomalies.size(),
                List.copyOf(anomalies),
                anomalies.isEmpty() ? "OK" : "ANOMALIES_FOUND");
    }

    private static void validatePaymentReferences(BatchData batch) {
        Set<String> orderIds = batch.orders().stream()
                .map(OrderRecord::orderId)
                .collect(Collectors.toSet());
        batch.payments().stream()
                .filter(payment -> !orderIds.contains(payment.orderId()))
                .min(Comparator.comparing(PaymentRecord::paymentId))
                .ifPresent(payment -> {
                    throw new ReconcileException(
                            "INVALID_CSV",
                            "成功支付流水引用不存在的订单: paymentId=" + payment.paymentId()
                                    + ", orderId=" + payment.orderId());
                });
    }

    private static void addAnomaly(OrderRecord order, List<PaymentRecord> payments, List<Anomaly> anomalies) {
        List<PaymentRecord> sortedPayments = payments.stream()
                .sorted(Comparator.comparing(PaymentRecord::paymentId))
                .toList();
        List<String> paymentIds = sortedPayments.stream().map(PaymentRecord::paymentId).toList();

        if (sortedPayments.isEmpty()) {
            anomalies.add(new Anomaly(
                    "MISSING_PAYMENT", order.orderId(), order.amount(), List.of(), null,
                    "已支付订单没有成功流水，需人工复核。"));
        } else if (sortedPayments.size() > 1) {
            anomalies.add(new Anomaly(
                    "DUPLICATE_SUCCESS", order.orderId(), order.amount(), paymentIds, null,
                    "发现多笔不同 ID 的成功流水，需人工复核；不代表已发生重复扣款。"));
        } else if (order.amount().compareTo(sortedPayments.get(0).amount()) != 0) {
            anomalies.add(new Anomaly(
                    "AMOUNT_MISMATCH", order.orderId(), order.amount(), paymentIds,
                    sortedPayments.get(0).amount(), "订单金额与成功流水金额不一致，需人工复核。"));
        }
    }
}
