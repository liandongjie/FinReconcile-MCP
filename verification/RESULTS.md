# FinReconcile-MCP · 验证记录

本文件保留对公开项目有意义的可复现验收结果。开发阶段的机器环境诊断、绝对路径及完整日志不随仓库发布。

## 验收结论

| 范围 | 状态 | 结果 |
| --- | --- | --- |
| P1：业务核心 | PASS | 两组固定 CSV：`batch001` 检出 3 条指定异常，`batch002` 检出 0 条异常 |
| P2：MCP 接入 | PASS | 真实 Java 子进程完成初始化、工具发现、工具调用、错误响应与正常退出 |
| P3：完整回归 | PASS | 10 个 JUnit 单元测试 + 1 个真实 stdio 集成测试；无失败、错误或跳过 |

### 已验证环境

- Windows PowerShell 7，JDK 21.0.10。
- Spring Boot 4.1.2、Spring AI 2.0.2、Maven Wrapper 3.9.16。
- 测试记录：2026-10-25 完成原项目全量回归；2026-10-26 在独立工作目录复制当前源码与数据并再次执行离线 `clean verify`，全部通过。

## 如何复现

在 Windows 环境、JDK 21 已配置的项目根目录执行：

```powershell
java -version
.\mvnw.cmd -B -ntp clean verify
```

注意：本项目的 stdio 集成测试通过 `java.exe` 启动子进程，目前只验收了 Windows；不要据此声称 macOS/Linux 的集成测试同样通过。

### 单元测试（10 项）

- `CsvBatchRepositoryTest`：6 项。覆盖非法路径、缺少文件、CSV 表头和字段缺失、未知状态、重复订单/流水号、非法金额。
- `ReconcileServiceTest`：4 项。覆盖三个业务规则、正常批次、`BigDecimal.compareTo` 金额比较、成功流水引用不存在订单的错误。

关键约束：同一订单多条不同 ID 的成功支付流水只标记 `DUPLICATE_SUCCESS`，不直接认定真实重复扣款。

### MCP 集成测试（1 项）

`McpStdioIT` 启动实际打包生成的 Java JAR，通过 stdin/stdout 发送和接收 MCP JSON-RPC 消息，验证：

1. `initialize`：服务名称为 `fin-reconcile-mcp`。
2. `tools/list`：恰好发现 `list_batches` 与 `reconcile_batch`。
3. `tools/call list_batches`：得到 `batch001`、`batch002`。
4. `tools/call reconcile_batch(batch001)`：检出 `DUPLICATE_SUCCESS`、`AMOUNT_MISMATCH`、`MISSING_PAYMENT` 三条异常。
5. `tools/call reconcile_batch(../escape)`：返回 `isError = true`，错误包含 `INVALID_BATCH_ID`。
6. 客户端关闭 stdin 后进程正常以退出码 0 结束，stdout 无普通日志或 banner 污染。

关键摘要：

```text
Unit tests:        10 run, 0 failures, 0 errors, 0 skipped
Stdio integration:  1 run, 0 failures, 0 errors, 0 skipped
Maven:             BUILD SUCCESS
```

## 安全与能力边界

- 仅处理本地 UTF-8 CSV 中的 `PAID` 订单和 `SUCCESS` 全额支付流水；金额使用 `BigDecimal`。
- 数据校验失败会产生稳定错误码，不返回伪造的 `OK`。
- `batchId` 使用字符白名单，并检查规范化路径；但该方案不等于针对不可信符号链接和多用户文件系统的完整沙盒。
- MCP 工具只读，不提供退款、划转或任何资金操作，不调用真实支付 API。
- 不支持部分支付、分期、退款、并发写入或支付渠道最终一致性；核验异常仅代表待复核，不构成真实资金损失结论。

更多使用说明和示例请参见 [README](../README.md)。
