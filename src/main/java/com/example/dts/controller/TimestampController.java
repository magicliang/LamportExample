package com.example.dts.controller;

import com.example.dts.model.TimestampEvent;
import com.example.dts.service.TimestampService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间戳管理REST控制器
 * 提供Lamport时间戳、版本向量和向量时钟的API接口
 * 
 * @author DTS Team
 */
@RestController
@RequestMapping("/v1/timestamp")
@Api(tags = "时间戳管理", description = "分布式时间戳管理API")
public class TimestampController {
    
    private static final Logger logger = LoggerFactory.getLogger(TimestampController.class);
    
    private final TimestampService timestampService;
    
    public TimestampController(TimestampService timestampService) {
        this.timestampService = timestampService;
    }
    
    /**
     * 创建新的时间戳事件
     */
    @PostMapping("/event")
    @ApiOperation("创建时间戳事件")
    public ResponseEntity<Map<String, Object>> createEvent(
            @ApiParam("事件请求") @Valid @RequestBody CreateEventRequest request) {
        
        try {
            TimestampEvent event = timestampService.createEvent(
                    request.getEventType(), 
                    request.getEventData()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("eventId", event.getId());
            response.put("lamportTimestamp", event.getLamportTimestamp());
            response.put("nodeId", event.getNodeId());
            response.put("createdAt", event.getCreatedAt());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error creating timestamp event: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 同步接收到的时间戳事件
     */
    @PostMapping("/sync")
    @ApiOperation("同步时间戳事件")
    public ResponseEntity<Map<String, Object>> syncEvent(
            @ApiParam("同步事件请求") @Valid @RequestBody SyncEventRequest request) {
        
        try {
            TimestampEvent event = timestampService.syncEvent(
                    request.getSourceNodeId(),
                    request.getLamportTimestamp(),
                    request.getVectorClock(),
                    request.getVersionVector(),
                    request.getEventType(),
                    request.getEventData()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("eventId", event.getId());
            response.put("syncedAt", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error syncing timestamp event: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 获取当前时间戳状态
     */
    @GetMapping("/status")
    @ApiOperation("获取当前时间戳状态")
    public ResponseEntity<Map<String, Object>> getCurrentStatus() {
        try {
            Map<String, Object> status = timestampService.getCurrentTimestampStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Error getting timestamp status: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 比较两个事件的时间关系
     */
    @GetMapping("/compare")
    @ApiOperation("比较事件时间关系")
    public ResponseEntity<Map<String, Object>> compareEvents(
            @ApiParam("事件ID1") @RequestParam @NotNull Long eventId1,
            @ApiParam("事件ID2") @RequestParam @NotNull Long eventId2) {
        
        try {
            Map<String, Object> comparison = timestampService.compareEvents(eventId1, eventId2);
            return ResponseEntity.ok(comparison);
        } catch (Exception e) {
            logger.error("Error comparing events {} and {}: {}", eventId1, eventId2, e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 获取节点事件历史
     */
    @GetMapping("/history/{nodeId}")
    @ApiOperation("获取节点事件历史")
    public ResponseEntity<Map<String, Object>> getNodeHistory(
            @ApiParam("节点ID") @PathVariable @NotBlank String nodeId,
            @ApiParam("限制数量") @RequestParam(defaultValue = "50") int limit) {
        
        try {
            List<TimestampEvent> events = timestampService.getNodeEventHistory(nodeId, limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("nodeId", nodeId);
            response.put("events", events);
            response.put("count", events.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting node history for {}: {}", nodeId, e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 获取时间范围内的事件
     */
    @GetMapping("/events")
    @ApiOperation("获取时间范围内的事件")
    public ResponseEntity<Map<String, Object>> getEventsInRange(
            @ApiParam("开始时间") @RequestParam 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @ApiParam("结束时间") @RequestParam 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        try {
            List<TimestampEvent> events = timestampService.getEventsInTimeRange(startTime, endTime);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("startTime", startTime);
            response.put("endTime", endTime);
            response.put("events", events);
            response.put("count", events.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting events in range: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 检测冲突
     */
    @GetMapping("/conflicts")
    @ApiOperation("检测事件冲突")
    public ResponseEntity<Map<String, Object>> detectConflicts(
            @ApiParam("限制数量") @RequestParam(defaultValue = "10") int limit) {
        
        try {
            List<Map<String, Object>> conflicts = timestampService.detectConflicts(limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("conflicts", conflicts);
            response.put("count", conflicts.size());
            response.put("detectedAt", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error detecting conflicts: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 同步所有时间戳
     */
    @PostMapping("/sync-all")
    @ApiOperation("同步所有时间戳")
    public ResponseEntity<Map<String, Object>> syncAllTimestamps() {
        try {
            Map<String, Object> result = timestampService.syncAllTimestamps();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error syncing all timestamps: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 创建事件请求DTO
     */
    public static class CreateEventRequest {
        @NotBlank(message = "事件类型不能为空")
        private String eventType;
        
        private Map<String, Object> eventData = new HashMap<>();
        
        public String getEventType() {
            return eventType;
        }
        
        public void setEventType(String eventType) {
            this.eventType = eventType;
        }
        
        public Map<String, Object> getEventData() {
            return eventData;
        }
        
        public void setEventData(Map<String, Object> eventData) {
            this.eventData = eventData;
        }
    }
    
    /**
     * 同步事件请求DTO
     */
    public static class SyncEventRequest {
        @NotBlank(message = "源节点ID不能为空")
        private String sourceNodeId;
        
        @NotNull(message = "Lamport时间戳不能为空")
        private Long lamportTimestamp;
        
        private Map<String, Long> vectorClock = new HashMap<>();
        private Map<String, Long> versionVector = new HashMap<>();
        
        @NotBlank(message = "事件类型不能为空")
        private String eventType;
        
        private Map<String, Object> eventData = new HashMap<>();
        
        public String getSourceNodeId() {
            return sourceNodeId;
        }
        
        public void setSourceNodeId(String sourceNodeId) {
            this.sourceNodeId = sourceNodeId;
        }
        
        public Long getLamportTimestamp() {
            return lamportTimestamp;
        }
        
        public void setLamportTimestamp(Long lamportTimestamp) {
            this.lamportTimestamp = lamportTimestamp;
        }
        
        public Map<String, Long> getVectorClock() {
            return vectorClock;
        }
        
        public void setVectorClock(Map<String, Long> vectorClock) {
            this.vectorClock = vectorClock;
        }
        
        public Map<String, Long> getVersionVector() {
            return versionVector;
        }
        
        public void setVersionVector(Map<String, Long> versionVector) {
            this.versionVector = versionVector;
        }
        
        public String getEventType() {
            return eventType;
        }
        
        public void setEventType(String eventType) {
            this.eventType = eventType;
        }
        
        public Map<String, Object> getEventData() {
            return eventData;
        }
        
        public void setEventData(Map<String, Object> eventData) {
            this.eventData = eventData;
        }
    }
}