package com.example.dts.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 向量时钟单元测试
 * 
 * @author DTS Team
 */
class VectorClockTest {
    
    @Test
    void testEmptyVectorClock() {
        // Given
        VectorClock clock = new VectorClock();
        
        // Then
        assertTrue(clock.isEmpty());
        assertEquals(0, clock.size());
        assertEquals(0L, clock.getValue("node1"));
    }
    
    @Test
    void testVectorClockWithInitialData() {
        // Given
        Map<String, Long> initialClock = new HashMap<>();
        initialClock.put("node1", 5L);
        initialClock.put("node2", 3L);
        
        // When
        VectorClock clock = new VectorClock(initialClock);
        
        // Then
        assertFalse(clock.isEmpty());
        assertEquals(2, clock.size());
        assertEquals(5L, clock.getValue("node1"));
        assertEquals(3L, clock.getValue("node2"));
        assertEquals(0L, clock.getValue("node3"));
    }
    
    @Test
    void testTick() {
        // Given
        VectorClock clock = new VectorClock();
        
        // When
        VectorClock newClock = clock.tick("node1");
        
        // Then
        assertEquals(0L, clock.getValue("node1")); // original unchanged
        assertEquals(1L, newClock.getValue("node1")); // new clock updated
        
        // Multiple ticks
        VectorClock clock2 = newClock.tick("node1");
        assertEquals(2L, clock2.getValue("node1"));
    }
    
    @Test
    void testSync() {
        // Given
        Map<String, Long> clock1Data = new HashMap<>();
        clock1Data.put("node1", 3L);
        clock1Data.put("node2", 1L);
        VectorClock clock1 = new VectorClock(clock1Data);
        
        Map<String, Long> clock2Data = new HashMap<>();
        clock2Data.put("node1", 1L);
        clock2Data.put("node2", 4L);
        clock2Data.put("node3", 2L);
        VectorClock clock2 = new VectorClock(clock2Data);
        
        // When
        VectorClock syncedClock = clock1.sync(clock2, "node1");
        
        // Then
        assertEquals(4L, syncedClock.getValue("node1")); // max(3,1) + 1
        assertEquals(4L, syncedClock.getValue("node2")); // max(1,4)
        assertEquals(2L, syncedClock.getValue("node3")); // max(0,2)
    }
    
    @Test
    void testCompareEqual() {
        // Given
        Map<String, Long> clockData = new HashMap<>();
        clockData.put("node1", 3L);
        clockData.put("node2", 2L);
        
        VectorClock clock1 = new VectorClock(clockData);
        VectorClock clock2 = new VectorClock(clockData);
        
        // When
        VectorClock.ClockRelation relation = clock1.compareTo(clock2);
        
        // Then
        assertEquals(VectorClock.ClockRelation.EQUAL, relation);
        assertTrue(clock1.equals(clock2));
    }
    
    @Test
    void testCompareBefore() {
        // Given
        Map<String, Long> clock1Data = new HashMap<>();
        clock1Data.put("node1", 1L);
        clock1Data.put("node2", 2L);
        VectorClock clock1 = new VectorClock(clock1Data);
        
        Map<String, Long> clock2Data = new HashMap<>();
        clock2Data.put("node1", 3L);
        clock2Data.put("node2", 4L);
        VectorClock clock2 = new VectorClock(clock2Data);
        
        // When
        VectorClock.ClockRelation relation = clock1.compareTo(clock2);
        
        // Then
        assertEquals(VectorClock.ClockRelation.BEFORE, relation);
        assertTrue(clock1.happensBefore(clock2));
        assertFalse(clock1.happensAfter(clock2));
        assertFalse(clock1.isConcurrent(clock2));
    }
    
    @Test
    void testCompareAfter() {
        // Given
        Map<String, Long> clock1Data = new HashMap<>();
        clock1Data.put("node1", 5L);
        clock1Data.put("node2", 3L);
        VectorClock clock1 = new VectorClock(clock1Data);
        
        Map<String, Long> clock2Data = new HashMap<>();
        clock2Data.put("node1", 2L);
        clock2Data.put("node2", 1L);
        VectorClock clock2 = new VectorClock(clock2Data);
        
        // When
        VectorClock.ClockRelation relation = clock1.compareTo(clock2);
        
        // Then
        assertEquals(VectorClock.ClockRelation.AFTER, relation);
        assertFalse(clock1.happensBefore(clock2));
        assertTrue(clock1.happensAfter(clock2));
        assertFalse(clock1.isConcurrent(clock2));
    }
    
    @Test
    void testCompareConcurrent() {
        // Given
        Map<String, Long> clock1Data = new HashMap<>();
        clock1Data.put("node1", 3L);
        clock1Data.put("node2", 1L);
        VectorClock clock1 = new VectorClock(clock1Data);
        
        Map<String, Long> clock2Data = new HashMap<>();
        clock2Data.put("node1", 1L);
        clock2Data.put("node2", 3L);
        VectorClock clock2 = new VectorClock(clock2Data);
        
        // When
        VectorClock.ClockRelation relation = clock1.compareTo(clock2);
        
        // Then
        assertEquals(VectorClock.ClockRelation.CONCURRENT, relation);
        assertFalse(clock1.happensBefore(clock2));
        assertFalse(clock1.happensAfter(clock2));
        assertTrue(clock1.isConcurrent(clock2));
    }
    
    @Test
    void testCompareWithDifferentNodes() {
        // Given
        Map<String, Long> clock1Data = new HashMap<>();
        clock1Data.put("node1", 2L);
        clock1Data.put("node2", 1L);
        VectorClock clock1 = new VectorClock(clock1Data);
        
        Map<String, Long> clock2Data = new HashMap<>();
        clock2Data.put("node2", 3L);
        clock2Data.put("node3", 1L);
        VectorClock clock2 = new VectorClock(clock2Data);
        
        // When
        VectorClock.ClockRelation relation = clock1.compareTo(clock2);
        
        // Then
        assertEquals(VectorClock.ClockRelation.CONCURRENT, relation);
        assertTrue(clock1.isConcurrent(clock2));
    }
    
    @Test
    void testSetValue() {
        // Given
        VectorClock clock = new VectorClock();
        
        // When
        clock.setValue("node1", 5L);
        clock.setValue("node2", 3L);
        
        // Then
        assertEquals(5L, clock.getValue("node1"));
        assertEquals(3L, clock.getValue("node2"));
        assertEquals(2, clock.size());
    }
    
    @Test
    void testGetClock() {
        // Given
        Map<String, Long> originalData = new HashMap<>();
        originalData.put("node1", 3L);
        originalData.put("node2", 2L);
        VectorClock clock = new VectorClock(originalData);
        
        // When
        Map<String, Long> clockCopy = clock.getClock();
        
        // Then
        assertEquals(originalData, clockCopy);
        assertNotSame(originalData, clockCopy); // should be a copy
        
        // Modifying copy should not affect original
        clockCopy.put("node3", 1L);
        assertFalse(clock.getNodeIds().contains("node3"));
    }
    
    @Test
    void testGetNodeIds() {
        // Given
        Map<String, Long> clockData = new HashMap<>();
        clockData.put("node1", 3L);
        clockData.put("node2", 2L);
        VectorClock clock = new VectorClock(clockData);
        
        // When
        java.util.Set<String> nodeIds = clock.getNodeIds();
        
        // Then
        assertEquals(2, nodeIds.size());
        assertTrue(nodeIds.contains("node1"));
        assertTrue(nodeIds.contains("node2"));
        assertFalse(nodeIds.contains("node3"));
    }
    
    @Test
    void testClear() {
        // Given
        Map<String, Long> clockData = new HashMap<>();
        clockData.put("node1", 3L);
        clockData.put("node2", 2L);
        VectorClock clock = new VectorClock(clockData);
        
        // When
        clock.clear();
        
        // Then
        assertTrue(clock.isEmpty());
        assertEquals(0, clock.size());
        assertEquals(0L, clock.getValue("node1"));
    }
    
    @Test
    void testCopyConstructor() {
        // Given
        Map<String, Long> clockData = new HashMap<>();
        clockData.put("node1", 3L);
        clockData.put("node2", 2L);
        VectorClock original = new VectorClock(clockData);
        
        // When
        VectorClock copy = new VectorClock(original);
        
        // Then
        assertEquals(original, copy);
        assertNotSame(original, copy);
        
        // Modifying copy should not affect original
        copy.setValue("node3", 1L);
        assertFalse(original.getNodeIds().contains("node3"));
        assertTrue(copy.getNodeIds().contains("node3"));
    }
    
    @Test
    void testHashCodeAndEquals() {
        // Given
        Map<String, Long> clockData1 = new HashMap<>();
        clockData1.put("node1", 3L);
        clockData1.put("node2", 2L);
        
        Map<String, Long> clockData2 = new HashMap<>();
        clockData2.put("node1", 3L);
        clockData2.put("node2", 2L);
        
        Map<String, Long> clockData3 = new HashMap<>();
        clockData3.put("node1", 3L);
        clockData3.put("node2", 3L);
        
        VectorClock clock1 = new VectorClock(clockData1);
        VectorClock clock2 = new VectorClock(clockData2);
        VectorClock clock3 = new VectorClock(clockData3);
        
        // Then
        assertEquals(clock1, clock2);
        assertEquals(clock1.hashCode(), clock2.hashCode());
        
        assertNotEquals(clock1, clock3);
        assertNotEquals(clock1.hashCode(), clock3.hashCode());
        
        assertNotEquals(clock1, null);
        assertNotEquals(clock1, "not a vector clock");
    }
    
    @Test
    void testToString() {
        // Given
        Map<String, Long> clockData = new HashMap<>();
        clockData.put("node1", 3L);
        clockData.put("node2", 2L);
        VectorClock clock = new VectorClock(clockData);
        
        // When
        String str = clock.toString();
        
        // Then
        assertTrue(str.contains("VectorClock"));
        assertTrue(str.contains("node1"));
        assertTrue(str.contains("node2"));
        assertTrue(str.contains("3"));
        assertTrue(str.contains("2"));
    }
}