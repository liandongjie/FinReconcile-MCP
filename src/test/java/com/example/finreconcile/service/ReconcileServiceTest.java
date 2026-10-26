package com.example.finreconcile.service;

import com.example.finreconcile.domain.BatchData;
import com.example.finreconcile.domain.OrderRecord;
import com.example.finreconcile.domain.PaymentRecord;
import com.example.finreconcile.domain.ReconcileResult;
import com.example.finreconcile.repository.BatchRepository;
import com.example.finreconcile.repository.CsvBatchRepository;
import com.example.finreconcile.repository.ReconcileException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconcileServiceTest {

    private final ReconcileService service =
            new ReconcileService(new CsvBatchRepository(Path.of("data")));

    @Test
    void batch001ContainsTheThreeExpectedAnomaliesInOrder() {
        ReconcileResult result = service.reconcile("batch001");

        assertEquals("ANOMALIES_FOUND", result.status());
        assertEquals(3, result.checkedOrders());
        assertEquals(3, result.anomalyCount());
        assertEquals(List.of("O1001", "O1002", "O1003"),
                result.anomalies().stream().map(anomaly -> anomaly.orderId()).toList());
        assertEquals(List.of("DUPLICATE_SUCCESS", "AMOUNT_MISMATCH", "MISSING_PAYMENT"),
                result.anomalies().stream().map(anomaly -> anomaly.ruleCode()).toList());
        assertEquals(List.of("P001", "P002"), result.anomalies().get(0).paymentIds());
        assertEquals("199.00", result.anomalies().get(1).actualAmount().toPlainString());
        assertEquals(List.of(), result.anomalies().get(2).paymentIds());
    }

    @Test
    void batch002IsCleanAndComparesMoneyByDecimalValue() {
        ReconcileResult result = service.reconcile("batch002");

        assertEquals("OK", result.status());
        assertEquals(2, result.checkedOrders());
        assertEquals(0, result.anomalyCount());
        assertEquals(List.of(), result.anomalies());
    }

    @Test
    void listsOnlyTheTwoValidSampleBatches() {
        assertEquals(List.of("batch001", "batch002"), service.listBatches());
    }

    @Test
    void rejectsSuccessfulPaymentReferencingAMissingOrder() {
        BatchRepository repository = new BatchRepository() {
            @Override
            public List<String> listBatchIds() {
                return List.of("orphanPayment");
            }

            @Override
            public BatchData load(String batchId) {
                return new BatchData(
                        List.of(new OrderRecord("O1001", new BigDecimal("100.00"))),
                        List.of(new PaymentRecord("P999", "O9999", new BigDecimal("100.00"))));
            }
        };
        ReconcileService orphanService = new ReconcileService(repository);

        ReconcileException exception = assertThrows(
                ReconcileException.class,
                () -> orphanService.reconcile("orphanPayment"));

        assertEquals("INVALID_CSV", exception.errorCode());
        assertTrue(exception.getMessage().contains("paymentId=P999"));
        assertTrue(exception.getMessage().contains("orderId=O9999"));
    }
}
