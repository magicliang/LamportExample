package com.example.dts.timestamp;

import com.example.dts.model.VectorClock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 向量时钟管理器
 * 管理分布式系统中的向量时钟，追踪事件的因果关系
 * 
 * @author DTS Team
 */
@Component
public class VectorClockManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorClockManager.class);
    
    private static final String VECTOR_CLOCK_KEY = "vector:clock:";
    private static final String NODE_LIST_KEY = "vector:nodes";
    
    private VectorClock currentClock;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${dts.node.id}")
    private String nodeId;
    
    @Value("${dts.timestamp.vector-clock.max-entries:1000}")
    private int maxEntries;
    
    @Value("${dts.timestamp.vector-clock.gc-threshold:0.8}")
    private double gcThreshold;
    
    public VectorClockManager(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.currentClock = new VectorClock();
    }
    
    @PostConstruct
    public void initialize() {
        // 从Redis恢复向量时钟状态
        recoverClockFromRedis();
        
        // 注册当前节点
        registerNode(nodeId);
        
        logger.info("Vector clock manager initialized for node: {}, current clock: {}", 
                   nodeId, currentClock);
    }
    
    /**
     * 时钟滴答，增加当前节点的时钟值
     */
    public VectorClock tick() {
        lock.writeLock().lock();
        try {
            currentClock = currentClock.tick(nodeId);
            
            // 持久化到Redis
            persistClockToRedis();
            
            // 检查是否需要垃圾回收
            if (shouldPerformGC()) {
                performGC();
            }
            
            logger.debug("Vector clock tick for node {}: {}", nodeId, currentClock);
            return new VectorClock(currentClock);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 与接收到的向量时钟同步
     */
    public VectorClock sync(VectorClock receivedClock) {
        lock.writeLock().lock();
        try {
            currentClock = currentClock.sync(receivedClock, nodeId);
            
            // 持久化到Redis
            persistClockToRedis();
            
            logger.debug("Vector clock sync for node {}: received={}, new={}", 
                        nodeId, receivedClock, currentClock);
            return new VectorClock(currentClock);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取当前向量时钟
     */
    public VectorClock getCurrentClock() {
        lock.readLock().lock();
        try {
            return new VectorClock(currentClock);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 设置向量时钟（主要用于恢复）
     */
    public void setCurrentClock(VectorClock clock) {
        lock.writeLock().lock();
        try {
            currentClock = new VectorClock(clock);
            persistClockToRedis();
            logger.info("Vector clock set for node {}: {}", nodeId, currentClock);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 比较两个向量时钟的关系
     */
    public VectorClock.ClockRelation compare(VectorClock clock1, VectorClock clock2) {
        return clock1.compareTo(clock2);
    }
    
    /**
     * 检查两个事件是否存在因果关系
     */
    public boolean hasCausalRelation(VectorClock clock1, VectorClock clock2) {
        VectorClock.ClockRelation relation = clock1.compareTo(clock2);
        return relation == VectorClock.ClockRelation.BEFORE || 
               relation == VectorClock.ClockRelation.AFTER;
    }
    
    /**
     * 检查两个事件是否并发
     */
    public boolean areConcurrent(VectorClock clock1, VectorClock clock2) {
        return clock1.compareTo(clock2) == VectorClock.ClockRelation.CONCURRENT;
    }
    
    /**
     * 获取指定节点的向量时钟
     */
    public VectorClock getNodeClock(String targetNodeId) {
        try {
            String clockKey = VECTOR_CLOCK_KEY + targetNodeId;
            String clockJson = redisTemplate.opsForValue().get(clockKey);
            
            if (clockJson != null) {
                Map<String, Long> clockMap = objectMapper.readValue(clockJson, Map.class);
                return new VectorClock(clockMap);
            } else {
                logger.warn("Clock not found for node: {}", targetNodeId);
                return new VectorClock();
            }
        } catch (Exception e) {
            logger.error("Error getting clock for node {}: {}", targetNodeId, e.getMessage());
            return new VectorClock();
        }
    }
    
    /**
     * 获取所有已知节点的时钟
     */
    public Map<String, VectorClock> getAllNodeClocks() {
        Map<String, VectorClock> nodeClocks = new ConcurrentHashMap<>();
        
        try {
            java.util.Set<String> nodeIds = redisTemplate.opsForSet().members(NODE_LIST_KEY);
            if (nodeIds != null) {
                for (String nodeId : nodeIds) {
                    VectorClock clock = getNodeClock(nodeId);
                    if (!clock.isEmpty()) {
                        nodeClocks.put(nodeId, clock);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error getting all node clocks: {}", e.getMessage());
        }
        
        return nodeClocks;
    }
    
    /**
     * 注册节点
     */
    public void registerNode(String nodeId) {
        try {
            redisTemplate.opsForSet().add(NODE_LIST_KEY, nodeId);
            logger.debug("Node registered: {}", nodeId);
        } catch (Exception e) {
            logger.error("Error registering node {}: {}", nodeId, e.getMessage());
        }
    }
    
    /**
     * 注销节点
     */
    public void unregisterNode(String nodeId) {
        try {
            redisTemplate.opsForSet().remove(NODE_LIST_KEY, nodeId);
            String clockKey = VECTOR_CLOCK_KEY + nodeId;
            redisTemplate.delete(clockKey);
            logger.info("Node unregistered: {}", nodeId);
        } catch (Exception e) {
            logger.error("Error unregistering node {}: {}", nodeId, e.getMessage());
        }
    }
    
    /**
     * 从Redis恢复向量时钟状态
     */
    private void recoverClockFromRedis() {
        try {
            String clockKey = VECTOR_CLOCK_KEY + nodeId;
            String clockJson = redisTemplate.opsForValue().get(clockKey);
            
            if (clockJson != null) {
                Map<String, Long> clockMap = objectMapper.readValue(clockJson, Map.class);
                currentClock = new VectorClock(clockMap);
                logger.info("Recovered vector clock for node {}: {}", nodeId, currentClock);
            }
        } catch (Exception e) {
            logger.error("Error recovering vector clock from Redis: {}", e.getMessage());
        }
    }
    
    /**
     * 持久化向量时钟到Redis
     */
    private void persistClockToRedis() {
        try {
            String clockKey = VECTOR_CLOCK_KEY + nodeId;
            String clockJson = objectMapper.writeValueAsString(currentClock.getClock());
            redisTemplate.opsForValue().set(clockKey, clockJson);
        } catch (JsonProcessingException e) {
            logger.error("Error persisting vector clock to Redis: {}", e.getMessage());
        }
    }
    
    /**
     * 检查是否需要执行垃圾回收
     */
    private boolean shouldPerformGC() {
        return currentClock.size() > maxEntries * gcThreshold;
    }
    
    /**
     * 执行垃圾回收，清理过期的时钟条目
     */
    private void performGC() {
        try {
            // 获取所有活跃节点
            java.util.Set<String> activeNodes = redisTemplate.opsForSet().members(NODE_LIST_KEY);
            
            if (activeNodes != null && activeNodes.size() < currentClock.size()) {
                // 创建新的时钟，只保留活跃节点
                Map<String, Long> newClockMap = new ConcurrentHashMap<>();
                for (String activeNode : activeNodes) {
                    long value = currentClock.getValue(activeNode);
                    if (value > 0) {
                        newClockMap.put(activeNode, value);
                    }
                }
                
                currentClock = new VectorClock(newClockMap);
                logger.info("Vector clock GC completed for node {}, entries: {} -> {}", 
                           nodeId, currentClock.size(), newClockMap.size());
            }
        } catch (Exception e) {
            logger.error("Error performing vector clock GC: {}", e.getMessage());
        }
    }
    
    /**
     * 重置向量时钟（主要用于测试）
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            currentClock = new VectorClock();
            try {
                String clockKey = VECTOR_CLOCK_KEY + nodeId;
                redisTemplate.delete(clockKey);
            } catch (Exception e) {
                logger.error("Error resetting vector clock in Redis: {}", e.getMessage());
            }
            logger.info("Vector clock reset for node: {}", nodeId);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取节点ID
     */
    public String getNodeId() {
        return nodeId;
    }
}