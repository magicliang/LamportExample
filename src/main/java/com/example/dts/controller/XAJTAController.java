package com.example.dts.controller;

import com.example.dts.service.JTATransactionService;
import com.example.dts.service.XATransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * XA 和 JTA 事务控制器
 * 提供 XA 和 JTA 分布式事务的 REST API
 * 
 * @author DTS Team
 */
@RestController
@RequestMapping("/api/v1/xa-jta")
@Tag(name = "XA/JTA Transaction", description = "XA 和 JTA 分布式事务管理 API")
public class XAJTAController {

    private static final Logger logger = LoggerFactory.getLogger(XAJTAController.class);

    private final XATransactionService xaTransactionService;
    private final JTATransactionService jtaTransactionService;

    public XAJTAController(XATransactionService xaTransactionService, 
                          JTATransactionService jtaTransactionService) {
        this.xaTransactionService = xaTransactionService;
        this.jtaTransactionService = jtaTransactionService;
    }

    /**
     * 执行 XA 事务
     */
    @PostMapping("/xa/execute")
    @Operation(summary = "执行 XA 分布式事务", description = "基于 XA 协议执行分布式事务")
    public ResponseEntity<Map<String, Object>> executeXATransaction(
            @Parameter(description = "XA 事务请求") @Valid @RequestBody XATransactionRequest request) {
        
        logger.info("Received XA transaction request: type={}", request.getBusinessType());
        
        try {
            Map<String, Object> result = xaTransactionService.executeXATransaction(
                request.getBusinessType(), request.getBusinessData());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("XA transaction execution failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("transactionType", "XA");
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 异步执行 XA 事务
     */
    @PostMapping("/xa/execute-async")
    @Operation(summary = "异步执行 XA 分布式事务", description = "异步方式执行 XA 分布式事务")
    public ResponseEntity<Map<String, Object>> executeXATransactionAsync(
            @Parameter(description = "XA 事务请求") @Valid @RequestBody XATransactionRequest request) {
        
        logger.info("Received async XA transaction request: type={}", request.getBusinessType());
        
        try {
            CompletableFuture<Map<String, Object>> future = xaTransactionService.executeXATransactionAsync(
                request.getBusinessType(), request.getBusinessData());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ACCEPTED");
            response.put("message", "XA transaction submitted for async processing");
            response.put("transactionType", "XA_ASYNC");
            
            // 异步处理结果（实际应用中可能需要通过回调或轮询获取结果）
            future.thenAccept(result -> {
                logger.info("Async XA transaction completed: {}", result);
            }).exceptionally(throwable -> {
                logger.error("Async XA transaction failed", throwable);
                return null;
            });
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            logger.error("Async XA transaction submission failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("transactionType", "XA_ASYNC");
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 批量执行 XA 事务
     */
    @PostMapping("/xa/batch-execute")
    @Operation(summary = "批量执行 XA 分布式事务", description = "批量执行多个 XA 分布式事务")
    public ResponseEntity<Map<String, Object>> executeBatchXATransactions(
            @Parameter(description = "批量 XA 事务请求") @Valid @RequestBody BatchXATransactionRequest request) {
        
        logger.info("Received batch XA transaction request: count={}", request.getTransactions().size());
        
        try {
            List<Map<String, Object>> results = xaTransactionService.executeBatchXATransactions(
                request.getTransactions());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("totalTransactions", request.getTransactions().size());
            response.put("results", results);
            response.put("transactionType", "XA_BATCH");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Batch XA transaction execution failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("transactionType", "XA_BATCH");
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 执行 JTA 事务
     */
    @PostMapping("/jta/execute")
    @Operation(summary = "执行 JTA 分布式事务", description = "基于 JTA 规范执行分布式事务")
    public ResponseEntity<Map<String, Object>> executeJTATransaction(
            @Parameter(description = "JTA 事务请求") @Valid @RequestBody JTATransactionRequest request) {
        
        logger.info("Received JTA transaction request: type={}", request.getBusinessType());
        
        try {
            Map<String, Object> result = jtaTransactionService.executeJTATransaction(
                request.getBusinessType(), request.getBusinessData());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("JTA transaction execution failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("transactionType", "JTA");
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 查询 XA 事务日志
     */
    @GetMapping("/xa/logs/{transactionId}")
    @Operation(summary = "查询 XA 事务日志", description = "根据事务 ID 查询 XA 事务执行日志")
    public ResponseEntity<Map<String, Object>> getXATransactionLogs(
            @Parameter(description = "事务 ID") @PathVariable @NotBlank String transactionId) {
        
        logger.info("Querying XA transaction logs: {}", transactionId);
        
        try {
            List<Map<String, Object>> logs = xaTransactionService.queryXATransactionLogs(transactionId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("transactionId", transactionId);
            response.put("logs", logs);
            response.put("logCount", logs.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to query XA transaction logs", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("transactionId", transactionId);
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 获取 XA 事务统计信息
     */
    @GetMapping("/xa/statistics")
    @Operation(summary = "获取 XA 事务统计信息", description = "获取 XA 事务的统计数据")
    public ResponseEntity<Map<String, Object>> getXATransactionStatistics() {
        
        logger.info("Getting XA transaction statistics");
        
        try {
            Map<String, Object> statistics = xaTransactionService.getXATransactionStatistics();
            
            return ResponseEntity.ok(statistics);
            
        } catch (Exception e) {
            logger.error("Failed to get XA transaction statistics", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 查询 JTA 事务状态
     */
    @GetMapping("/jta/status/{transactionId}")
    @Operation(summary = "查询 JTA 事务状态", description = "根据事务 ID 查询 JTA 事务状态")
    public ResponseEntity<Map<String, Object>> getJTATransactionStatus(
            @Parameter(description = "事务 ID") @PathVariable @NotBlank String transactionId) {
        
        logger.info("Querying JTA transaction status: {}", transactionId);
        
        try {
            Map<String, Object> status = jtaTransactionService.getJTATransactionStatus(transactionId);
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Failed to query JTA transaction status", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("transactionId", transactionId);
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 获取活跃的 JTA 事务
     */
    @GetMapping("/jta/active")
    @Operation(summary = "获取活跃的 JTA 事务", description = "获取当前所有活跃的 JTA 事务")
    public ResponseEntity<Map<String, Object>> getActiveJTATransactions() {
        
        logger.info("Getting active JTA transactions");
        
        try {
            List<Map<String, Object>> activeTransactions = jtaTransactionService.getActiveJTATransactions();
            
            Map<String, Object> response = new HashMap<>();
            response.put("activeTransactions", activeTransactions);
            response.put("count", activeTransactions.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to get active JTA transactions", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 清理已完成的 JTA 事务
     */
    @PostMapping("/jta/cleanup")
    @Operation(summary = "清理已完成的 JTA 事务", description = "清理已提交或回滚的 JTA 事务状态")
    public ResponseEntity<Map<String, Object>> cleanupCompletedJTATransactions() {
        
        logger.info("Cleaning up completed JTA transactions");
        
        try {
            jtaTransactionService.cleanupCompletedTransactions();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Completed JTA transactions cleaned up");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to cleanup completed JTA transactions", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "XA/JTA 服务健康检查", description = "检查 XA 和 JTA 服务的健康状态")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("services", Map.of(
            "XA", "AVAILABLE",
            "JTA", "AVAILABLE"
        ));
        
        return ResponseEntity.ok(health);
    }

    /**
     * XA 事务请求 DTO
     */
    public static class XATransactionRequest {
        @NotBlank(message = "Business type is required")
        private String businessType;
        
        @NotNull(message = "Business data is required")
        private Map<String, Object> businessData;

        // Getters and Setters
        public String getBusinessType() {
            return businessType;
        }

        public void setBusinessType(String businessType) {
            this.businessType = businessType;
        }

        public Map<String, Object> getBusinessData() {
            return businessData;
        }

        public void setBusinessData(Map<String, Object> businessData) {
            this.businessData = businessData;
        }
    }

    /**
     * 批量 XA 事务请求 DTO
     */
    public static class BatchXATransactionRequest {
        @NotNull(message = "Transactions list is required")
        private List<Map<String, Object>> transactions;

        // Getters and Setters
        public List<Map<String, Object>> getTransactions() {
            return transactions;
        }

        public void setTransactions(List<Map<String, Object>> transactions) {
            this.transactions = transactions;
        }
    }

    /**
     * JTA 事务请求 DTO
     */
    public static class JTATransactionRequest {
        @NotBlank(message = "Business type is required")
        private String businessType;
        
        @NotNull(message = "Business data is required")
        private Map<String, Object> businessData;

        // Getters and Setters
        public String getBusinessType() {
            return businessType;
        }

        public void setBusinessType(String businessType) {
            this.businessType = businessType;
        }

        public Map<String, Object> getBusinessData() {
            return businessData;
        }

        public void setBusinessData(Map<String, Object> businessData) {
            this.businessData = businessData;
        }
    }
}