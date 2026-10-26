package com.example.finreconcile.repository;

import com.example.finreconcile.domain.BatchData;
import com.example.finreconcile.domain.OrderRecord;
import com.example.finreconcile.domain.PaymentRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class CsvBatchRepository implements BatchRepository {

    private static final Pattern BATCH_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Pattern AMOUNT = Pattern.compile("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,2})?");
    private static final List<String> ORDER_HEADERS = List.of("order_id", "amount", "status");
    private static final List<String> PAYMENT_HEADERS =
            List.of("payment_id", "order_id", "amount", "status");
    private static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .get();

    private final Path dataRoot;

    public CsvBatchRepository(Path dataRoot) {
        this.dataRoot = dataRoot.toAbsolutePath().normalize();
    }

    @Override
    public List<String> listBatchIds() {
        if (!Files.isDirectory(dataRoot)) {
            throw new ReconcileException("DATA_DIR_NOT_FOUND", "数据目录不存在");
        }
        try (var entries = Files.list(dataRoot)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(path -> BATCH_ID.matcher(path.getFileName().toString()).matches())
                    .filter(path -> Files.isRegularFile(path.resolve("orders.csv")))
                    .filter(path -> Files.isRegularFile(path.resolve("payments.csv")))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new ReconcileException("DATA_READ_ERROR", "无法读取数据目录", exception);
        }
    }

    @Override
    public BatchData load(String batchId) {
        Path batchDirectory = resolveBatchDirectory(batchId);
        Path ordersFile = requiredFile(batchDirectory.resolve("orders.csv"), "orders.csv");
        Path paymentsFile = requiredFile(batchDirectory.resolve("payments.csv"), "payments.csv");
        return new BatchData(readOrders(ordersFile), readPayments(paymentsFile));
    }

    private Path resolveBatchDirectory(String batchId) {
        if (batchId == null || !BATCH_ID.matcher(batchId).matches()) {
            throw new ReconcileException("INVALID_BATCH_ID", "batchId 只允许 1 至 32 位字母、数字、下划线或连字符");
        }
        Path resolved = dataRoot.resolve(batchId).normalize();
        if (!resolved.startsWith(dataRoot)) {
            throw new ReconcileException("INVALID_BATCH_ID", "batchId 不能越出数据目录");
        }
        if (!Files.isDirectory(resolved)) {
            throw new ReconcileException("BATCH_NOT_FOUND", "批次不存在: " + batchId);
        }
        return resolved;
    }

    private static Path requiredFile(Path path, String name) {
        if (!Files.isRegularFile(path)) {
            throw new ReconcileException("FILE_NOT_FOUND", "批次缺少文件: " + name);
        }
        return path;
    }

    private static List<OrderRecord> readOrders(Path path) {
        List<OrderRecord> orders = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try (CSVParser parser = parse(path, ORDER_HEADERS)) {
            for (CSVRecord row : parser) {
                String orderId = required(row, "order_id", path);
                if (!ids.add(orderId)) {
                    throw dataError(path, row, "重复订单号: " + orderId);
                }
                String status = required(row, "status", path);
                if (!"PAID".equals(status)) {
                    throw dataError(path, row, "未知订单状态: " + status);
                }
                orders.add(new OrderRecord(orderId, amount(row, path)));
            }
        } catch (IOException exception) {
            throw new ReconcileException("DATA_READ_ERROR", "无法读取文件: " + path.getFileName(), exception);
        }
        return List.copyOf(orders);
    }

    private static List<PaymentRecord> readPayments(Path path) {
        List<PaymentRecord> payments = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try (CSVParser parser = parse(path, PAYMENT_HEADERS)) {
            for (CSVRecord row : parser) {
                String paymentId = required(row, "payment_id", path);
                if (!ids.add(paymentId)) {
                    throw dataError(path, row, "重复流水号: " + paymentId);
                }
                String status = required(row, "status", path);
                if (!"SUCCESS".equals(status)) {
                    throw dataError(path, row, "未知流水状态: " + status);
                }
                payments.add(new PaymentRecord(
                        paymentId,
                        required(row, "order_id", path),
                        amount(row, path)));
            }
        } catch (IOException exception) {
            throw new ReconcileException("DATA_READ_ERROR", "无法读取文件: " + path.getFileName(), exception);
        }
        return List.copyOf(payments);
    }

    private static CSVParser parse(Path path, List<String> expectedHeaders) throws IOException {
        Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        try {
            CSVParser parser = CSV.parse(reader);
            if (!parser.getHeaderNames().equals(expectedHeaders)) {
                parser.close();
                throw new ReconcileException(
                        "INVALID_CSV", path.getFileName() + " 表头必须为 " + String.join(",", expectedHeaders));
            }
            return parser;
        } catch (RuntimeException | IOException exception) {
            reader.close();
            throw exception;
        }
    }

    private static String required(CSVRecord row, String field, Path path) {
        String value;
        try {
            value = row.get(field).trim();
        } catch (IllegalArgumentException exception) {
            throw dataError(path, row, "缺少字段: " + field);
        }
        if (value.isEmpty()) {
            throw dataError(path, row, "字段不能为空: " + field);
        }
        return value;
    }

    private static BigDecimal amount(CSVRecord row, Path path) {
        String value = required(row, "amount", path);
        if (!AMOUNT.matcher(value).matches()) {
            throw dataError(path, row, "非法金额: " + value);
        }
        BigDecimal amount = new BigDecimal(value);
        if (amount.signum() <= 0) {
            throw dataError(path, row, "金额必须为正数: " + value);
        }
        return amount;
    }

    private static ReconcileException dataError(Path path, CSVRecord row, String message) {
        return new ReconcileException(
                "INVALID_CSV", path.getFileName() + " 第 " + row.getRecordNumber() + " 条数据: " + message);
    }
}
