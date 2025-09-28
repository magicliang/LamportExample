package com.example.dts.service;

import com.example.dts.DtsApplication;
import com.example.dts.model.TimestampEvent;
import com.example.dts.repository.TimestampEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 时间戳服务集成测试
 * 使用Testcontainers进行真实的数据库和Redis测试
 * 
 * @author DTS Team
 */
@SpringBootTest(classes = DtsApplication.class)
@ActiveProfiles("test")
@Testcontainers
@Transactional
class TimestampServiceIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:6-alpine")
            .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }
    
    @Autowired
    private TimestampService timestampService;
    
    @Autowired
    private TimestampEventRepository timestampEventRepository;
    
    @BeforeEach
    void setUp() {
        timestampEventRepository.deleteAll();
    }
    
    @Test
    void testCreateEvent() {
        // Given
        String eventType = "TEST_EVENT";
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("key1", "value1");
        eventData.put("key2", 123);
        
        // When
        TimestampEvent event = timestampService.createEvent(eventType, eventData);
        
        // Then
        assertNotNull(event);
        assertNotNull(event.getId());
        assertEquals(eventType, event.getEventType());
        assertTrue(event.getLamportTimestamp() > 0);
        assertNotNull(event.getVectorClock());
        assertNotNull(event.getVersionVector());
        assertNotNull(event.getCreatedAt());
        
        // Verify persistence
        TimestampEvent savedEvent = timestampEventRepository.findById(event.getId()).orElse(null);
        assertNotNull(savedEvent);
        assertEquals(event.getEventType(), savedEvent.getEventType());
    }
    
    @Test
    void testSyncEvent() {
        // Given
        String sourceNodeId = "source-node";
        long receivedLamportTime = 10L;
        Map<String, Long> receivedVectorClock = new HashMap<>();
        receivedVectorClock.put("source-node", 5L);
        receivedVectorClock.put("other-node", 3L);
        
        Map<String, Long> receivedVersionVector = new HashMap<>();
        receivedVersionVector.put("source-node", 2L);
        
        String eventType = "SYNC_EVENT";
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("syncData", "test");
        
        // When
        TimestampEvent syncedEvent = timestampService.syncEvent(
                sourceNodeId, receivedLamportTime, receivedVectorClock, 
                receivedVersionVector, eventType, eventData);
        
        // Then
        assertNotNull(syncedEvent);
        assertEquals(sourceNodeId, syncedEvent.getNodeId());
        assertEquals(receivedLamportTime, syncedEvent.getLamportTimestamp());
        assertEquals(eventType, syncedEvent.getEventType());
        
        // Verify persistence
        TimestampEvent savedEvent = timestampEventRepository.findById(syncedEvent.getId()).orElse(null);
        assertNotNull(savedEvent);
    }
    
    @Test
    void testGetCurrentTimestampStatus() {
        // When
        Map<String, Object> status = timestampService.getCurrentTimestampStatus();
        
        // Then
        assertNotNull(status);
        assertTrue(status.containsKey("nodeId"));
        assertTrue(status.containsKey("lamportTime"));
        assertTrue(status.containsKey("vectorClock"));
        assertTrue(status.containsKey("versionVector"));
        assertTrue(status.containsKey("timestamp"));
        
        assertTrue(status.get("lamportTime") instanceof Long);
        assertTrue(status.get("vectorClock") instanceof Map);
        assertTrue(status.get("versionVector") instanceof Map);
    }
    
    @Test
    void testCompareEvents() {
        // Given
        Map<String, Object> eventData1 = new HashMap<>();
        eventData1.put("data", "event1");
        TimestampEvent event1 = timestampService.createEvent("EVENT_1", eventData1);
        
        // Wait a bit to ensure different timestamps
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Map<String, Object> eventData2 = new HashMap<>();
        eventData2.put("data", "event2");
        TimestampEvent event2 = timestampService.createEvent("EVENT_2", eventData2);
        
        // When
        Map<String, Object> comparison = timestampService.compareEvents(event1.getId(), event2.getId());
        
        // Then
        assertNotNull(comparison);
        assertTrue(comparison.containsKey("lamportRelation"));
        assertTrue(comparison.containsKey("lamportTime1"));
        assertTrue(comparison.containsKey("lamportTime2"));
        
        // Event1 should be before Event2 in Lamport time
        assertEquals("BEFORE", comparison.get("lamportRelation"));
        assertTrue((Long) comparison.get("lamportTime1") < (Long) comparison.get("lamportTime2"));
    }
    
    @Test
    void testGetNodeEventHistory() {
        // Given
        String nodeId = "test-node";
        int eventCount = 5;
        
        for (int i = 0; i < eventCount; i++) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("index", i);
            timestampService.createEvent("HISTORY_EVENT_" + i, eventData);
        }
        
        // When
        List<TimestampEvent> history = timestampService.getNodeEventHistory(nodeId, 10);
        
        // Then
        assertNotNull(history);
        assertEquals(eventCount, history.size());
        
        // Events should be ordered by creation time descending
        for (int i = 0; i < history.size() - 1; i++) {
            assertTrue(history.get(i).getCreatedAt().isAfter(history.get(i + 1).getCreatedAt()) ||
                      history.get(i).getCreatedAt().isEqual(history.get(i + 1).getCreatedAt()));
        }
    }
    
    @Test
    void testGetEventsInTimeRange() {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(1);
        
        // Create some events
        for (int i = 0; i < 3; i++) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("index", i);
            timestampService.createEvent("RANGE_EVENT_" + i, eventData);
        }
        
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);
        
        // When
        List<TimestampEvent> events = timestampService.getEventsInTimeRange(startTime, endTime);
        
        // Then
        assertNotNull(events);
        assertEquals(3, events.size());
        
        // All events should be within the time range
        for (TimestampEvent event : events) {
            assertTrue(event.getCreatedAt().isAfter(startTime) || event.getCreatedAt().isEqual(startTime));
            assertTrue(event.getCreatedAt().isBefore(endTime) || event.getCreatedAt().isEqual(endTime));
        }
    }
    
    @Test
    void testDetectConflicts() {
        // Given - Create events that might have conflicts
        Map<String, Object> eventData1 = new HashMap<>();
        eventData1.put("resource", "shared-resource");
        TimestampEvent event1 = timestampService.createEvent("CONFLICT_EVENT_1", eventData1);
        
        Map<String, Object> eventData2 = new HashMap<>();
        eventData2.put("resource", "shared-resource");
        TimestampEvent event2 = timestampService.createEvent("CONFLICT_EVENT_2", eventData2);
        
        // When
        List<Map<String, Object>> conflicts = timestampService.detectConflicts(10);
        
        // Then
        assertNotNull(conflicts);
        // Note: Actual conflict detection depends on version vector implementation
        // This test mainly verifies the method doesn't throw exceptions
    }
    
    @Test
    void testSyncAllTimestamps() {
        // Given - Create some events first
        for (int i = 0; i < 3; i++) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("index", i);
            timestampService.createEvent("SYNC_TEST_EVENT_" + i, eventData);
        }
        
        // When
        Map<String, Object> result = timestampService.syncAllTimestamps();
        
        // Then
        assertNotNull(result);
        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));
        assertTrue(result.containsKey("syncedAt"));
        assertTrue(result.containsKey("lamportTime"));
        assertTrue(result.containsKey("vectorClock"));
        assertTrue(result.containsKey("versionVector"));
    }
    
    @Test
    void testMultipleEventsTimestampOrdering() {
        // Given
        int eventCount = 10;
        java.util.List<TimestampEvent> events = new java.util.ArrayList<>();
        
        // When - Create multiple events
        for (int i = 0; i < eventCount; i++) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("sequence", i);
            TimestampEvent event = timestampService.createEvent("SEQUENCE_EVENT_" + i, eventData);
            events.add(event);
        }
        
        // Then - Verify Lamport timestamps are monotonically increasing
        for (int i = 0; i < events.size() - 1; i++) {
            assertTrue(events.get(i).getLamportTimestamp() < events.get(i + 1).getLamportTimestamp(),
                      "Lamport timestamps should be monotonically increasing");
        }
    }
    
    @Test
    void testConcurrentEventCreation() throws InterruptedException {
        // Given
        int threadCount = 5;
        int eventsPerThread = 10;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.ConcurrentLinkedQueue<TimestampEvent> allEvents = new java.util.concurrent.ConcurrentLinkedQueue<>();
        
        // When - Create events concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < eventsPerThread; j++) {
                        Map<String, Object> eventData = new HashMap<>();
                        eventData.put("threadId", threadId);
                        eventData.put("eventIndex", j);
                        TimestampEvent event = timestampService.createEvent("CONCURRENT_EVENT", eventData);
                        allEvents.add(event);
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        latch.await();
        
        // Then
        assertEquals(threadCount * eventsPerThread, allEvents.size());
        
        // Verify all events have unique Lamport timestamps
        java.util.Set<Long> timestamps = new java.util.HashSet<>();
        for (TimestampEvent event : allEvents) {
            assertTrue(timestamps.add(event.getLamportTimestamp()), 
                      "All Lamport timestamps should be unique");
        }
    }
}