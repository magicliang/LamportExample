package com.example.dts.controller;

import com.example.dts.service.DistributedTransactionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 分布式事务管理REST控制器
 * 提供Seata分布式事务的API接口
 * 
 * @author DTS Team
 */
@RestController
@RequestMapping("/v1/transaction")
@Api(tags = "分布式事务管理", description = "Seata分布式事务管理API")
public class TransactionController {
    
    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);
    
    private final DistributedTransactionService distributedTransactionService;
    
    public TransactionController(DistributedTransactionService distributedTransactionService) {
        this.distributedTransactionService = distributedTransactionService;
    }
    
    /**
     * 执行AT模式分布式事务
     */
    @PostMapping("/at")
    @ApiOperation("执行AT模式分布式事务")
    public ResponseEntity<Map<String, Object>> executeATTransaction(
            @ApiParam("AT事务请求") @Valid @RequestBody TransactionRequest request) {
        
        try {
            logger.info("Executing AT transaction: type={}", request.getBusinessType());
            
            Map<String, Object> result = distributedTransactionService.executeATTransaction(
                    request.getBusinessType(), 
                    request.getBusinessData()
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("AT transaction failed: type={}, error={}", 
                        request.getBusinessType(), e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("transactionType", "AT");
            errorResponse.put("businessType", request.getBusinessType());
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 执行TCC模式分布式事务
     */
    @PostMapping("/tcc")
    @ApiOperation("执行TCC模式分布式事务")
    public ResponseEntity<Map<String, Object>> executeTCCTransaction(
            @ApiParam("TCC事务请求") @Valid @RequestBody TransactionRequest request) {
        
        try {
            logger.info("Executing TCC transaction: type={}", request.getBusinessType());
            
            Map<String, Object> result = distributedTransactionService.executeTCCTransaction(
                    request.getBusinessType(), 
                    request.getBusinessData()
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("TCC transaction failed: type={}, error={}", 
                        request.getBusinessType(), e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("transactionType", "TCC");
            errorResponse.put("businessType", request.getBusinessType());
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 执行SAGA模式分布式事务
     */
    @PostMapping("/saga")
    @ApiOperation("执行SAGA模式分布式事务")
    public ResponseEntity<Map<String, Object>> executeSAGATransaction(
            @ApiParam("SAGA事务请求") @Valid @RequestBody TransactionRequest request) {
        
        try {
            logger.info("Executing SAGA transaction: type={}", request.getBusinessType());
            
            Map<String, Object> result = distributedTransactionService.executeSAGATransaction(
                    request.getBusinessType(), 
                    request.getBusinessData()
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("SAGA transaction failed: type={}, error={}", 
                        request.getBusinessType(), e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("transactionType", "SAGA");
            errorResponse.put("businessType", request.getBusinessType());
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 异步执行分布式事务
     */
    @PostMapping("/async")
    @ApiOperation("异步执行分布式事务")
    public ResponseEntity<Map<String, Object>> executeTransactionAsync(
            @ApiParam("异步事务请求") @Valid @RequestBody AsyncTransactionRequest request) {
        
        try {
            logger.info("Executing async transaction: type={}, businessType={}", 
                       request.getTransactionType(), request.getBusinessType());
            
            CompletableFuture<Map<String, Object>> future = distributedTransactionService
                    .executeTransactionAsync(
                            request.getTransactionType(),
                            request.getBusinessType(), 
                            request.getBusinessData()
                    );
            
            // 返回异步执行的任务ID
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("async", true);
            response.put("transactionType", request.getTransactionType());
            response.put("businessType", request.getBusinessType());
            response.put("taskId", generateTaskId());
            response.put("submittedAt", System.currentTimeMillis());
            response.put("message", "Transaction submitted for async execution");
            
            // 异步处理结果（实际项目中可能需要通过消息队列或回调处理）
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Async transaction failed: type={}, businessType={}, error={}", 
                               request.getTransactionType(), request.getBusinessType(), throwable.getMessage());
                } else {
                    logger.info("Async transaction completed: type={}, businessType={}, success={}", 
                               request.getTransactionType(), request.getBusinessType(), result.get("success"));
                }
            });
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Async transaction submission failed: type={}, businessType={}, error={}", 
                        request.getTransactionType(), request.getBusinessType(), e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("async", true);
            errorResponse.put("transactionType", request.getTransactionType());
            errorResponse.put("businessType", request.getBusinessType());
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 批量执行分布式事务
     */
    @PostMapping("/batch")
    @ApiOperation("批量执行分布式事务")
    public ResponseEntity<Map<String, Object>> executeBatchTransactions(
            @ApiParam("批量事务请求") @Valid @RequestBody BatchTransactionRequest request) {
        
        try {
            logger.info("Executing batch transactions: count={}, type={}", 
                       request.getTransactions().size(), request.getTransactionType());
            
            java.util.List<CompletableFuture<Map<String, Object>>> futures = new java.util.ArrayList<>();
            
            // 提交所有事务
            for (TransactionRequest txRequest : request.getTransactions()) {
                CompletableFuture<Map<String, Object>> future = distributedTransactionService
                        .executeTransactionAsync(
                                request.getTransactionType(),
                                txRequest.getBusinessType(),
                                txRequest.getBusinessData()
                        );
                futures.add(future);
            }
            
            // 等待所有事务完成
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("batch", true);
            response.put("transactionType", request.getTransactionType());
            response.put("totalCount", request.getTransactions().size());
            response.put("batchId", generateTaskId());
            response.put("submittedAt", System.currentTimeMillis());
            
            // 异步处理批量结果
            allFutures.whenComplete((result, throwable) -> {
                int successCount = 0;
                int failureCount = 0;
                
                for (CompletableFuture<Map<String, Object>> future : futures) {
                    try {
                        Map<String, Object> txResult = future.get();
                        if (Boolean.TRUE.equals(txResult.get("success"))) {
                            successCount++;
                        } else {
                            failureCount++;
                        }
                    } catch (Exception e) {
                        failureCount++;
                    }
                }
                
                logger.info("Batch transactions completed: total={}, success={}, failure={}", 
                           futures.size(), successCount, failureCount);
            });
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Batch transaction submission failed: error={}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("batch", true);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 获取事务状态（模拟）
     */
    @GetMapping("/status/{transactionId}")
    @ApiOperation("获取事务状态")
    public ResponseEntity<Map<String, Object>> getTransactionStatus(
            @ApiParam("事务ID") @PathVariable @NotBlank String transactionId) {
        
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("transactionId", transactionId);
            status.put("status", "COMMITTED"); // 模拟状态
            status.put("createdAt", System.currentTimeMillis() - 60000);
            status.put("completedAt", System.currentTimeMillis());
            status.put("duration", 60000);
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error getting transaction status: id={}, error={}", transactionId, e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("transactionId", transactionId);
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 生成任务ID
     */
    private String generateTaskId() {
        return "task-" + System.currentTimeMillis() + "-" + 
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 事务请求DTO
     */
    public static class TransactionRequest {
        @NotBlank(message = "业务类型不能为空")
        private String businessType;
        
        private Map<String, Object> businessData = new HashMap<>();
        
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
     * 异步事务请求DTO
     */
    public static class AsyncTransactionRequest extends TransactionRequest {
        @NotBlank(message = "事务类型不能为空")
        private String transactionType;
        
        public String getTransactionType() {
            return transactionType;
        }
        
        public void setTransactionType(String transactionType) {
            this.transactionType = transactionType;
        }
    }
    
    /**
     * 批量事务请求DTO
     */
    public static class BatchTransactionRequest {
        @NotBlank(message = "事务类型不能为空")
        private String transactionType;
        
        private java.util.List<TransactionRequest> transactions = new java.util.ArrayList<>();
        
        public String getTransactionType() {
            return transactionType;
        }
        
        public void setTransactionType(String transactionType) {
            this.transactionType = transactionType;
        }
        
        public java.util.List<TransactionRequest> getTransactions() {
            return transactions;
        }
        
        public void setTransactions(java.util.List<TransactionRequest> transactions) {
            this.transactions = transactions;
        }
    }
}