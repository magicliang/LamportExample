package com.example.dts.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 时间戳事件实体
 * 
 * @author DTS Team
 */
@Entity
@Table(name = "timestamp_events", indexes = {
    @Index(name = "idx_node_timestamp", columnList = "nodeId,lamportTimestamp"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class TimestampEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;
    
    @Column(name = "lamport_timestamp", nullable = false)
    private Long lamportTimestamp;
    
    @Column(name = "vector_clock", columnDefinition = "JSON")
    private String vectorClock;
    
    @Column(name = "version_vector", columnDefinition = "JSON")
    private String versionVector;
    
    @Column(name = "event_type", length = 32)
    private String eventType;
    
    @Column(name = "event_data", columnDefinition = "JSON")
    private String eventData;
    
    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    // 构造函数
    public TimestampEvent() {}
    
    public TimestampEvent(String nodeId, Long lamportTimestamp, String eventType) {
        this.nodeId = nodeId;
        this.lamportTimestamp = lamportTimestamp;
        this.eventType = eventType;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    public Long getLamportTimestamp() {
        return lamportTimestamp;
    }
    
    public void setLamportTimestamp(Long lamportTimestamp) {
        this.lamportTimestamp = lamportTimestamp;
    }
    
    public String getVectorClock() {
        return vectorClock;
    }
    
    public void setVectorClock(String vectorClock) {
        this.vectorClock = vectorClock;
    }
    
    public void setVectorClockMap(Map<String, Long> vectorClockMap) {
        if (vectorClockMap != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                this.vectorClock = mapper.writeValueAsString(vectorClockMap);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize vector clock", e);
            }
        }
    }
    
    public String getVersionVector() {
        return versionVector;
    }
    
    public void setVersionVector(String versionVector) {
        this.versionVector = versionVector;
    }
    
    public void setVersionVectorMap(Map<String, Long> versionVectorMap) {
        if (versionVectorMap != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                this.versionVector = mapper.writeValueAsString(versionVectorMap);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize version vector", e);
            }
        }
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public String getEventData() {
        return eventData;
    }
    
    public void setEventData(String eventData) {
        this.eventData = eventData;
    }
    
    public void setEventDataMap(Map<String, Object> eventDataMap) {
        if (eventDataMap != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                this.eventData = mapper.writeValueAsString(eventDataMap);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize event data", e);
            }
        }
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // 添加缺失的getter方法
    public String getEventId() {
        return this.id != null ? this.id.toString() : null;
    }
    
    public Long getTimestamp() {
        return this.lamportTimestamp;
    }
    
    public String getBusinessType() {
        return this.eventType;
    }
    
    public Long getLamportClock() {
        return this.lamportTimestamp;
    }
    
    @Override
    public String toString() {
        return "TimestampEvent{" +
                "id=" + id +
                ", nodeId='" + nodeId + '\'' +
                ", lamportTimestamp=" + lamportTimestamp +
                ", eventType='" + eventType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}