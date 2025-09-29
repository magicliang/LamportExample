package com.example.dts.service;

import com.example.dts.model.TimestampEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import javax.transaction.*;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JTA 事务服务
 * 提供高级的 JTA 事务管理功能
 * 
 * @author DTS Team
 */
@Service
public class JTATransactionService {

    private static final Logger logger = LoggerFactory.getLogger(JTATransactionService.class);

    private final DataSource primaryDataSource;
    private final DataSource secondaryDataSource;
    private final TransactionManager transactionManager;
    private final UserTransaction userTransaction;
    private final TimestampService timestampService;
    
    // 事务状态跟踪
    private final Map<String, TransactionStatus> transactionStatusMap = new ConcurrentHashMap<>();
    private final AtomicLong transactionCounter = new AtomicLong(0);

    public JTATransactionService(
            @Qualifier("primaryXADataSource") DataSource primaryDataSource,
            @Qualifier("secondaryXADataSource") DataSource secondaryDataSource,
            TransactionManager transactionManager,
            UserTransaction userTransaction,
            TimestampService timestampService) {
        this.primaryDataSource = primaryDataSource;
        this.secondaryDataSource = secondaryDataSource;
        this.transactionManager = transactionManager;
        this.userTransaction = userTransaction;
        this.timestampService = timestampService;
    }

    /**
     * 事务状态枚举
     */
    public enum TransactionStatus {
        ACTIVE, PREPARING, PREPARED, COMMITTING, COMMITTED, ROLLING_BACK, ROLLED_BACK, UNKNOWN
    }

    /**
     * 执行 JTA 分布式事务（手动管理）
     */
    public Map<String, Object> executeJTATransaction(String businessType, Map<String, Object> businessData) {
        String transactionId = "JTA_" + transactionCounter.incrementAndGet() + "_" + System.currentTimeMillis();
        logger.info("Starting JTA transaction: id={}, type={}", transactionId, businessType);
        
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        Transaction transaction = null;
        
        try {
            // 开始事务
            transactionManager.begin();
            transaction = transactionManager.getTransaction();
            transactionStatusMap.put(transactionId, TransactionStatus.ACTIVE);
            
            // 生成时间戳事件
            TimestampEvent event = timestampService.generateTimestampEvent(
                "JTA_TRANSACTION", businessType, businessData);
            event.setEventId(transactionId);
            
            // 注册同步器监听事务状态变化
            transaction.registerSynchronization(new TransactionSynchronization(transactionId));
            
            // 执行业务操作
            List<String> operationResults = new ArrayList<>();
            
            // 操作1：在主数据源执行
            String primaryResult = executeJTAOperationOnPrimary(event, businessData);
            operationResults.add(primaryResult);
            
            // 操作2：在辅助数据源执行
            String secondaryResult = executeJTAOperationOnSecondary(event, businessData);
            operationResults.add(secondaryResult);
            
            // 操作3：执行复杂业务逻辑
            String businessResult = executeComplexBusinessLogic(businessType, businessData);
            operationResults.add(businessResult);
            
            // 准备提交
            transactionStatusMap.put(transactionId, TransactionStatus.PREPARING);
            
            // 提交事务
            transactionStatusMap.put(transactionId, TransactionStatus.COMMITTING);
            transactionManager.commit();
            transactionStatusMap.put(transactionId, TransactionStatus.COMMITTED);
            
            long duration = System.currentTimeMillis() - startTime;
            
            result.put("status", "SUCCESS");
            result.put("transactionId", transactionId);
            result.put("timestamp", event.getTimestamp());
            result.put("operationResults", operationResults);
            result.put("duration", duration);
            result.put("transactionType", "JTA");
            result.put("finalStatus", TransactionStatus.COMMITTED);
            
            logger.info("JTA transaction completed successfully: id={}, duration={}ms", 
                       transactionId, duration);
            
        } catch (Exception e) {
            logger.error("JTA transaction failed: id={}", transactionId, e);
            
            try {
                if (transaction != null) {
                    transactionStatusMap.put(transactionId, TransactionStatus.ROLLING_BACK);
                    transactionManager.rollback();
                    transactionStatusMap.put(transactionId, TransactionStatus.ROLLED_BACK);
                }
                
                result.put("status", "ROLLBACK");
                result.put("transactionId", transactionId);
                result.put("error", e.getMessage());
                result.put("finalStatus", TransactionStatus.ROLLED_BACK);
                
            } catch (Exception rollbackException) {
                logger.error("JTA transaction rollback failed: id={}", transactionId, rollbackException);
                transactionStatusMap.put(transactionId, TransactionStatus.UNKNOWN);
                result.put("status", "ROLLBACK_FAILED");
                result.put("error", rollbackException.getMessage());
                result.put("finalStatus", TransactionStatus.UNKNOWN);
            }
        }
        
        return result;
    }

    /**
     * 在主数据源执行 JTA 操作
     */
    private String executeJTAOperationOnPrimary(TimestampEvent event, Map<String, Object> businessData) 
            throws SQLException {
        logger.debug("Executing JTA operation on primary data source");
        
        try (Connection conn = primaryDataSource.getConnection()) {
            // 创建 JTA 事务日志表
            createJTALogTableIfNotExists(conn);
            
            // 插入事务开始日志
            String insertSql = "INSERT INTO jta_transaction_log (transaction_id, phase, operation, " +
                              "data_source, status, data, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, event.getEventId());
                stmt.setString(2, "EXECUTE");
                stmt.setString(3, "PRIMARY_OPERATION");
                stmt.setString(4, "PRIMARY");
                stmt.setString(5, "SUCCESS");
                stmt.setString(6, businessData.toString());
                stmt.setTimestamp(7, new java.sql.Timestamp(System.currentTimeMillis()));
                
                int rows = stmt.executeUpdate();
                logger.debug("Inserted {} JTA log rows into primary data source", rows);
                
                // 模拟一些业务操作
                performPrimaryBusinessOperation(conn, event, businessData);
                
                return "PRIMARY_JTA_SUCCESS_" + rows;
            }
        }
    }

    /**
     * 在辅助数据源执行 JTA 操作
     */
    private String executeJTAOperationOnSecondary(TimestampEvent event, Map<String, Object> businessData) 
            throws SQLException {
        logger.debug("Executing JTA operation on secondary data source");
        
        try (Connection conn = secondaryDataSource.getConnection()) {
            // 创建 JTA 事务日志表
            createJTALogTableIfNotExists(conn);
            
            // 插入事务日志
            String insertSql = "INSERT INTO jta_transaction_log (transaction_id, phase, operation, " +
                              "data_source, status, data, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, event.getEventId());
                stmt.setString(2, "EXECUTE");
                stmt.setString(3, "SECONDARY_OPERATION");
                stmt.setString(4, "SECONDARY");
                stmt.setString(5, "SUCCESS");
                stmt.setString(6, businessData.toString());
                stmt.setTimestamp(7, new java.sql.Timestamp(System.currentTimeMillis()));
                
                int rows = stmt.executeUpdate();
                logger.debug("Inserted {} JTA log rows into secondary data source", rows);
                
                // 模拟一些业务操作
                performSecondaryBusinessOperation(conn, event, businessData);
                
                return "SECONDARY_JTA_SUCCESS_" + rows;
            }
        }
    }

    /**
     * 创建 JTA 事务日志表
     */
    private void createJTALogTableIfNotExists(Connection conn) throws SQLException {
        String createTableSql = "CREATE TABLE IF NOT EXISTS jta_transaction_log (" +
                               "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                               "transaction_id VARCHAR(255) NOT NULL, " +
                               "phase VARCHAR(50) NOT NULL, " +
                               "operation VARCHAR(100) NOT NULL, " +
                               "data_source VARCHAR(50) NOT NULL, " +
                               "status VARCHAR(50) NOT NULL, " +
                               "data TEXT, " +
                               "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                               "INDEX idx_transaction_id (transaction_id), " +
                               "INDEX idx_phase (phase)" +
                               ")";
        
        try (PreparedStatement stmt = conn.prepareStatement(createTableSql)) {
            stmt.executeUpdate();
        }
    }

    /**
     * 执行主数据源业务操作
     */
    private void performPrimaryBusinessOperation(Connection conn, TimestampEvent event, 
                                               Map<String, Object> businessData) throws SQLException {
        // 模拟复杂的业务操作
        String businessSql = "INSERT INTO timestamp_events (event_id, event_type, business_type, " +
                            "timestamp_value, lamport_clock, vector_clock, version_vector, " +
                            "node_id, business_data, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE business_data = VALUES(business_data)";
        
        try (PreparedStatement stmt = conn.prepareStatement(businessSql)) {
            stmt.setString(1, event.getEventId());
            stmt.setString(2, event.getEventType());
            stmt.setString(3, event.getBusinessType());
            stmt.setLong(4, event.getTimestamp());
            stmt.setLong(5, event.getLamportClock());
            stmt.setString(6, event.getVectorClock());
            stmt.setString(7, event.getVersionVector());
            stmt.setString(8, event.getNodeId());
            stmt.setString(9, businessData.toString());
            stmt.setTimestamp(10, new java.sql.Timestamp(System.currentTimeMillis()));
            
            stmt.executeUpdate();
        }
    }

    /**
     * 执行辅助数据源业务操作
     */
    private void performSecondaryBusinessOperation(Connection conn, TimestampEvent event, 
                                                 Map<String, Object> businessData) throws SQLException {
        // 创建业务数据表
        String createBusinessTableSql = "CREATE TABLE IF NOT EXISTS business_operations (" +
                                       "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                                       "transaction_id VARCHAR(255) NOT NULL, " +
                                       "operation_type VARCHAR(100) NOT NULL, " +
                                       "operation_data TEXT, " +
                                       "status VARCHAR(50) NOT NULL, " +
                                       "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                                       ")";
        
        try (PreparedStatement createStmt = conn.prepareStatement(createBusinessTableSql)) {
            createStmt.executeUpdate();
        }
        
        // 插入业务操作记录
        String insertBusinessSql = "INSERT INTO business_operations (transaction_id, operation_type, " +
                                  "operation_data, status) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(insertBusinessSql)) {
            stmt.setString(1, event.getEventId());
            stmt.setString(2, event.getBusinessType());
            stmt.setString(3, businessData.toString());
            stmt.setString(4, "COMPLETED");
            
            stmt.executeUpdate();
        }
    }

    /**
     * 执行复杂业务逻辑
     */
    private String executeComplexBusinessLogic(String businessType, Map<String, Object> businessData) {
        logger.debug("Executing complex business logic for type: {}", businessType);
        
        // 模拟复杂的业务处理
        try {
            Thread.sleep(50); // 模拟处理时间
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Business logic processing interrupted", e);
        }
        
        // 根据业务类型执行不同的逻辑
        switch (businessType) {
            case "COMPLEX_ORDER":
                return processComplexOrder(businessData);
            case "BATCH_UPDATE":
                return processBatchUpdate(businessData);
            case "DATA_MIGRATION":
                return processDataMigration(businessData);
            default:
                return "UNKNOWN_BUSINESS_TYPE_PROCESSED";
        }
    }

    /**
     * 处理复杂订单
     */
    private String processComplexOrder(Map<String, Object> businessData) {
        logger.debug("Processing complex order: {}", businessData);
        
        // 验证订单数据
        if (!businessData.containsKey("orderId") || !businessData.containsKey("items")) {
            throw new RuntimeException("Invalid order data");
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) businessData.get("items");
        
        double totalAmount = 0.0;
        for (Map<String, Object> item : items) {
            Double price = (Double) item.get("price");
            Integer quantity = (Integer) item.get("quantity");
            totalAmount += price * quantity;
        }
        
        return "COMPLEX_ORDER_PROCESSED_AMOUNT_" + totalAmount;
    }

    /**
     * 处理批量更新
     */
    private String processBatchUpdate(Map<String, Object> businessData) {
        logger.debug("Processing batch update: {}", businessData);
        
        if (!businessData.containsKey("updates")) {
            throw new RuntimeException("No updates provided");
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> updates = (List<Map<String, Object>>) businessData.get("updates");
        
        return "BATCH_UPDATE_PROCESSED_COUNT_" + updates.size();
    }

    /**
     * 处理数据迁移
     */
    private String processDataMigration(Map<String, Object> businessData) {
        logger.debug("Processing data migration: {}", businessData);
        
        if (!businessData.containsKey("sourceTable") || !businessData.containsKey("targetTable")) {
            throw new RuntimeException("Missing migration table information");
        }
        
        return "DATA_MIGRATION_PROCESSED";
    }

    /**
     * 查询 JTA 事务状态
     */
    public Map<String, Object> getJTATransactionStatus(String transactionId) {
        Map<String, Object> status = new HashMap<>();
        status.put("transactionId", transactionId);
        status.put("currentStatus", transactionStatusMap.getOrDefault(transactionId, TransactionStatus.UNKNOWN));
        status.put("timestamp", System.currentTimeMillis());
        
        return status;
    }

    /**
     * 获取所有活跃的 JTA 事务
     */
    public List<Map<String, Object>> getActiveJTATransactions() {
        List<Map<String, Object>> activeTransactions = new ArrayList<>();
        
        for (Map.Entry<String, TransactionStatus> entry : transactionStatusMap.entrySet()) {
            if (entry.getValue() == TransactionStatus.ACTIVE || 
                entry.getValue() == TransactionStatus.PREPARING ||
                entry.getValue() == TransactionStatus.COMMITTING) {
                
                Map<String, Object> transaction = new HashMap<>();
                transaction.put("transactionId", entry.getKey());
                transaction.put("status", entry.getValue());
                activeTransactions.add(transaction);
            }
        }
        
        return activeTransactions;
    }

    /**
     * 清理已完成的事务状态
     */
    public void cleanupCompletedTransactions() {
        transactionStatusMap.entrySet().removeIf(entry -> 
            entry.getValue() == TransactionStatus.COMMITTED || 
            entry.getValue() == TransactionStatus.ROLLED_BACK);
        
        logger.info("Cleaned up completed transactions. Active count: {}", transactionStatusMap.size());
    }

    /**
     * 事务同步器
     */
    private class TransactionSynchronization implements Synchronization {
        private final String transactionId;
        
        public TransactionSynchronization(String transactionId) {
            this.transactionId = transactionId;
        }
        
        @Override
        public void beforeCompletion() {
            logger.debug("JTA transaction before completion: {}", transactionId);
            transactionStatusMap.put(transactionId, TransactionStatus.PREPARING);
        }
        
        @Override
        public void afterCompletion(int status) {
            logger.debug("JTA transaction after completion: {}, status: {}", transactionId, status);
            
            switch (status) {
                case Status.STATUS_COMMITTED:
                    transactionStatusMap.put(transactionId, TransactionStatus.COMMITTED);
                    break;
                case Status.STATUS_ROLLEDBACK:
                    transactionStatusMap.put(transactionId, TransactionStatus.ROLLED_BACK);
                    break;
                default:
                    transactionStatusMap.put(transactionId, TransactionStatus.UNKNOWN);
            }
        }
    }
}