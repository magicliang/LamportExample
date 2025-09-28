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
import javax.transaction.Status;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JTA 事务服务测试
 * 
 * @author DTS Team
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class JTATransactionServiceTest {

    @Mock
    private DataSource primaryDataSource;

    @Mock
    private DataSource secondaryDataSource;

    @Mock
    private TransactionManager transactionManager;

    @Mock
    private UserTransaction userTransaction;

    @Mock
    private TimestampService timestampService;

    @Mock
    private Transaction transaction;

    @Mock
    private Connection primaryConnection;

    @Mock
    private Connection secondaryConnection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private JTATransactionService jtaTransactionService;

    @BeforeEach
    void setUp() throws Exception {
        jtaTransactionService = new JTATransactionService(
            primaryDataSource, secondaryDataSource, transactionManager, 
            userTransaction, timestampService);

        // Mock connections
        when(primaryDataSource.getConnection()).thenReturn(primaryConnection);
        when(secondaryDataSource.getConnection()).thenReturn(secondaryConnection);
        when(primaryConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(secondaryConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // Mock transaction manager
        when(transactionManager.getTransaction()).thenReturn(transaction);
    }

    @Test
    void testExecuteJTATransaction_Success() throws Exception {
        // Arrange
        String businessType = "COMPLEX_ORDER";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("orderId", "ORDER_001");
        businessData.put("items", List.of(
            Map.of("productId", "P001", "price", 50.0, "quantity", 2),
            Map.of("productId", "P002", "price", 30.0, "quantity", 1)
        ));

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Act
        Map<String, Object> result = jtaTransactionService.executeJTATransaction(businessType, businessData);

        // Assert
        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals("JTA", result.get("transactionType"));
        assertEquals(JTATransactionService.TransactionStatus.COMMITTED, result.get("finalStatus"));
        assertNotNull(result.get("transactionId"));
        assertNotNull(result.get("timestamp"));
        assertNotNull(result.get("duration"));
        assertNotNull(result.get("operationResults"));

        @SuppressWarnings("unchecked")
        List<String> operationResults = (List<String>) result.get("operationResults");
        assertEquals(3, operationResults.size()); // Primary, Secondary, Business logic

        // Verify transaction operations
        verify(transactionManager).begin();
        verify(transactionManager).commit();
        verify(transaction).registerSynchronization(any());
    }

    @Test
    void testExecuteJTATransaction_Rollback() throws Exception {
        // Arrange
        String businessType = "INVALID_ORDER";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("orderId", "INVALID_ORDER");

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Simulate exception during processing
        when(preparedStatement.executeUpdate()).thenThrow(new RuntimeException("Database constraint violation"));

        // Act
        Map<String, Object> result = jtaTransactionService.executeJTATransaction(businessType, businessData);

        // Assert
        assertNotNull(result);
        assertEquals("ROLLBACK", result.get("status"));
        assertEquals(JTATransactionService.TransactionStatus.ROLLED_BACK, result.get("finalStatus"));
        assertNotNull(result.get("error"));
        assertTrue(result.get("error").toString().contains("Database constraint violation"));

        // Verify rollback was called
        verify(transactionManager).begin();
        verify(transactionManager).rollback();
    }

    @Test
    void testExecuteJTATransaction_ComplexOrder() throws Exception {
        // Arrange
        String businessType = "COMPLEX_ORDER";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("orderId", "ORDER_123");
        businessData.put("items", List.of(
            Map.of("productId", "P001", "price", 100.0, "quantity", 2),
            Map.of("productId", "P002", "price", 50.0, "quantity", 3)
        ));

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Act
        Map<String, Object> result = jtaTransactionService.executeJTATransaction(businessType, businessData);

        // Assert
        assertEquals("SUCCESS", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<String> operationResults = (List<String>) result.get("operationResults");
        
        // Check that complex order processing result contains the total amount
        String businessResult = operationResults.get(2);
        assertTrue(businessResult.contains("COMPLEX_ORDER_PROCESSED_AMOUNT_350.0"));
    }

    @Test
    void testExecuteJTATransaction_BatchUpdate() throws Exception {
        // Arrange
        String businessType = "BATCH_UPDATE";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("updates", List.of(
            Map.of("id", 1, "field", "value1"),
            Map.of("id", 2, "field", "value2"),
            Map.of("id", 3, "field", "value3")
        ));

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Act
        Map<String, Object> result = jtaTransactionService.executeJTATransaction(businessType, businessData);

        // Assert
        assertEquals("SUCCESS", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<String> operationResults = (List<String>) result.get("operationResults");
        
        // Check that batch update processing result contains the count
        String businessResult = operationResults.get(2);
        assertTrue(businessResult.contains("BATCH_UPDATE_PROCESSED_COUNT_3"));
    }

    @Test
    void testExecuteJTATransaction_DataMigration() throws Exception {
        // Arrange
        String businessType = "DATA_MIGRATION";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("sourceTable", "old_table");
        businessData.put("targetTable", "new_table");

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Act
        Map<String, Object> result = jtaTransactionService.executeJTATransaction(businessType, businessData);

        // Assert
        assertEquals("SUCCESS", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<String> operationResults = (List<String>) result.get("operationResults");
        
        // Check that data migration processing result
        String businessResult = operationResults.get(2);
        assertEquals("DATA_MIGRATION_PROCESSED", businessResult);
    }

    @Test
    void testExecuteJTATransaction_InvalidComplexOrder() throws Exception {
        // Arrange
        String businessType = "COMPLEX_ORDER";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("orderId", "INVALID_ORDER");
        // Missing items

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Mock the business logic to throw exception for invalid order
        when(preparedStatement.executeUpdate()).thenAnswer(invocation -> {
            throw new RuntimeException("Invalid order data");
        });

        // Act
        Map<String, Object> result = jtaTransactionService.executeJTATransaction(businessType, businessData);

        // Assert
        assertEquals("ROLLBACK", result.get("status"));
        assertNotNull(result.get("error"));
        assertTrue(result.get("error").toString().contains("Invalid order data"));
    }

    @Test
    void testExecuteJTATransaction_InvalidBatchUpdate() throws Exception {
        // Arrange
        String businessType = "BATCH_UPDATE";
        Map<String, Object> businessData = new HashMap<>();
        // Missing updates

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Mock the business logic to throw exception for missing updates
        when(preparedStatement.executeUpdate()).thenAnswer(invocation -> {
            throw new RuntimeException("No updates provided");
        });

        // Act
        Map<String, Object> result = jtaTransactionService.executeJTATransaction(businessType, businessData);

        // Assert
        assertEquals("ROLLBACK", result.get("status"));
        assertNotNull(result.get("error"));
        assertTrue(result.get("error").toString().contains("No updates provided"));
    }

    @Test
    void testExecuteJTATransaction_InvalidDataMigration() throws Exception {
        // Arrange
        String businessType = "DATA_MIGRATION";
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("sourceTable", "old_table");
        // Missing targetTable

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Mock the business logic to throw exception for missing table info
        when(preparedStatement.executeUpdate()).thenAnswer(invocation -> {
            throw new RuntimeException("Missing migration table information");
        });

        // Act
        Map<String, Object> result = jtaTransactionService.executeJTATransaction(businessType, businessData);

        // Assert
        assertEquals("ROLLBACK", result.get("status"));
        assertNotNull(result.get("error"));
        assertTrue(result.get("error").toString().contains("Missing migration table information"));
    }

    @Test
    void testGetJTATransactionStatus_ExistingTransaction() {
        // Arrange
        String transactionId = "JTA_TEST_123";
        
        // Simulate transaction status tracking
        jtaTransactionService.executeJTATransaction("TEST_TYPE", Map.of("test", "data"));

        // Act
        Map<String, Object> status = jtaTransactionService.getJTATransactionStatus(transactionId);

        // Assert
        assertNotNull(status);
        assertEquals(transactionId, status.get("transactionId"));
        assertNotNull(status.get("currentStatus"));
        assertNotNull(status.get("timestamp"));
    }

    @Test
    void testGetJTATransactionStatus_NonExistingTransaction() {
        // Arrange
        String transactionId = "NON_EXISTING_123";

        // Act
        Map<String, Object> status = jtaTransactionService.getJTATransactionStatus(transactionId);

        // Assert
        assertNotNull(status);
        assertEquals(transactionId, status.get("transactionId"));
        assertEquals(JTATransactionService.TransactionStatus.UNKNOWN, status.get("currentStatus"));
    }

    @Test
    void testGetActiveJTATransactions_NoActiveTransactions() {
        // Act
        List<Map<String, Object>> activeTransactions = jtaTransactionService.getActiveJTATransactions();

        // Assert
        assertNotNull(activeTransactions);
        assertTrue(activeTransactions.isEmpty());
    }

    @Test
    void testCleanupCompletedTransactions() {
        // Arrange
        // Execute some transactions to create completed states
        jtaTransactionService.executeJTATransaction("TEST_TYPE_1", Map.of("test", "data1"));
        jtaTransactionService.executeJTATransaction("TEST_TYPE_2", Map.of("test", "data2"));

        // Act
        jtaTransactionService.cleanupCompletedTransactions();

        // Assert
        // After cleanup, there should be no completed transactions in memory
        List<Map<String, Object>> activeTransactions = jtaTransactionService.getActiveJTATransactions();
        assertTrue(activeTransactions.isEmpty());
    }

    @Test
    void testTransactionSynchronization_BeforeCompletion() throws Exception {
        // This test verifies that the synchronization callbacks work correctly
        // The actual synchronization testing would require integration testing
        // with a real transaction manager, so we'll test the service behavior

        // Arrange
        String businessType = "SYNC_TEST";
        Map<String, Object> businessData = Map.of("test", "sync");

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Act
        Map<String, Object> result = jtaTransactionService.executeJTATransaction(businessType, businessData);

        // Assert
        assertEquals("SUCCESS", result.get("status"));
        verify(transaction).registerSynchronization(any());
    }

    @Test
    void testRollbackFailure() throws Exception {
        // Arrange
        String businessType = "ROLLBACK_FAILURE_TEST";
        Map<String, Object> businessData = Map.of("test", "rollback");

        TimestampEvent mockEvent = createMockTimestampEvent();
        when(timestampService.generateTimestampEvent(anyString(), anyString(), any()))
            .thenReturn(mockEvent);

        // Simulate exception during processing and rollback failure
        when(preparedStatement.executeUpdate()).thenThrow(new RuntimeException("Processing error"));
        when(transactionManager.rollback()).thenThrow(new RuntimeException("Rollback failed"));

        // Act
        Map<String, Object> result = jtaTransactionService.executeJTATransaction(businessType, businessData);

        // Assert
        assertEquals("ROLLBACK_FAILED", result.get("status"));
        assertEquals(JTATransactionService.TransactionStatus.UNKNOWN, result.get("finalStatus"));
        assertNotNull(result.get("error"));
        assertTrue(result.get("error").toString().contains("Rollback failed"));
    }

    private TimestampEvent createMockTimestampEvent() {
        TimestampEvent event = new TimestampEvent();
        event.setEventId("JTA_TEST_EVENT_" + System.currentTimeMillis());
        event.setEventType("JTA_TRANSACTION");
        event.setBusinessType("TEST_BUSINESS");
        event.setTimestamp(System.currentTimeMillis());
        event.setLamportClock(1L);
        event.setNodeId("test-node");
        return event;
    }
}