package com.example.dts.service;

import com.example.dts.model.TimestampEvent;
import io.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 分布式事务服务
 * 集成Seata分布式事务管理，确保跨服务的数据一致性
 * 
 * @author DTS Team
 */
@Service
public class DistributedTransactionService {
    
    private static final Logger logger = LoggerFactory.getLogger(DistributedTransactionService.class);
    
    private final TimestampService timestampService;
    
    @Value("${dts.node.id}")
    private String nodeId;
    
    @Value("${dts.transaction.timeout:30000}")
    private int transactionTimeout;
    
    @Value("${dts.transaction.retry-count:3}")
    private int retryCount;
    
    public DistributedTransactionService(TimestampService timestampService) {
        this.timestampService = timestampService;
    }
    
    /**
     * 执行分布式事务 - AT模式
     * 自动管理事务的提交和回滚
     */
    @GlobalTransactional(name = "dts-at-transaction", timeoutMills = 30000, rollbackFor = Exception.class)
    public Map<String, Object> executeATTransaction(String businessType, Map<String, Object> businessData) {
        logger.info("Starting AT transaction: type={}, node={}", businessType, nodeId);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. 创建事务开始事件
            Map<String, Object> startEventData = new HashMap<>();
            startEventData.put("transactionType", "AT");
            startEventData.put("businessType", businessType);
            startEventData.put("businessData", businessData);
            startEventData.put("phase", "START");
            
            TimestampEvent startEvent = timestampService.createEvent("TRANSACTION_START", startEventData);
            
            // 2. 执行业务逻辑
            Map<String, Object> businessResult = executeBusinessLogic(businessType, businessData);
            
            // 3. 创建事务成功事件
            Map<String, Object> successEventData = new HashMap<>();
            successEventData.put("transactionType", "AT");
            successEventData.put("businessType", businessType);
            successEventData.put("businessResult", businessResult);
            successEventData.put("phase", "SUCCESS");
            successEventData.put("startEventId", startEvent.getId());
            
            TimestampEvent successEvent = timestampService.createEvent("TRANSACTION_SUCCESS", successEventData);
            
            result.put("success", true);
            result.put("transactionType", "AT");
            result.put("startEventId", startEvent.getId());
            result.put("successEventId", successEvent.getId());
            result.put("businessResult", businessResult);
            result.put("completedAt", System.currentTimeMillis());
            
            logger.info("AT transaction completed successfully: type={}, node={}", businessType, nodeId);
            
        } catch (Exception e) {
            logger.error("AT transaction failed: type={}, node={}, error={}", businessType, nodeId, e.getMessage());
            
            // 创建事务失败事件
            try {
                Map<String, Object> failEventData = new HashMap<>();
                failEventData.put("transactionType", "AT");
                failEventData.put("businessType", businessType);
                failEventData.put("error", e.getMessage());
                failEventData.put("phase", "ROLLBACK");
                
                timestampService.createEvent("TRANSACTION_ROLLBACK", failEventData);
            } catch (Exception eventException) {
                logger.error("Failed to create rollback event: {}", eventException.getMessage());
            }
            
            result.put("success", false);
            result.put("error", e.getMessage());
            throw e; // 重新抛出异常以触发Seata回滚
        }
        
        return result;
    }
    
    /**
     * 执行分布式事务 - TCC模式
     * 手动管理Try-Confirm-Cancel三个阶段
     */
    @GlobalTransactional(name = "dts-tcc-transaction", timeoutMills = 30000, rollbackFor = Exception.class)
    public Map<String, Object> executeTCCTransaction(String businessType, Map<String, Object> businessData) {
        logger.info("Starting TCC transaction: type={}, node={}", businessType, nodeId);
        
        Map<String, Object> result = new HashMap<>();
        String transactionId = generateTransactionId();
        
        try {
            // 1. Try阶段 - 预留资源
            Map<String, Object> tryResult = tryPhase(transactionId, businessType, businessData);
            
            // 2. Confirm阶段 - 确认提交
            Map<String, Object> confirmResult = confirmPhase(transactionId, businessType, tryResult);
            
            result.put("success", true);
            result.put("transactionType", "TCC");
            result.put("transactionId", transactionId);
            result.put("tryResult", tryResult);
            result.put("confirmResult", confirmResult);
            result.put("completedAt", System.currentTimeMillis());
            
            logger.info("TCC transaction completed successfully: id={}, type={}, node={}", 
                       transactionId, businessType, nodeId);
            
        } catch (Exception e) {
            logger.error("TCC transaction failed: id={}, type={}, node={}, error={}", 
                        transactionId, businessType, nodeId, e.getMessage());
            
            // Cancel阶段 - 回滚操作
            try {
                cancelPhase(transactionId, businessType, businessData);
            } catch (Exception cancelException) {
                logger.error("TCC cancel phase failed: id={}, error={}", transactionId, cancelException.getMessage());
            }
            
            result.put("success", false);
            result.put("transactionId", transactionId);
            result.put("error", e.getMessage());
            throw e;
        }
        
        return result;
    }
    
    /**
     * 执行分布式事务 - SAGA模式
     * 基于状态机的长事务处理
     */
    @GlobalTransactional(name = "dts-saga-transaction", timeoutMills = 60000, rollbackFor = Exception.class)
    public Map<String, Object> executeSAGATransaction(String businessType, Map<String, Object> businessData) {
        logger.info("Starting SAGA transaction: type={}, node={}", businessType, nodeId);
        
        Map<String, Object> result = new HashMap<>();
        String sagaId = generateTransactionId();
        
        try {
            // 定义SAGA步骤
            java.util.List<Map<String, Object>> sagaSteps = defineSagaSteps(businessType, businessData);
            
            // 执行SAGA步骤
            java.util.List<Map<String, Object>> stepResults = new java.util.ArrayList<>();
            
            for (int i = 0; i < sagaSteps.size(); i++) {
                Map<String, Object> step = sagaSteps.get(i);
                try {
                    Map<String, Object> stepResult = executeSagaStep(sagaId, i, step);
                    stepResults.add(stepResult);
                    
                    logger.debug("SAGA step {} completed: id={}, step={}", i, sagaId, step.get("name"));
                    
                } catch (Exception stepException) {
                    logger.error("SAGA step {} failed: id={}, error={}", i, sagaId, stepException.getMessage());
                    
                    // 执行补偿操作
                    compensateSagaSteps(sagaId, stepResults);
                    throw stepException;
                }
            }
            
            result.put("success", true);
            result.put("transactionType", "SAGA");
            result.put("sagaId", sagaId);
            result.put("stepResults", stepResults);
            result.put("completedAt", System.currentTimeMillis());
            
            logger.info("SAGA transaction completed successfully: id={}, type={}, node={}", 
                       sagaId, businessType, nodeId);
            
        } catch (Exception e) {
            logger.error("SAGA transaction failed: id={}, type={}, node={}, error={}", 
                        sagaId, businessType, nodeId, e.getMessage());
            
            result.put("success", false);
            result.put("sagaId", sagaId);
            result.put("error", e.getMessage());
            throw e;
        }
        
        return result;
    }
    
    /**
     * 异步执行分布式事务
     */
    public CompletableFuture<Map<String, Object>> executeTransactionAsync(
            String transactionType, String businessType, Map<String, Object> businessData) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                switch (transactionType.toUpperCase()) {
                    case "AT":
                        return executeATTransaction(businessType, businessData);
                    case "TCC":
                        return executeTCCTransaction(businessType, businessData);
                    case "SAGA":
                        return executeSAGATransaction(businessType, businessData);
                    default:
                        throw new IllegalArgumentException("Unsupported transaction type: " + transactionType);
                }
            } catch (Exception e) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", e.getMessage());
                return errorResult;
            }
        }).orTimeout(transactionTimeout, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 执行业务逻辑（模拟）
     */
    @Transactional
    protected Map<String, Object> executeBusinessLogic(String businessType, Map<String, Object> businessData) {
        Map<String, Object> result = new HashMap<>();
        
        // 模拟业务处理时间
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 根据业务类型执行不同逻辑
        switch (businessType) {
            case "ORDER_CREATE":
                result.put("orderId", generateTransactionId());
                result.put("status", "CREATED");
                break;
            case "PAYMENT_PROCESS":
                result.put("paymentId", generateTransactionId());
                result.put("status", "PROCESSED");
                break;
            case "INVENTORY_UPDATE":
                result.put("inventoryId", generateTransactionId());
                result.put("status", "UPDATED");
                break;
            default:
                result.put("businessType", businessType);
                result.put("status", "COMPLETED");
        }
        
        result.put("processedAt", System.currentTimeMillis());
        result.put("nodeId", nodeId);
        
        return result;
    }
    
    /**
     * TCC Try阶段
     */
    private Map<String, Object> tryPhase(String transactionId, String businessType, Map<String, Object> businessData) {
        logger.debug("TCC Try phase: id={}, type={}", transactionId, businessType);
        
        Map<String, Object> tryEventData = new HashMap<>();
        tryEventData.put("transactionId", transactionId);
        tryEventData.put("transactionType", "TCC");
        tryEventData.put("businessType", businessType);
        tryEventData.put("phase", "TRY");
        tryEventData.put("businessData", businessData);
        
        timestampService.createEvent("TCC_TRY", tryEventData);
        
        // 模拟资源预留
        Map<String, Object> result = new HashMap<>();
        result.put("resourceId", generateTransactionId());
        result.put("reserved", true);
        result.put("tryAt", System.currentTimeMillis());
        
        return result;
    }
    
    /**
     * TCC Confirm阶段
     */
    private Map<String, Object> confirmPhase(String transactionId, String businessType, Map<String, Object> tryResult) {
        logger.debug("TCC Confirm phase: id={}, type={}", transactionId, businessType);
        
        Map<String, Object> confirmEventData = new HashMap<>();
        confirmEventData.put("transactionId", transactionId);
        confirmEventData.put("transactionType", "TCC");
        confirmEventData.put("businessType", businessType);
        confirmEventData.put("phase", "CONFIRM");
        confirmEventData.put("tryResult", tryResult);
        
        timestampService.createEvent("TCC_CONFIRM", confirmEventData);
        
        // 模拟确认提交
        Map<String, Object> result = new HashMap<>();
        result.put("confirmed", true);
        result.put("confirmAt", System.currentTimeMillis());
        
        return result;
    }
    
    /**
     * TCC Cancel阶段
     */
    private void cancelPhase(String transactionId, String businessType, Map<String, Object> businessData) {
        logger.debug("TCC Cancel phase: id={}, type={}", transactionId, businessType);
        
        Map<String, Object> cancelEventData = new HashMap<>();
        cancelEventData.put("transactionId", transactionId);
        cancelEventData.put("transactionType", "TCC");
        cancelEventData.put("businessType", businessType);
        cancelEventData.put("phase", "CANCEL");
        cancelEventData.put("businessData", businessData);
        
        timestampService.createEvent("TCC_CANCEL", cancelEventData);
    }
    
    /**
     * 定义SAGA步骤
     */
    private java.util.List<Map<String, Object>> defineSagaSteps(String businessType, Map<String, Object> businessData) {
        java.util.List<Map<String, Object>> steps = new java.util.ArrayList<>();
        
        // 根据业务类型定义不同的SAGA步骤
        switch (businessType) {
            case "ORDER_PROCESS":
                steps.add(createSagaStep("CREATE_ORDER", businessData));
                steps.add(createSagaStep("RESERVE_INVENTORY", businessData));
                steps.add(createSagaStep("PROCESS_PAYMENT", businessData));
                steps.add(createSagaStep("CONFIRM_ORDER", businessData));
                break;
            default:
                steps.add(createSagaStep("DEFAULT_STEP", businessData));
        }
        
        return steps;
    }
    
    /**
     * 创建SAGA步骤
     */
    private Map<String, Object> createSagaStep(String stepName, Map<String, Object> stepData) {
        Map<String, Object> step = new HashMap<>();
        step.put("name", stepName);
        step.put("data", stepData);
        step.put("compensationAction", stepName + "_COMPENSATE");
        return step;
    }
    
    /**
     * 执行SAGA步骤
     */
    private Map<String, Object> executeSagaStep(String sagaId, int stepIndex, Map<String, Object> step) {
        String stepName = (String) step.get("name");
        
        Map<String, Object> stepEventData = new HashMap<>();
        stepEventData.put("sagaId", sagaId);
        stepEventData.put("stepIndex", stepIndex);
        stepEventData.put("stepName", stepName);
        stepEventData.put("stepData", step.get("data"));
        
        timestampService.createEvent("SAGA_STEP", stepEventData);
        
        // 模拟步骤执行
        Map<String, Object> result = new HashMap<>();
        result.put("stepName", stepName);
        result.put("stepIndex", stepIndex);
        result.put("executedAt", System.currentTimeMillis());
        result.put("success", true);
        
        return result;
    }
    
    /**
     * 补偿SAGA步骤
     */
    private void compensateSagaSteps(String sagaId, java.util.List<Map<String, Object>> completedSteps) {
        logger.info("Compensating SAGA steps: id={}, steps={}", sagaId, completedSteps.size());
        
        // 逆序执行补偿操作
        for (int i = completedSteps.size() - 1; i >= 0; i--) {
            Map<String, Object> step = completedSteps.get(i);
            try {
                compensateSagaStep(sagaId, i, step);
            } catch (Exception e) {
                logger.error("SAGA compensation failed: id={}, step={}, error={}", sagaId, i, e.getMessage());
            }
        }
    }
    
    /**
     * 补偿单个SAGA步骤
     */
    private void compensateSagaStep(String sagaId, int stepIndex, Map<String, Object> step) {
        String stepName = (String) step.get("stepName");
        
        Map<String, Object> compensateEventData = new HashMap<>();
        compensateEventData.put("sagaId", sagaId);
        compensateEventData.put("stepIndex", stepIndex);
        compensateEventData.put("stepName", stepName);
        compensateEventData.put("action", "COMPENSATE");
        
        timestampService.createEvent("SAGA_COMPENSATE", compensateEventData);
        
        logger.debug("SAGA step compensated: id={}, step={}, name={}", sagaId, stepIndex, stepName);
    }
    
    /**
     * 生成事务ID
     */
    private String generateTransactionId() {
        return nodeId + "-" + System.currentTimeMillis() + "-" + 
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}