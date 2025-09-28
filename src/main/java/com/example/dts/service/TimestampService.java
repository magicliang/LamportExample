package com.example.dts.service;

import com.example.dts.model.TimestampEvent;
import com.example.dts.model.VectorClock;
import com.example.dts.model.VersionVector;
import com.example.dts.repository.TimestampEventRepository;
import com.example.dts.timestamp.LamportClockManager;
import com.example.dts.timestamp.VectorClockManager;
import com.example.dts.timestamp.VersionVectorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间戳服务
 * 整合Lamport时间戳、版本向量和向量时钟的功能
 * 
 * @author DTS Team
 */
@Service
@Transactional
public class TimestampService {
    
    private static final Logger logger = LoggerFactory.getLogger(TimestampService.class);
    
    private final LamportClockManager lamportClockManager;
    private final VectorClockManager vectorClockManager;
    private final VersionVectorManager versionVectorManager;
    private final TimestampEventRepository timestampEventRepository;
    
    @Value("${dts.node.id}")
    private String nodeId;
    
    public TimestampService(LamportClockManager lamportClockManager,
                           VectorClockManager vectorClockManager,
                           VersionVectorManager versionVectorManager,
                           TimestampEventRepository timestampEventRepository) {
        this.lamportClockManager = lamportClockManager;
        this.vectorClockManager = vectorClockManager;
        this.versionVectorManager = versionVectorManager;
        this.timestampEventRepository = timestampEventRepository;
    }
    
    /**
     * 创建新的时间戳事件
     */
    public TimestampEvent createEvent(String eventType, Map<String, Object> eventData) {
        // 生成各种时间戳
        long lamportTime = lamportClockManager.tick();
        VectorClock vectorClock = vectorClockManager.tick();
        VersionVector versionVector = versionVectorManager.increment();
        
        // 创建事件记录
        TimestampEvent event = new TimestampEvent(nodeId, lamportTime, eventType);
        event.setVectorClockMap(vectorClock.getClock());
        event.setVersionVectorMap(versionVector.getVector());
        event.setEventDataMap(eventData);
        
        // 保存到数据库
        event = timestampEventRepository.save(event);
        
        logger.info("Created timestamp event: id={}, type={}, lamport={}, node={}", 
                   event.getId(), eventType, lamportTime, nodeId);
        
        return event;
    }
    
    /**
     * 同步接收到的时间戳事件
     */
    public TimestampEvent syncEvent(String sourceNodeId, long receivedLamportTime, 
                                   Map<String, Long> receivedVectorClock,
                                   Map<String, Long> receivedVersionVector,
                                   String eventType, Map<String, Object> eventData) {
        
        // 同步各种时间戳
        long syncedLamportTime = lamportClockManager.sync(receivedLamportTime);
        VectorClock syncedVectorClock = vectorClockManager.sync(new VectorClock(receivedVectorClock));
        VersionVector syncedVersionVector = versionVectorManager.merge(new VersionVector(receivedVersionVector));
        
        // 创建同步事件记录
        TimestampEvent event = new TimestampEvent(sourceNodeId, receivedLamportTime, eventType);
        event.setVectorClockMap(receivedVectorClock);
        event.setVersionVectorMap(receivedVersionVector);
        event.setEventDataMap(eventData);
        
        // 保存到数据库
        event = timestampEventRepository.save(event);
        
        logger.info("Synced timestamp event: id={}, source={}, lamport={}->{}, node={}", 
                   event.getId(), sourceNodeId, receivedLamportTime, syncedLamportTime, nodeId);
        
        return event;
    }
    
    /**
     * 获取当前节点的时间戳状态
     */
    public Map<String, Object> getCurrentTimestampStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("nodeId", nodeId);
        status.put("lamportTime", lamportClockManager.getCurrentTime());
        status.put("vectorClock", vectorClockManager.getCurrentClock().getClock());
        status.put("versionVector", versionVectorManager.getCurrentVector().getVector());
        status.put("timestamp", System.currentTimeMillis());
        
        return status;
    }
    
    /**
     * 比较两个事件的时间关系
     */
    public Map<String, Object> compareEvents(Long eventId1, Long eventId2) {
        TimestampEvent event1 = timestampEventRepository.findById(eventId1)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId1));
        TimestampEvent event2 = timestampEventRepository.findById(eventId2)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId2));
        
        Map<String, Object> comparison = new HashMap<>();
        
        // Lamport时间戳比较
        long lamport1 = event1.getLamportTimestamp();
        long lamport2 = event2.getLamportTimestamp();
        String lamportRelation;
        if (lamport1 < lamport2) {
            lamportRelation = "BEFORE";
        } else if (lamport1 > lamport2) {
            lamportRelation = "AFTER";
        } else {
            lamportRelation = "CONCURRENT";
        }
        
        comparison.put("lamportRelation", lamportRelation);
        comparison.put("lamportTime1", lamport1);
        comparison.put("lamportTime2", lamport2);
        
        // 向量时钟比较（如果存在）
        if (event1.getVectorClock() != null && event2.getVectorClock() != null) {
            try {
                VectorClock clock1 = parseVectorClock(event1.getVectorClock());
                VectorClock clock2 = parseVectorClock(event2.getVectorClock());
                VectorClock.ClockRelation clockRelation = vectorClockManager.compare(clock1, clock2);
                
                comparison.put("vectorClockRelation", clockRelation.toString());
                comparison.put("vectorClock1", clock1.getClock());
                comparison.put("vectorClock2", clock2.getClock());
            } catch (Exception e) {
                logger.error("Error comparing vector clocks: {}", e.getMessage());
                comparison.put("vectorClockRelation", "ERROR");
            }
        }
        
        // 版本向量比较（如果存在）
        if (event1.getVersionVector() != null && event2.getVersionVector() != null) {
            try {
                VersionVector vector1 = parseVersionVector(event1.getVersionVector());
                VersionVector vector2 = parseVersionVector(event2.getVersionVector());
                VersionVector.VectorRelation vectorRelation = versionVectorManager.compare(vector1, vector2);
                
                comparison.put("versionVectorRelation", vectorRelation.toString());
                comparison.put("versionVector1", vector1.getVector());
                comparison.put("versionVector2", vector2.getVector());
                comparison.put("hasConflict", vector1.hasConflict(vector2));
            } catch (Exception e) {
                logger.error("Error comparing version vectors: {}", e.getMessage());
                comparison.put("versionVectorRelation", "ERROR");
            }
        }
        
        return comparison;
    }
    
    /**
     * 获取节点的事件历史
     */
    public List<TimestampEvent> getNodeEventHistory(String targetNodeId, int limit) {
        return timestampEventRepository.findByNodeIdOrderByCreatedAtDesc(targetNodeId, 
                org.springframework.data.domain.PageRequest.of(0, limit));
    }
    
    /**
     * 获取指定时间范围内的事件
     */
    public List<TimestampEvent> getEventsInTimeRange(java.time.LocalDateTime startTime, 
                                                    java.time.LocalDateTime endTime) {
        return timestampEventRepository.findByCreatedAtBetweenOrderByLamportTimestamp(startTime, endTime);
    }
    
    /**
     * 检测事件冲突
     */
    public List<Map<String, Object>> detectConflicts(int limit) {
        List<Map<String, Object>> conflicts = new java.util.ArrayList<>();
        
        // 获取最近的事件
        List<TimestampEvent> recentEvents = timestampEventRepository
                .findTop100ByOrderByCreatedAtDesc();
        
        // 检查版本向量冲突
        for (int i = 0; i < recentEvents.size() && conflicts.size() < limit; i++) {
            for (int j = i + 1; j < recentEvents.size() && conflicts.size() < limit; j++) {
                TimestampEvent event1 = recentEvents.get(i);
                TimestampEvent event2 = recentEvents.get(j);
                
                if (event1.getVersionVector() != null && event2.getVersionVector() != null) {
                    try {
                        VersionVector vector1 = parseVersionVector(event1.getVersionVector());
                        VersionVector vector2 = parseVersionVector(event2.getVersionVector());
                        
                        if (vector1.hasConflict(vector2)) {
                            Map<String, Object> conflict = new HashMap<>();
                            conflict.put("event1Id", event1.getId());
                            conflict.put("event2Id", event2.getId());
                            conflict.put("node1", event1.getNodeId());
                            conflict.put("node2", event2.getNodeId());
                            conflict.put("conflictType", "VERSION_VECTOR");
                            conflict.put("detectedAt", System.currentTimeMillis());
                            conflicts.add(conflict);
                        }
                    } catch (Exception e) {
                        logger.error("Error detecting conflict between events {} and {}: {}", 
                                   event1.getId(), event2.getId(), e.getMessage());
                    }
                }
            }
        }
        
        return conflicts;
    }
    
    /**
     * 同步所有时间戳管理器
     */
    public Map<String, Object> syncAllTimestamps() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取全局最大Lamport时间
            long globalMaxLamport = lamportClockManager.getGlobalMaxClock();
            if (globalMaxLamport > lamportClockManager.getCurrentTime()) {
                lamportClockManager.sync(globalMaxLamport);
            }
            
            // 获取所有节点的向量时钟并同步
            Map<String, VectorClock> allVectorClocks = vectorClockManager.getAllNodeClocks();
            for (VectorClock clock : allVectorClocks.values()) {
                vectorClockManager.sync(clock);
            }
            
            // 获取所有节点的版本向量并合并
            Map<String, VersionVector> allVersionVectors = versionVectorManager.getAllNodeVectors();
            for (VersionVector vector : allVersionVectors.values()) {
                versionVectorManager.merge(vector);
            }
            
            result.put("success", true);
            result.put("syncedAt", System.currentTimeMillis());
            result.put("lamportTime", lamportClockManager.getCurrentTime());
            result.put("vectorClock", vectorClockManager.getCurrentClock().getClock());
            result.put("versionVector", versionVectorManager.getCurrentVector().getVector());
            
            logger.info("All timestamps synced successfully for node: {}", nodeId);
            
        } catch (Exception e) {
            logger.error("Error syncing timestamps: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 解析向量时钟JSON
     */
    private VectorClock parseVectorClock(String json) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Long> clockMap = mapper.readValue(json, Map.class);
        return new VectorClock(clockMap);
    }
    
    /**
     * 解析版本向量JSON
     */
    private VersionVector parseVersionVector(String json) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Long> vectorMap = mapper.readValue(json, Map.class);
        return new VersionVector(vectorMap);
    }
}