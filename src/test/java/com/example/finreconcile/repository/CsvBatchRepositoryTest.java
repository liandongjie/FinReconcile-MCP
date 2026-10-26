package com.example.finreconcile.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvBatchRepositoryTest {

    @TempDir
    Path dataRoot;

    @Test
    void rejectsPathTraversalBeforeResolvingFiles() {
        assertError("INVALID_BATCH_ID", () -> repository().load("../secret"));
        assertError("INVALID_BATCH_ID", () -> repository().load("C:\\Windows"));
    }

    @Test
    void reportsMissingFilesInsteadOfReturningAnEmptyResult() throws IOException {
        Path batch = Files.createDirectory(dataRoot.resolve("missingFiles"));
        Files.writeString(batch.resolve("orders.csv"), "order_id,amount,status\nO1,1.00,PAID\n");

        assertError("FILE_NOT_FOUND", () -> repository().load("missingFiles"));
    }

    @Test
    void rejectsMissingColumnsAndFields() throws IOException {
        createBatch("missingColumn",
                "order_id,status\nO1,PAID\n",
                "payment_id,order_id,amount,status\nP1,O1,1.00,SUCCESS\n");
        createBatch("missingField",
                "order_id,amount,status\nO1,,PAID\n",
                "payment_id,order_id,amount,status\nP1,O1,1.00,SUCCESS\n");

        assertError("INVALID_CSV", () -> repository().load("missingColumn"));
        assertError("INVALID_CSV", () -> repository().load("missingField"));
    }

    @Test
    void rejectsUnknownStatuses() throws IOException {
        createBatch("badOrderStatus",
                "order_id,amount,status\nO1,1.00,CANCELLED\n",
                "payment_id,order_id,amount,status\nP1,O1,1.00,SUCCESS\n");
        createBatch("badPaymentStatus",
                "order_id,amount,status\nO1,1.00,PAID\n",
                "payment_id,order_id,amount,status\nP1,O1,1.00,FAILED\n");

        assertError("INVALID_CSV", () -> repository().load("badOrderStatus"));
        assertError("INVALID_CSV", () -> repository().load("badPaymentStatus"));
    }

    @Test
    void rejectsDuplicateOrderAndPaymentIds() throws IOException {
        createBatch("duplicateOrder",
                "order_id,amount,status\nO1,1.00,PAID\nO1,1.00,PAID\n",
                "payment_id,order_id,amount,status\nP1,O1,1.00,SUCCESS\n");
        createBatch("duplicatePayment",
                "order_id,amount,status\nO1,1.00,PAID\n",
                "payment_id,order_id,amount,status\nP1,O1,1.00,SUCCESS\nP1,O1,1.00,SUCCESS\n");

        assertError("INVALID_CSV", () -> repository().load("duplicateOrder"));
        assertError("INVALID_CSV", () -> repository().load("duplicatePayment"));
    }

    @Test
    void rejectsMoreThanTwoDecimalPlacesAndNonPositiveAmounts() throws IOException {
        createBatch("tooPrecise",
                "order_id,amount,status\nO1,1.001,PAID\n",
                "payment_id,order_id,amount,status\nP1,O1,1.00,SUCCESS\n");
        createBatch("zeroAmount",
                "order_id,amount,status\nO1,0.00,PAID\n",
                "payment_id,order_id,amount,status\nP1,O1,1.00,SUCCESS\n");

        assertError("INVALID_CSV", () -> repository().load("tooPrecise"));
        assertError("INVALID_CSV", () -> repository().load("zeroAmount"));
    }

    private CsvBatchRepository repository() {
        return new CsvBatchRepository(dataRoot);
    }

    private void createBatch(String batchId, String orders, String payments) throws IOException {
        Path batch = Files.createDirectory(dataRoot.resolve(batchId));
        Files.writeString(batch.resolve("orders.csv"), orders);
        Files.writeString(batch.resolve("payments.csv"), payments);
    }

    private static void assertError(String errorCode, Runnable action) {
        ReconcileException exception = assertThrows(ReconcileException.class, action::run);
        assertEquals(errorCode, exception.errorCode());
    }
}
