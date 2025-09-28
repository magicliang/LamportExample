package com.example.dts.timestamp;

import com.example.dts.model.VersionVector;
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
 * 版本向量管理器
 * 管理分布式系统中的版本向量，支持多版本并发控制
 * 
 * @author DTS Team
 */
@Component
public class VersionVectorManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VersionVectorManager.class);
    
    private static final String VERSION_VECTOR_KEY = "version:vector:";
    private static final String VERSION_HISTORY_KEY = "version:history:";
    private static final String NODE_LIST_KEY = "version:nodes";
    
    private VersionVector currentVector;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${dts.node.id}")
    private String nodeId;
    
    @Value("${dts.timestamp.vector.max-nodes:100}")
    private int maxNodes;
    
    @Value("${dts.timestamp.vector.cleanup-interval:3600000}")
    private long cleanupInterval;
    
    public VersionVectorManager(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.currentVector = new VersionVector();
    }
    
    @PostConstruct
    public void initialize() {
        // 从Redis恢复版本向量状态
        recoverVectorFromRedis();
        
        // 注册当前节点
        registerNode(nodeId);
        
        logger.info("Version vector manager initialized for node: {}, current vector: {}", 
                   nodeId, currentVector);
    }
    
    /**
     * 增加当前节点的版本号
     */
    public VersionVector increment() {
        lock.writeLock().lock();
        try {
            currentVector = currentVector.increment(nodeId);
            
            // 持久化到Redis
            persistVectorToRedis();
            
            // 保存版本历史
            saveVersionHistory();
            
            logger.debug("Version vector increment for node {}: {}", nodeId, currentVector);
            return new VersionVector(currentVector);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 设置指定节点的版本号
     */
    public VersionVector set(String targetNodeId, long version) {
        lock.writeLock().lock();
        try {
            currentVector = currentVector.set(targetNodeId, version);
            
            // 持久化到Redis
            persistVectorToRedis();
            
            logger.debug("Version vector set for node {} -> {}: {}", 
                        targetNodeId, version, currentVector);
            return new VersionVector(currentVector);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 与另一个版本向量合并
     */
    public VersionVector merge(VersionVector otherVector) {
        lock.writeLock().lock();
        try {
            VersionVector oldVector = new VersionVector(currentVector);
            currentVector = currentVector.merge(otherVector);
            
            // 持久化到Redis
            persistVectorToRedis();
            
            // 保存合并历史
            saveMergeHistory(oldVector, otherVector);
            
            logger.debug("Version vector merge for node {}: old={}, other={}, new={}", 
                        nodeId, oldVector, otherVector, currentVector);
            return new VersionVector(currentVector);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取当前版本向量
     */
    public VersionVector getCurrentVector() {
        lock.readLock().lock();
        try {
            return new VersionVector(currentVector);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 设置版本向量（主要用于恢复）
     */
    public void setCurrentVector(VersionVector vector) {
        lock.writeLock().lock();
        try {
            currentVector = new VersionVector(vector);
            persistVectorToRedis();
            logger.info("Version vector set for node {}: {}", nodeId, currentVector);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 检查是否与另一个版本向量存在冲突
     */
    public boolean hasConflict(VersionVector otherVector) {
        lock.readLock().lock();
        try {
            return currentVector.hasConflict(otherVector);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 比较两个版本向量的关系
     */
    public VersionVector.VectorRelation compare(VersionVector vector1, VersionVector vector2) {
        return vector1.compareTo(vector2);
    }
    
    /**
     * 获取指定节点的版本向量
     */
    public VersionVector getNodeVector(String targetNodeId) {
        try {
            String vectorKey = VERSION_VECTOR_KEY + targetNodeId;
            String vectorJson = redisTemplate.opsForValue().get(vectorKey);
            
            if (vectorJson != null) {
                Map<String, Long> vectorMap = objectMapper.readValue(vectorJson, Map.class);
                return new VersionVector(vectorMap);
            } else {
                logger.warn("Version vector not found for node: {}", targetNodeId);
                return new VersionVector();
            }
        } catch (Exception e) {
            logger.error("Error getting version vector for node {}: {}", targetNodeId, e.getMessage());
            return new VersionVector();
        }
    }
    
    /**
     * 获取所有已知节点的版本向量
     */
    public Map<String, VersionVector> getAllNodeVectors() {
        Map<String, VersionVector> nodeVectors = new ConcurrentHashMap<>();
        
        try {
            java.util.Set<String> nodeIds = redisTemplate.opsForSet().members(NODE_LIST_KEY);
            if (nodeIds != null) {
                for (String nodeId : nodeIds) {
                    VersionVector vector = getNodeVector(nodeId);
                    if (!vector.isEmpty()) {
                        nodeVectors.put(nodeId, vector);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error getting all node vectors: {}", e.getMessage());
        }
        
        return nodeVectors;
    }
    
    /**
     * 检测并解决冲突
     */
    public VersionVector resolveConflict(VersionVector conflictVector, String strategy) {
        lock.writeLock().lock();
        try {
            VersionVector resolvedVector;
            
            switch (strategy.toLowerCase()) {
                case "merge":
                    // 合并策略：取每个节点的最大版本号
                    resolvedVector = currentVector.merge(conflictVector);
                    break;
                case "last-write-wins":
                    // 最后写入获胜策略：比较总版本号
                    if (conflictVector.getSum() > currentVector.getSum()) {
                        resolvedVector = new VersionVector(conflictVector);
                    } else {
                        resolvedVector = new VersionVector(currentVector);
                    }
                    break;
                case "manual":
                    // 手动解决策略：保持当前状态，等待手动干预
                    resolvedVector = new VersionVector(currentVector);
                    logger.warn("Manual conflict resolution required for node {}", nodeId);
                    break;
                default:
                    // 默认使用合并策略
                    resolvedVector = currentVector.merge(conflictVector);
                    break;
            }
            
            currentVector = resolvedVector;
            persistVectorToRedis();
            
            logger.info("Conflict resolved for node {} using strategy {}: {}", 
                       nodeId, strategy, resolvedVector);
            return new VersionVector(resolvedVector);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取版本历史
     */
    public java.util.List<VersionVector> getVersionHistory(int limit) {
        java.util.List<VersionVector> history = new java.util.ArrayList<>();
        
        try {
            String historyKey = VERSION_HISTORY_KEY + nodeId;
            java.util.List<String> historyJson = redisTemplate.opsForList().range(historyKey, 0, limit - 1);
            
            if (historyJson != null) {
                for (String json : historyJson) {
                    Map<String, Long> vectorMap = objectMapper.readValue(json, Map.class);
                    history.add(new VersionVector(vectorMap));
                }
            }
        } catch (Exception e) {
            logger.error("Error getting version history: {}", e.getMessage());
        }
        
        return history;
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
            String vectorKey = VERSION_VECTOR_KEY + nodeId;
            String historyKey = VERSION_HISTORY_KEY + nodeId;
            redisTemplate.delete(vectorKey);
            redisTemplate.delete(historyKey);
            logger.info("Node unregistered: {}", nodeId);
        } catch (Exception e) {
            logger.error("Error unregistering node {}: {}", nodeId, e.getMessage());
        }
    }
    
    /**
     * 从Redis恢复版本向量状态
     */
    private void recoverVectorFromRedis() {
        try {
            String vectorKey = VERSION_VECTOR_KEY + nodeId;
            String vectorJson = redisTemplate.opsForValue().get(vectorKey);
            
            if (vectorJson != null) {
                Map<String, Long> vectorMap = objectMapper.readValue(vectorJson, Map.class);
                currentVector = new VersionVector(vectorMap);
                logger.info("Recovered version vector for node {}: {}", nodeId, currentVector);
            }
        } catch (Exception e) {
            logger.error("Error recovering version vector from Redis: {}", e.getMessage());
        }
    }
    
    /**
     * 持久化版本向量到Redis
     */
    private void persistVectorToRedis() {
        try {
            String vectorKey = VERSION_VECTOR_KEY + nodeId;
            String vectorJson = objectMapper.writeValueAsString(currentVector.getVector());
            redisTemplate.opsForValue().set(vectorKey, vectorJson);
        } catch (JsonProcessingException e) {
            logger.error("Error persisting version vector to Redis: {}", e.getMessage());
        }
    }
    
    /**
     * 保存版本历史
     */
    private void saveVersionHistory() {
        try {
            String historyKey = VERSION_HISTORY_KEY + nodeId;
            String vectorJson = objectMapper.writeValueAsString(currentVector.getVector());
            
            // 添加到历史列表头部
            redisTemplate.opsForList().leftPush(historyKey, vectorJson);
            
            // 限制历史记录数量
            redisTemplate.opsForList().trim(historyKey, 0, 99); // 保留最近100条记录
        } catch (JsonProcessingException e) {
            logger.error("Error saving version history: {}", e.getMessage());
        }
    }
    
    /**
     * 保存合并历史
     */
    private void saveMergeHistory(VersionVector oldVector, VersionVector otherVector) {
        try {
            String mergeKey = "version:merge:" + nodeId;
            Map<String, Object> mergeInfo = new ConcurrentHashMap<>();
            mergeInfo.put("timestamp", System.currentTimeMillis());
            mergeInfo.put("oldVector", oldVector.getVector());
            mergeInfo.put("otherVector", otherVector.getVector());
            mergeInfo.put("newVector", currentVector.getVector());
            
            String mergeJson = objectMapper.writeValueAsString(mergeInfo);
            redisTemplate.opsForList().leftPush(mergeKey, mergeJson);
            redisTemplate.opsForList().trim(mergeKey, 0, 49); // 保留最近50条合并记录
        } catch (JsonProcessingException e) {
            logger.error("Error saving merge history: {}", e.getMessage());
        }
    }
    
    /**
     * 重置版本向量（主要用于测试）
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            currentVector = new VersionVector();
            try {
                String vectorKey = VERSION_VECTOR_KEY + nodeId;
                String historyKey = VERSION_HISTORY_KEY + nodeId;
                redisTemplate.delete(vectorKey);
                redisTemplate.delete(historyKey);
            } catch (Exception e) {
                logger.error("Error resetting version vector in Redis: {}", e.getMessage());
            }
            logger.info("Version vector reset for node: {}", nodeId);
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