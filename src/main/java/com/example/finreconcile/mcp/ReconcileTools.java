package com.example.finreconcile.mcp;

import com.example.finreconcile.domain.ReconcileResult;
import com.example.finreconcile.service.ReconcileService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class ReconcileTools {

    private final ReconcileService service;

    public ReconcileTools(ReconcileService service) {
        this.service = service;
    }

    @McpTool(
            name = "list_batches",
            description = "列出本地数据目录中可核验的合法批次 ID",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<String> listBatches() {
        return service.listBatches();
    }

    @McpTool(
            name = "reconcile_batch",
            description = "核验指定批次的已支付订单与成功支付流水，返回待复核异常",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public ReconcileResult reconcileBatch(
            @McpToolParam(description = "批次 ID，仅允许字母、数字、下划线和连字符", required = true)
            String batchId) {
        return service.reconcile(batchId);
    }
}
