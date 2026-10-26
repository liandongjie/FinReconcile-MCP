package com.example.finreconcile.mcp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpStdioIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void startsServerListsAndCallsBothToolsOverStdio() throws Exception {
        Path project = Path.of("").toAbsolutePath();
        Path jar = project.resolve("target/fin-reconcile-mcp-1.0.0.jar");
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-jar", jar.toString());
        builder.directory(project.toFile());
        builder.environment().put("FIN_RECONCILE_DATA_DIR", project.resolve("data").toString());

        Process process = builder.start();
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(process));
        BufferedReader output = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8));
        try {
            try (BufferedWriter input = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8))) {

                send(input, request(1, "initialize", JSON.createObjectNode()
                        .put("protocolVersion", "2025-06-18")
                        .set("capabilities", JSON.createObjectNode())
                        .set("clientInfo", JSON.createObjectNode()
                                .put("name", "fin-reconcile-integration-test")
                                .put("version", "1.0"))));
                JsonNode initialize = response(output, 1);
                assertEquals("fin-reconcile-mcp", initialize.at("/result/serverInfo/name").stringValue());

                send(input, notification("notifications/initialized", JSON.createObjectNode()));

                send(input, request(2, "tools/list", JSON.createObjectNode()));
                JsonNode list = response(output, 2);
                Set<String> toolNames = new HashSet<>();
                list.at("/result/tools").forEach(tool -> toolNames.add(tool.path("name").stringValue()));
                assertEquals(Set.of("list_batches", "reconcile_batch"), toolNames);

                send(input, toolCall(3, "list_batches", JSON.createObjectNode()));
                JsonNode batches = response(output, 3);
                assertFalse(batches.at("/result/isError").booleanValue(), () -> batches.toString());
                JsonNode batchIds = JSON.readTree(batches.at("/result/content/0/text").stringValue());
                assertEquals(2, batchIds.size());
                assertEquals(Set.of("batch001", "batch002"),
                        Set.of(batchIds.get(0).stringValue(), batchIds.get(1).stringValue()));

                send(input, toolCall(4, "reconcile_batch", JSON.createObjectNode().put("batchId", "batch001")));
                JsonNode reconciliation = response(output, 4);
                assertFalse(reconciliation.at("/result/isError").booleanValue(), () -> reconciliation.toString());
                JsonNode result = JSON.readTree(reconciliation.at("/result/content/0/text").stringValue());
                assertEquals(3, result.path("anomalyCount").intValue());
                Set<String> ruleCodes = new HashSet<>();
                result.path("anomalies").forEach(anomaly -> ruleCodes.add(anomaly.path("ruleCode").stringValue()));
                assertEquals(Set.of("DUPLICATE_SUCCESS", "AMOUNT_MISMATCH", "MISSING_PAYMENT"), ruleCodes);

                send(input, toolCall(5, "reconcile_batch", JSON.createObjectNode().put("batchId", "../escape")));
                JsonNode invalidBatch = response(output, 5);
                assertTrue(invalidBatch.at("/result/isError").booleanValue(), () -> invalidBatch.toString());
                assertTrue(invalidBatch.at("/result/content/0/text").stringValue().contains("INVALID_BATCH_ID"));
            }

            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "服务器应在 stdin EOF 后退出");
            assertEquals(0, process.exitValue(), () -> "stderr: " + stderr.join());
            assertEquals("", output.lines().collect(Collectors.joining()).trim(),
                    "最后一个响应后 stdout 不应包含 banner 或日志");
            System.out.println("MCP_EVIDENCE tools=[list_batches,reconcile_batch] "
                    + "batches=[batch001,batch002] batch001.anomalyCount=3 "
                    + "invalidBatch.isError=true trailingStdout=empty exitCode=0");
        } finally {
            process.getOutputStream().close();
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly().waitFor(2, TimeUnit.SECONDS);
                }
            }
            output.close();
        }
    }

    private static ObjectNode request(int id, String method, JsonNode params) {
        return JSON.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", method)
                .set("params", params);
    }

    private static ObjectNode notification(String method, JsonNode params) {
        return JSON.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .set("params", params);
    }

    private static ObjectNode toolCall(int id, String name, JsonNode arguments) {
        return request(id, "tools/call", JSON.createObjectNode()
                .put("name", name)
                .set("arguments", arguments));
    }

    private static void send(BufferedWriter input, JsonNode message) throws IOException {
        input.write(JSON.writeValueAsString(message));
        input.newLine();
        input.flush();
    }

    private static JsonNode response(BufferedReader output, int id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            long remainingMillis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            String line = CompletableFuture.supplyAsync(() -> readLine(output))
                    .get(remainingMillis, TimeUnit.MILLISECONDS);
            if (line == null) {
                throw new AssertionError("MCP 进程在返回响应前关闭 stdout");
            }
            JsonNode message = JSON.readTree(line);
            if (message.path("id").intValue(-1) == id) {
                assertFalse(message.has("error"), () -> "MCP error: " + message);
                return message;
            }
        }
        throw new AssertionError("等待 MCP 响应超时: " + id);
    }

    private static String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static String readAll(Process process) {
        try {
            return new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return exception.toString();
        }
    }
}
