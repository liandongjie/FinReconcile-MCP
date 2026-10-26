<div align="center">

# FinReconcile-MCP

**把确定性的支付对账能力，交给 AI 客户端按需调用。**

基于 Java 21、Spring Boot 与 Spring AI 的轻量级、只读 MCP Server。

无需自建 Agent 或聊天界面，通过 `stdio` 连接 Codex，即可用自然语言核验本地模拟支付数据。

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot 4.1.2](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring AI 2.0.2](https://img.shields.io/badge/Spring%20AI-2.0.2-0D9488?style=flat-square)
![MCP stdio](https://img.shields.io/badge/MCP-stdio-6366F1?style=flat-square)

</div>

![FinReconcile-MCP 彩色封面](docs/images/cover.svg)

> **项目定位**：面向教学模拟数据的支付对账 MCP 工具服务，不连接真实支付渠道，不执行扣款、退款或转账。所有异常均为**待人工复核信号**，不是资金损失或欺诈结论。

[快速开始](#快速开始) · [架构设计](#架构设计) · [工具与规则](#工具与规则) · [运行演示](#运行演示) · [测试与质量](#测试与质量)

## 项目能做什么？

| 能力 | 实际实现 |
| --- | --- |
| **AI 工具接入** | 使用 Spring AI 注解发布 `list_batches`、`reconcile_batch` 两个 MCP Tool |
| **确定性核验** | 按订单关联成功支付流水，检测重复成功流水、金额不一致和成功流水缺失 |
| **可靠的金额比较** | 使用 `BigDecimal.compareTo()`，正确处理 `0.10` 与 `0.1` |
| **输入与数据校验** | 对批次编号、CSV 表头、主键、金额、状态及支付流水的订单引用进行校验 |
| **端到端验证** | 10 个单元测试、1 个启动真实 JAR 的 stdio 集成测试；已完成 Codex 实际调用 |

**设计原则**：Java 负责业务事实和判断；Codex 负责理解自然语言、选择工具和解释结果。MCP 解决的是工具的标准化连接问题，而不是把金融判断交给大模型。

## 架构设计

![FinReconcile-MCP 彩色系统架构图](docs/images/architecture.svg)

| 模块 | 职责 | 关键类 |
| --- | --- | --- |
| MCP 接入层 | 工具注册、接收参数、返回结果 | `ReconcileTools` |
| 业务逻辑层 | 订单与流水关联、数据完整性校验、三条互斥规则 | `ReconcileService` |
| 数据访问层 | 按批次读取及校验本地 UTF-8 CSV | `BatchRepository` / `CsvBatchRepository` |
| 数据模型 | 订单、流水、异常以及核验摘要 | `domain/` 下的 record |

通信链路：Codex（MCP Client）通过 **stdio** 与 Java 子进程交换 **JSON-RPC** 消息；使用 `tools/list` 发现工具，通过 `tools/call` 调用工具。Server 不提供 HTTP 接口，也不需要模型 API Key。

## 工具与规则

### 两个 MCP Tool

| Tool | 输入 | 返回 |
| --- | --- | --- |
| `list_batches` | 无 | 同时具有 `orders.csv` 和 `payments.csv` 的合法批次编号，按字典序排序 |
| `reconcile_batch` | `batchId`，例如 `batch001` | `batchId`、`checkedOrders`、`anomalyCount`、`anomalies`、`status` |

### 三条互斥业务规则

| 触发条件 | 规则编号 | 业务含义 |
| --- | --- | --- |
| 已支付订单没有成功流水 | `MISSING_PAYMENT` | 订单与支付记录无法匹配 |
| 同一订单存在两笔及以上不同 ID 的成功流水 | `DUPLICATE_SUCCESS` | 可能存在重复记录，**不等于已经重复扣款** |
| 恰有一笔成功流水，但金额与订单不相等 | `AMOUNT_MISMATCH` | 金额需要人工核实 |

恰好一笔成功流水且金额相等的订单不产生异常。规则顺序是**无流水 → 多笔流水 → 单笔金额比较**，不会把多笔流水简单求和后当作正常的分次支付。

在执行业务规则前，程序还会校验每笔成功流水引用的订单是否存在；如存在孤立引用，返回 `INVALID_CSV`，而不是将批次误报为 `OK`。

### CSV 数据约定

项目自带两组纯模拟数据：

| 批次 | 订单数 | 核验结果 |
| --- | ---: | --- |
| `data/batch001` | 3 | 三类异常各一项，`ANOMALIES_FOUND` |
| `data/batch002` | 2 | 0 项异常，`OK` |

订单文件 `orders.csv`：

```csv
order_id,amount,status
O1001,100.00,PAID
O1002,200.00,PAID
O1003,80.00,PAID
```

支付流水文件 `payments.csv`：

```csv
payment_id,order_id,amount,status
P001,O1001,100.00,SUCCESS
P002,O1001,100.00,SUCCESS
P003,O1002,199.00,SUCCESS
```

CSV 使用 UTF-8；订单仅接受 `PAID`，流水仅接受 `SUCCESS`；金额必须为大于 0 且最多两位小数的十进制数；订单号、流水号在各自文件内必须唯一。此项目**不支持**退款、分期、部分支付和其他状态。

## 快速开始

### 1. 环境要求

- **JDK 21**（不是只有 JRE）；`JAVA_HOME` 应指向 JDK 21 的安装目录。
- **Windows + PowerShell 7**：已验证的开发与端到端测试环境。
- Maven Wrapper 随仓库提供（Maven 3.9.16），不需要预装 Maven；首次构建可能需要下载依赖。
- 本项目不需要 MySQL、Redis、Docker、OpenAI/DeepSeek Key，也不监听端口。

克隆仓库后，在**项目根目录**打开 PowerShell：

```powershell
java -version   # 应显示 Java 21
.\mvnw.cmd -B -ntp clean verify
```

如果 `java -version` 不是 21，请先在你的机器上配置 JDK 21 的 `JAVA_HOME` 和 `PATH`，然后重试。Windows 的真实 stdio 集成测试会使用 `java.exe`，因此目前**不能宣称 macOS/Linux 的 `clean verify` 已通过**。

成功后会得到：

```text
target/fin-reconcile-mcp-1.0.0.jar
```

### 2. 注册到 Codex

确保上一步构建完成，并且 `JAVA_HOME` 指向 JDK 21。在同一个 PowerShell 窗口执行：

```powershell
$javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
$jarFile = (Resolve-Path ".\target\fin-reconcile-mcp-1.0.0.jar").Path
$dataDir = (Resolve-Path ".\data").Path

codex mcp add fin-reconcile --env "FIN_RECONCILE_DATA_DIR=$dataDir" -- $javaExe -jar $jarFile
codex mcp list
```

这里传入**绝对路径**，避免 Codex 从其他工作目录启动时找不到 JAR 或 CSV；PowerShell 会将包含空格的 Java 路径作为一个参数传递。Codex CLI 需要单独安装和登录；若已有同名 MCP 配置，请先检查再更新，避免重复注册。

### 3. 用自然语言调用

重新打开一个 Codex 会话，输入：

> 请通过 fin-reconcile MCP 工具列出所有可用批次，分别核验 batch001 和 batch002；按异常规则解释结果，不要推测异常原因。

预期的调用顺序是 `list_batches` → `reconcile_batch(batch001)` → `reconcile_batch(batch002)`。可在 Codex 的工具调用记录中确认实际执行的是 MCP Tool，而不是直接读取 CSV。

卸载注册配置：

```powershell
codex mcp remove fin-reconcile
```

## 运行演示

![FinReconcile-MCP 核验结果彩色可视化：模拟数据](docs/images/sample-result.svg)

**batch001** 返回 `anomalyCount = 3`，对应三笔订单：

| 订单 | 结果 | 证据 |
| --- | --- | --- |
| `O1001` | `DUPLICATE_SUCCESS` | 金额 100.00；关联成功流水 `P001` 和 `P002` |
| `O1002` | `AMOUNT_MISMATCH` | 订单 200.00；流水 `P003` 金额 199.00 |
| `O1003` | `MISSING_PAYMENT` | 订单 80.00；未找到成功流水 |

结果关键字段节选：

```json
{
  "batchId": "batch001",
  "checkedOrders": 3,
  "anomalyCount": 3,
  "status": "ANOMALIES_FOUND"
}
```

**batch002** 返回 `checkedOrders = 2`、`anomalyCount = 0`、`status = "OK"`；其中 `0.10` 与 `0.1` 按 `BigDecimal` 数值比较相等。上图是**模拟数据的结果信息图**，不是实盘交易界面或客户端截图。

### 错误处理

异常输入不会被伪装成成功核验；MCP 返回 `isError = true` 的工具结果，错误信息包含稳定的 `errorCode`。

| 错误码 | 示例 |
| --- | --- |
| `INVALID_BATCH_ID` | `batchId=../escape` 或长度/字符非法 |
| `BATCH_NOT_FOUND` | 批次目录不存在 |
| `FILE_NOT_FOUND` | 缺少必要 CSV 文件 |
| `INVALID_CSV` | 表头错误、金额非法、重复主键、未知状态或孤立流水引用 |
| `DATA_DIR_NOT_FOUND` | 指定的数据根目录不存在 |
| `DATA_READ_ERROR` | 数据目录或文件 I/O 读取失败 |

## 测试与质量

已在 Windows / Java 21 环境中完成完整 Maven `clean verify`：

| 测试 | 数量 | 覆盖范围 |
| --- | ---: | --- |
| Repository 单元测试 | 6 | 文件、CSV、字段、状态、金额、重复 ID 与路径校验 |
| Service 单元测试 | 4 | 三条规则、正常批次、金额精度、孤立支付流水 |
| 真实 stdio 集成测试 | 1 | 启动 JAR、`initialize`、`tools/list`、两个工具调用、错误响应、stdout 纯协议及正常退出 |

**结果：10 个单元测试 + 1 个集成测试，全部通过。** 具体测试命令、日志和已知限制见 [验证报告](verification/RESULTS.md)；注意其中尾部保留了早期网络阻塞的历史诊断，顶部 PASS 才代表最终交付状态。

项目不会在业务代码中向 stdout 打印普通日志；Spring Boot banner 与普通控制台日志已关闭，避免污染 stdio 协议输出。

## 主要目录

| 路径 | 内容 |
| --- | --- |
| `src/main/java/.../mcp/ReconcileTools.java` | MCP 工具定义 |
| `src/main/java/.../service/ReconcileService.java` | 对账规则与数据引用校验 |
| `src/main/java/.../repository/` | CSV 数据读取、校验、错误类型与仓储接口 |
| `src/main/java/.../domain/` | 订单、流水、异常、对账结果模型 |
| `src/test/java/.../` | 业务单元测试和真实 stdio 集成测试 |
| `data/batch001/`、`data/batch002/` | 固定模拟数据 |
| `docs/images/` | README 使用的彩色 SVG 信息图 |
| `verification/RESULTS.md` | 全量验收记录 |

## 边界与适用场景

这是一个**面向 Java/Spring AI/MCP 工程实践的教学型 MVP**：

- 只支持**本地 CSV + 一次性全额支付**模型；不涉及数据库、实时同步、跨系统最终一致性。
- 只读工具，不提供修改订单、退款、转账或其他资金操作。`readOnlyHint` 是客户端提示，真正的只读边界由 Java 工具能力决定。
- 对账异常属于**待复核事实**，不构成真实重复扣款、资金损失、财务审计或风控定性结论。

项目的核心价值不在于功能数量，而在于 **“确定性业务逻辑 → 标准 MCP Tool → 外部 AI 客户端”** 的完整、可验证闭环。

## 开源许可

本项目采用 [MIT License](LICENSE) 开源。示例仅用于学习和技术交流，不提供真实支付结算或风控建议。
