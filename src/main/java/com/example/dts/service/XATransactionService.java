package com.example.dts.service;

import com.example.dts.model.TimestampEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import javax.transaction.UserTransaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * XA 事务服务
 * 基于 JTA 实现的分布式事务管理
 * 
 * @author DTS Team
 */
@Service
public class XATransactionService {

    private static final Logger logger = LoggerFactory.getLogger(XATransactionService.class);

    private final DataSource primaryDataSource;
    private final DataSource secondaryDataSource;
    private final UserTransaction userTransaction;
    private final TimestampService timestampService;
    private final ExecutorService executorService;

    public XATransactionService(
            @Qualifier("primaryXADataSource") DataSource primaryDataSource,
            @Qualifier("secondaryXADataSource") DataSource secondaryDataSource,
            UserTransaction userTransaction,
            TimestampService timestampService) {
        this.primaryDataSource = primaryDataSource;
        this.secondaryDataSource = secondaryDataSource;
        this.userTransaction = userTransaction;
        this.timestampService = timestampService;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    /**
     * 执行 XA 分布式事务
     * 跨多个数据源的原子性操作
     */
    @Transactional
    public Map<String, Object> executeXATransaction(String businessType, Map<String, Object> businessData) {
        logger.info("Starting XA transaction: type={}", businessType);
        
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // 开始 JTA 事务
            userTransaction.begin();
            
            // 生成时间戳事件
            TimestampEvent event = timestampService.generateTimestampEvent(
                "XA_TRANSACTION", businessType, businessData);
            
            // 在主数据源执行操作
            String primaryResult = executeOnPrimaryDataSource(event, businessData);
            
            // 在辅助数据源执行操作
            String secondaryResult = executeOnSecondaryDataSource(event, businessData);
            
            // 模拟业务逻辑处理
            processBusinessLogic(businessType, businessData);
            
            // 提交事务
            userTransaction.commit();
            
            long duration = System.currentTimeMillis() - startTime;
            
            result.put("status", "SUCCESS");
            result.put("transactionId", event.getEventId());
            result.put("timestamp", event.getTimestamp());
            result.put("primaryResult", primaryResult);
            result.put("secondaryResult", secondaryResult);
            result.put("duration", duration);
            result.put("transactionType", "XA");
            
            logger.info("XA transaction completed successfully: id={}, duration={}ms", 
                       event.getEventId(), duration);
            
        } catch (Exception e) {
            logger.error("XA transaction failed", e);
            try {
                userTransaction.rollback();
                result.put("status", "ROLLBACK");
                result.put("error", e.getMessage());
            } catch (Exception rollbackException) {
                logger.error("XA transaction rollback failed", rollbackException);
                result.put("status", "ROLLBACK_FAILED");
                result.put("error", rollbackException.getMessage());
            }
        }
        
        return result;
    }

    /**
     * 在主数据源执行操作
     */
    private String executeOnPrimaryDataSource(TimestampEvent event, Map<String, Object> businessData) 
            throws SQLException {
        logger.debug("Executing operation on primary data source");
        
        try (Connection conn = primaryDataSource.getConnection()) {
            // 插入时间戳事件
            String insertSql = "INSERT INTO timestamp_events (event_id, event_type, business_type, " +
                              "timestamp_value, lamport_clock, vector_clock, version_vector, " +
                              "node_id, business_data, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
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
                
                int rows = stmt.executeUpdate();
                logger.debug("Inserted {} rows into primary data source", rows);
                
                return "PRIMARY_SUCCESS_" + rows;
            }
        }
    }

    /**
     * 在辅助数据源执行操作
     */
    private String executeOnSecondaryDataSource(TimestampEvent event, Map<String, Object> businessData) 
            throws SQLException {
        logger.debug("Executing operation on secondary data source");
        
        try (Connection conn = secondaryDataSource.getConnection()) {
            // 创建业务日志表（如果不存在）
            String createTableSql = "CREATE TABLE IF NOT EXISTS xa_transaction_log (" +
                                   "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                                   "transaction_id VARCHAR(255) NOT NULL, " +
                                   "event_type VARCHAR(100) NOT NULL, " +
                                   "business_type VARCHAR(100) NOT NULL, " +
                                   "status VARCHAR(50) NOT NULL, " +
                                   "data TEXT, " +
                                   "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                                   "INDEX idx_transaction_id (transaction_id)" +
                                   ")";
            
            try (PreparedStatement createStmt = conn.prepareStatement(createTableSql)) {
                createStmt.executeUpdate();
            }
            
            // 插入事务日志
            String insertSql = "INSERT INTO xa_transaction_log (transaction_id, event_type, " +
                              "business_type, status, data) VALUES (?, ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, event.getEventId());
                stmt.setString(2, event.getEventType());
                stmt.setString(3, event.getBusinessType());
                stmt.setString(4, "PROCESSING");
                stmt.setString(5, businessData.toString());
                
                int rows = stmt.executeUpdate();
                logger.debug("Inserted {} rows into secondary data source", rows);
                
                return "SECONDARY_SUCCESS_" + rows;
            }
        }
    }

    /**
     * 处理业务逻辑
     */
    private void processBusinessLogic(String businessType, Map<String, Object> businessData) {
        logger.debug("Processing business logic for type: {}", businessType);
        
        // 模拟业务处理时间
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Business logic processing interrupted", e);
        }
        
        // 根据业务类型执行不同的逻辑
        switch (businessType) {
            case "ORDER_PAYMENT":
                processOrderPayment(businessData);
                break;
            case "INVENTORY_UPDATE":
                processInventoryUpdate(businessData);
                break;
            case "USER_REGISTRATION":
                processUserRegistration(businessData);
                break;
            default:
                logger.warn("Unknown business type: {}", businessType);
        }
    }

    /**
     * 处理订单支付业务
     */
    private void processOrderPayment(Map<String, Object> businessData) {
        logger.debug("Processing order payment: {}", businessData);
        // 模拟订单支付逻辑
        if (businessData.containsKey("amount")) {
            Double amount = (Double) businessData.get("amount");
            if (amount <= 0) {
                throw new RuntimeException("Invalid payment amount: " + amount);
            }
        }
    }

    /**
     * 处理库存更新业务
     */
    private void processInventoryUpdate(Map<String, Object> businessData) {
        logger.debug("Processing inventory update: {}", businessData);
        // 模拟库存更新逻辑
        if (businessData.containsKey("quantity")) {
            Integer quantity = (Integer) businessData.get("quantity");
            if (quantity < 0) {
                throw new RuntimeException("Invalid inventory quantity: " + quantity);
            }
        }
    }

    /**
     * 处理用户注册业务
     */
    private void processUserRegistration(Map<String, Object> businessData) {
        logger.debug("Processing user registration: {}", businessData);
        // 模拟用户注册逻辑
        if (!businessData.containsKey("username") || !businessData.containsKey("email")) {
            throw new RuntimeException("Missing required user information");
        }
    }

    /**
     * 异步执行 XA 事务
     */
    public CompletableFuture<Map<String, Object>> executeXATransactionAsync(
            String businessType, Map<String, Object> businessData) {
        return CompletableFuture.supplyAsync(() -> 
            executeXATransaction(businessType, businessData), executorService);
    }

    /**
     * 批量执行 XA 事务
     */
    public List<Map<String, Object>> executeBatchXATransactions(
            List<Map<String, Object>> transactions) {
        logger.info("Executing batch XA transactions: count={}", transactions.size());
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Map<String, Object> transaction : transactions) {
            String businessType = (String) transaction.get("businessType");
            @SuppressWarnings("unchecked")
            Map<String, Object> businessData = (Map<String, Object>) transaction.get("businessData");
            
            try {
                Map<String, Object> result = executeXATransaction(businessType, businessData);
                results.add(result);
            } catch (Exception e) {
                logger.error("Batch XA transaction failed for type: {}", businessType, e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("status", "ERROR");
                errorResult.put("businessType", businessType);
                errorResult.put("error", e.getMessage());
                results.add(errorResult);
            }
        }
        
        return results;
    }

    /**
     * 查询 XA 事务日志
     */
    public List<Map<String, Object>> queryXATransactionLogs(String transactionId) {
        logger.debug("Querying XA transaction logs for: {}", transactionId);
        
        List<Map<String, Object>> logs = new ArrayList<>();
        
        try (Connection conn = secondaryDataSource.getConnection()) {
            String sql = "SELECT * FROM xa_transaction_log WHERE transaction_id = ? ORDER BY created_at DESC";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, transactionId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> log = new HashMap<>();
                        log.put("id", rs.getLong("id"));
                        log.put("transactionId", rs.getString("transaction_id"));
                        log.put("eventType", rs.getString("event_type"));
                        log.put("businessType", rs.getString("business_type"));
                        log.put("status", rs.getString("status"));
                        log.put("data", rs.getString("data"));
                        log.put("createdAt", rs.getTimestamp("created_at"));
                        logs.add(log);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query XA transaction logs", e);
        }
        
        return logs;
    }

    /**
     * 获取 XA 事务统计信息
     */
    public Map<String, Object> getXATransactionStatistics() {
        logger.debug("Getting XA transaction statistics");
        
        Map<String, Object> stats = new HashMap<>();
        
        try (Connection conn = secondaryDataSource.getConnection()) {
            // 统计总事务数
            String countSql = "SELECT COUNT(*) as total_count FROM xa_transaction_log";
            try (PreparedStatement stmt = conn.prepareStatement(countSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalTransactions", rs.getInt("total_count"));
                }
            }
            
            // 按状态统计
            String statusSql = "SELECT status, COUNT(*) as count FROM xa_transaction_log GROUP BY status";
            try (PreparedStatement stmt = conn.prepareStatement(statusSql);
                 ResultSet rs = stmt.executeQuery()) {
                Map<String, Integer> statusCounts = new HashMap<>();
                while (rs.next()) {
                    statusCounts.put(rs.getString("status"), rs.getInt("count"));
                }
                stats.put("statusCounts", statusCounts);
            }
            
            // 按业务类型统计
            String typeSql = "SELECT business_type, COUNT(*) as count FROM xa_transaction_log GROUP BY business_type";
            try (PreparedStatement stmt = conn.prepareStatement(typeSql);
                 ResultSet rs = stmt.executeQuery()) {
                Map<String, Integer> typeCounts = new HashMap<>();
                while (rs.next()) {
                    typeCounts.put(rs.getString("business_type"), rs.getInt("count"));
                }
                stats.put("businessTypeCounts", typeCounts);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to get XA transaction statistics", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
}