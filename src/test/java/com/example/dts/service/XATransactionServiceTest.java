package com.example.dts.service;

import com.example.dts.model.TimestampEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import javax.transaction.UserTransaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * XA 事务服务测试
 * 
 * @author DTS Team
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class XATransactionServiceTest {

    @Mock
    private DataSource primaryDataSource;

    @Mock
    private DataSource secondaryDataSource;

    @Mock
    private UserTransaction userTransaction;

    @Mock
    private TimestampService timestampService;

    @Mock
    private Connection primaryConnection;

    @Mock
    private Connection secondaryConnection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private XATransactionService xaTransactionService;

    @BeforeEach
    void setUp() throws Exception {
        xaTransactionService = new XATransactionService(
            primaryDataSource, secondaryDataSource, userTransaction, timestampService);

        // Mock connections
        when(primaryDataSource.getConnection()).thenReturn(primaryConnection);
        when(secondaryDataSource.getConnection()).thenReturn(secondaryConnection);
        when(primaryConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(secondaryConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
    }

    @Test
    void testExecuteXATransaction_Success() throws Exception {
        // Arrange
        String businessType = "ORDER_PAYMENT";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("orderId", "12345");
        businessData.put("amount", 100.0);

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Act
        Map<String, Object> result = xaTransactionService.executeXATransaction(businessType, businessData);

        // Assert
        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals("XA", result.get("transactionType"));
        assertNotNull(result.get("transactionId"));
        assertNotNull(result.get("timestamp"));
        assertNotNull(result.get("duration"));

        // Verify transaction operations
        verify(userTransaction).begin();
        verify(userTransaction).commit();
        verify(timestampService).generateTimestampEvent("XA_TRANSACTION", businessType, businessData);
    }

    @Test
    void testExecuteXATransaction_Rollback() throws Exception {
        // Arrange
        String businessType = "INVALID_PAYMENT";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("amount", -100.0); // Invalid amount

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Simulate exception during processing
        when(preparedStatement.executeUpdate()).thenThrow(new RuntimeException("Database error"));

        // Act
        Map<String, Object> result = xaTransactionService.executeXATransaction(businessType, businessData);

        // Assert
        assertNotNull(result);
        assertEquals("ROLLBACK", result.get("status"));
        assertNotNull(result.get("error"));

        // Verify rollback was called
        verify(userTransaction).begin();
        verify(userTransaction).rollback();
    }

    @Test
    void testExecuteXATransactionAsync_Success() throws Exception {
        // Arrange
        String businessType = "INVENTORY_UPDATE";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("productId", "P001");
        businessData.put("quantity", 50);

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Act
        CompletableFuture<Map<String, Object>> future = 
            xaTransactionService.executeXATransactionAsync(businessType, businessData);

        // Assert
        assertNotNull(future);
        Map<String, Object> result = future.get();
        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals("XA", result.get("transactionType"));
    }

    @Test
    void testExecuteBatchXATransactions_Success() throws Exception {
        // Arrange
        List<Map<String, Object>> transactions = List.of(
            Map.of("businessType", "ORDER_PAYMENT", "businessData", Map.of("orderId", "001", "amount", 100.0)),
            Map.of("businessType", "INVENTORY_UPDATE", "businessData", Map.of("productId", "P001", "quantity", 10)),
            Map.of("businessType", "USER_REGISTRATION", "businessData", Map.of("username", "user1", "email", "user1@test.com"))
        );

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Act
        List<Map<String, Object>> results = xaTransactionService.executeBatchXATransactions(transactions);

        // Assert
        assertNotNull(results);
        assertEquals(3, results.size());
        
        for (Map<String, Object> result : results) {
            assertEquals("SUCCESS", result.get("status"));
            assertEquals("XA", result.get("transactionType"));
        }

        // Verify each transaction was processed
        verify(userTransaction, times(3)).begin();
        verify(userTransaction, times(3)).commit();
    }

    @Test
    void testExecuteBatchXATransactions_PartialFailure() throws Exception {
        // Arrange
        List<Map<String, Object>> transactions = List.of(
            Map.of("businessType", "ORDER_PAYMENT", "businessData", Map.of("orderId", "001", "amount", 100.0)),
            Map.of("businessType", "INVALID_TYPE", "businessData", Map.of("invalid", "data"))
        );

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Simulate failure for second transaction
        when(preparedStatement.executeUpdate())
            .thenReturn(1) // First transaction succeeds
            .thenThrow(new RuntimeException("Invalid business type")); // Second fails

        // Act
        List<Map<String, Object>> results = xaTransactionService.executeBatchXATransactions(transactions);

        // Assert
        assertNotNull(results);
        assertEquals(2, results.size());
        
        assertEquals("SUCCESS", results.get(0).get("status"));
        assertEquals("ERROR", results.get(1).get("status"));
        assertNotNull(results.get(1).get("error"));
    }

    @Test
    void testQueryXATransactionLogs_Success() throws Exception {
        // Arrange
        String transactionId = "XA_TEST_123";
        
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getLong("id")).thenReturn(1L, 2L);
        when(resultSet.getString("transaction_id")).thenReturn(transactionId, transactionId);
        when(resultSet.getString("event_type")).thenReturn("XA_TRANSACTION", "XA_TRANSACTION");
        when(resultSet.getString("business_type")).thenReturn("ORDER_PAYMENT", "ORDER_PAYMENT");
        when(resultSet.getString("status")).thenReturn("PROCESSING", "COMPLETED");
        when(resultSet.getString("data")).thenReturn("{\"orderId\":\"001\"}", "{\"orderId\":\"001\"}");
        when(resultSet.getTimestamp("created_at")).thenReturn(new java.sql.Timestamp(System.currentTimeMillis()));

        // Act
        List<Map<String, Object>> logs = xaTransactionService.queryXATransactionLogs(transactionId);

        // Assert
        assertNotNull(logs);
        assertEquals(2, logs.size());
        
        Map<String, Object> firstLog = logs.get(0);
        assertEquals(transactionId, firstLog.get("transactionId"));
        assertEquals("XA_TRANSACTION", firstLog.get("eventType"));
        assertEquals("ORDER_PAYMENT", firstLog.get("businessType"));
    }

    @Test
    void testGetXATransactionStatistics_Success() throws Exception {
        // Arrange
        when(resultSet.next())
            .thenReturn(true, false) // For total count
            .thenReturn(true, true, false) // For status counts
            .thenReturn(true, false); // For business type counts
        
        when(resultSet.getInt("total_count")).thenReturn(100);
        when(resultSet.getString("status")).thenReturn("PROCESSING", "COMPLETED");
        when(resultSet.getInt("count")).thenReturn(30, 70, 50);
        when(resultSet.getString("business_type")).thenReturn("ORDER_PAYMENT");

        // Act
        Map<String, Object> statistics = xaTransactionService.getXATransactionStatistics();

        // Assert
        assertNotNull(statistics);
        assertEquals(100, statistics.get("totalTransactions"));
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> statusCounts = (Map<String, Integer>) statistics.get("statusCounts");
        assertNotNull(statusCounts);
        assertEquals(30, statusCounts.get("PROCESSING"));
        assertEquals(70, statusCounts.get("COMPLETED"));
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> typeCounts = (Map<String, Integer>) statistics.get("businessTypeCounts");
        assertNotNull(typeCounts);
        assertEquals(50, typeCounts.get("ORDER_PAYMENT"));
    }

    @Test
    void testProcessOrderPayment_ValidAmount() throws Exception {
        // Arrange
        String businessType = "ORDER_PAYMENT";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("orderId", "12345");
        businessData.put("amount", 100.0);

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Act
        Map<String, Object> result = xaTransactionService.executeXATransaction(businessType, businessData);

        // Assert
        assertEquals("SUCCESS", result.get("status"));
    }

    @Test
    void testProcessOrderPayment_InvalidAmount() throws Exception {
        // Arrange
        String businessType = "ORDER_PAYMENT";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("orderId", "12345");
        businessData.put("amount", -100.0); // Invalid negative amount

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Mock the business logic to throw exception for invalid amount
        when(preparedStatement.executeUpdate()).thenAnswer(invocation -> {
            // Simulate the business logic validation
            throw new RuntimeException("Invalid payment amount: -100.0");
        });

        // Act
        Map<String, Object> result = xaTransactionService.executeXATransaction(businessType, businessData);

        // Assert
        assertEquals("ROLLBACK", result.get("status"));
        assertNotNull(result.get("error"));
        assertTrue(result.get("error").toString().contains("Invalid payment amount"));
    }

    @Test
    void testProcessInventoryUpdate_ValidQuantity() throws Exception {
        // Arrange
        String businessType = "INVENTORY_UPDATE";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("productId", "P001");
        businessData.put("quantity", 50);

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Act
        Map<String, Object> result = xaTransactionService.executeXATransaction(businessType, businessData);

        // Assert
        assertEquals("SUCCESS", result.get("status"));
    }

    @Test
    void testProcessUserRegistration_ValidData() throws Exception {
        // Arrange
        String businessType = "USER_REGISTRATION";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("username", "testuser");
        businessData.put("email", "test@example.com");

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Act
        Map<String, Object> result = xaTransactionService.executeXATransaction(businessType, businessData);

        // Assert
        assertEquals("SUCCESS", result.get("status"));
    }

    @Test
    void testProcessUserRegistration_MissingData() throws Exception {
        // Arrange
        String businessType = "USER_REGISTRATION";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("username", "testuser");
        // Missing email

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Mock the business logic to throw exception for missing data
        when(preparedStatement.executeUpdate()).thenAnswer(invocation -> {
            throw new RuntimeException("Missing required user information");
        });

        // Act
        Map<String, Object> result = xaTransactionService.executeXATransaction(businessType, businessData);

        // Assert
        assertEquals("ROLLBACK", result.get("status"));
        assertNotNull(result.get("error"));
        assertTrue(result.get("error").toString().contains("Missing required user information"));
    }

    private TimestampEvent createMockTimestampEvent() {
        TimestampEvent event = new TimestampEvent();
        event.setEventId("TEST_EVENT_" + System.currentTimeMillis());
        event.setEventType("XA_TRANSACTION");
        event.setBusinessType("TEST_BUSINESS");
        event.setTimestamp(System.currentTimeMillis());
        event.setLamportClock(1L);
        event.setNodeId("test-node");
        return event;
    }
}