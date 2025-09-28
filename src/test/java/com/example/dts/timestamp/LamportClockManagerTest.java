package com.example.dts.timestamp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Lamport时钟管理器单元测试
 * 
 * @author DTS Team
 */
@ExtendWith(MockitoExtension.class)
class LamportClockManagerTest {
    
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    private LamportClockManager lamportClockManager;
    
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        lamportClockManager = new LamportClockManager(redisTemplate);
        ReflectionTestUtils.setField(lamportClockManager, "nodeId", "test-node");
        ReflectionTestUtils.setField(lamportClockManager, "persistenceEnabled", false);
    }
    
    @Test
    void testTick() {
        // Given
        long initialTime = lamportClockManager.getCurrentTime();
        
        // When
        long newTime = lamportClockManager.tick();
        
        // Then
        assertEquals(initialTime + 1, newTime);
        assertEquals(newTime, lamportClockManager.getCurrentTime());
    }
    
    @Test
    void testTickMultipleTimes() {
        // Given
        int tickCount = 10;
        
        // When & Then
        for (int i = 1; i <= tickCount; i++) {
            long time = lamportClockManager.tick();
            assertEquals(i, time);
        }
    }
    
    @Test
    void testSync() {
        // Given
        long receivedTimestamp = 5L;
        
        // When
        long syncedTime = lamportClockManager.sync(receivedTimestamp);
        
        // Then
        assertEquals(6L, syncedTime); // max(0, 5) + 1
        assertEquals(6L, lamportClockManager.getCurrentTime());
    }
    
    @Test
    void testSyncWithLowerTimestamp() {
        // Given
        lamportClockManager.tick(); // current = 1
        lamportClockManager.tick(); // current = 2
        lamportClockManager.tick(); // current = 3
        
        long receivedTimestamp = 1L;
        
        // When
        long syncedTime = lamportClockManager.sync(receivedTimestamp);
        
        // Then
        assertEquals(4L, syncedTime); // max(3, 1) + 1
        assertEquals(4L, lamportClockManager.getCurrentTime());
    }
    
    @Test
    void testSyncWithHigherTimestamp() {
        // Given
        lamportClockManager.tick(); // current = 1
        long receivedTimestamp = 10L;
        
        // When
        long syncedTime = lamportClockManager.sync(receivedTimestamp);
        
        // Then
        assertEquals(11L, syncedTime); // max(1, 10) + 1
        assertEquals(11L, lamportClockManager.getCurrentTime());
    }
    
    @Test
    void testSetCurrentTime() {
        // Given
        long newTime = 100L;
        
        // When
        lamportClockManager.setCurrentTime(newTime);
        
        // Then
        assertEquals(newTime, lamportClockManager.getCurrentTime());
    }
    
    @Test
    void testSyncWithNodeSuccess() {
        // Given
        String otherNodeId = "other-node";
        String otherTime = "15";
        when(valueOperations.get("lamport:clock:" + otherNodeId)).thenReturn(otherTime);
        
        // When
        long syncedTime = lamportClockManager.syncWithNode(otherNodeId);
        
        // Then
        assertEquals(16L, syncedTime); // max(0, 15) + 1
        verify(valueOperations).get("lamport:clock:" + otherNodeId);
    }
    
    @Test
    void testSyncWithNodeNotFound() {
        // Given
        String otherNodeId = "other-node";
        when(valueOperations.get("lamport:clock:" + otherNodeId)).thenReturn(null);
        
        // When
        long syncedTime = lamportClockManager.syncWithNode(otherNodeId);
        
        // Then
        assertEquals(1L, syncedTime); // tick() when sync fails
        verify(valueOperations).get("lamport:clock:" + otherNodeId);
    }
    
    @Test
    void testSyncWithNodeException() {
        // Given
        String otherNodeId = "other-node";
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));
        
        // When
        long syncedTime = lamportClockManager.syncWithNode(otherNodeId);
        
        // Then
        assertEquals(1L, syncedTime); // tick() when sync fails
    }
    
    @Test
    void testGetGlobalMaxClock() {
        // Given
        String globalTime = "50";
        when(valueOperations.get("lamport:global")).thenReturn(globalTime);
        
        // When
        long maxClock = lamportClockManager.getGlobalMaxClock();
        
        // Then
        assertEquals(50L, maxClock);
        verify(valueOperations).get("lamport:global");
    }
    
    @Test
    void testGetGlobalMaxClockNotFound() {
        // Given
        when(valueOperations.get("lamport:global")).thenReturn(null);
        
        // When
        long maxClock = lamportClockManager.getGlobalMaxClock();
        
        // Then
        assertEquals(0L, maxClock);
    }
    
    @Test
    void testReset() {
        // Given
        lamportClockManager.tick();
        lamportClockManager.tick();
        assertEquals(2L, lamportClockManager.getCurrentTime());
        
        // When
        lamportClockManager.reset();
        
        // Then
        assertEquals(0L, lamportClockManager.getCurrentTime());
    }
    
    @Test
    void testGetNodeId() {
        // When
        String nodeId = lamportClockManager.getNodeId();
        
        // Then
        assertEquals("test-node", nodeId);
    }
    
    @Test
    void testConcurrentTicks() throws InterruptedException {
        // Given
        int threadCount = 10;
        int ticksPerThread = 100;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger totalTicks = new java.util.concurrent.atomic.AtomicInteger(0);
        
        // When
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < ticksPerThread; j++) {
                    lamportClockManager.tick();
                    totalTicks.incrementAndGet();
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        
        // Then
        assertEquals(threadCount * ticksPerThread, lamportClockManager.getCurrentTime());
        assertEquals(threadCount * ticksPerThread, totalTicks.get());
    }
    
    @Test
    void testConcurrentSyncs() throws InterruptedException {
        // Given
        int threadCount = 5;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicLong maxSyncedTime = new java.util.concurrent.atomic.AtomicLong(0);
        
        // When
        for (int i = 0; i < threadCount; i++) {
            final long receivedTime = (i + 1) * 10L;
            new Thread(() -> {
                long syncedTime = lamportClockManager.sync(receivedTime);
                maxSyncedTime.updateAndGet(current -> Math.max(current, syncedTime));
                latch.countDown();
            }).start();
        }
        
        latch.await();
        
        // Then
        assertTrue(lamportClockManager.getCurrentTime() > 0);
        assertTrue(maxSyncedTime.get() > 0);
    }
}