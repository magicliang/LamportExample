package com.example.dts.timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lamport逻辑时钟管理器
 * 实现分布式系统中的逻辑时钟，确保事件的因果排序
 * 
 * @author DTS Team
 */
@Component
public class LamportClockManager {
    
    private static final Logger logger = LoggerFactory.getLogger(LamportClockManager.class);
    
    private static final String LAMPORT_CLOCK_KEY = "lamport:clock:";
    private static final String GLOBAL_CLOCK_KEY = "lamport:global";
    
    private final AtomicLong logicalClock = new AtomicLong(0);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${dts.node.id}")
    private String nodeId;
    
    @Value("${dts.timestamp.lamport.sync-interval:1000}")
    private long syncInterval;
    
    @Value("${dts.timestamp.lamport.persistence-enabled:true}")
    private boolean persistenceEnabled;
    
    public LamportClockManager(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @PostConstruct
    public void initialize() {
        // 从Redis恢复时钟状态
        if (persistenceEnabled) {
            recoverClockFromRedis();
        }
        
        logger.info("Lamport clock manager initialized for node: {}, current time: {}", 
                   nodeId, logicalClock.get());
    }
    
    /**
     * 时钟滴答，返回新的逻辑时间
     */
    public long tick() {
        lock.writeLock().lock();
        try {
            long newTime = logicalClock.incrementAndGet();
            
            // 异步持久化到Redis
            if (persistenceEnabled) {
                persistClockToRedis(newTime);
            }
            
            logger.debug("Clock tick for node {}: {}", nodeId, newTime);
            return newTime;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 与接收到的时间戳同步
     * 根据Lamport时钟算法：max(local_time, received_time) + 1
     */
    public long sync(long receivedTimestamp) {
        lock.writeLock().lock();
        try {
            long currentTime = logicalClock.get();
            long newTime = Math.max(currentTime, receivedTimestamp) + 1;
            logicalClock.set(newTime);
            
            // 异步持久化到Redis
            if (persistenceEnabled) {
                persistClockToRedis(newTime);
            }
            
            logger.debug("Clock sync for node {}: current={}, received={}, new={}", 
                        nodeId, currentTime, receivedTimestamp, newTime);
            return newTime;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取当前逻辑时间
     */
    public long getCurrentTime() {
        lock.readLock().lock();
        try {
            return logicalClock.get();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 设置逻辑时间（主要用于恢复）
     */
    public void setCurrentTime(long time) {
        lock.writeLock().lock();
        try {
            logicalClock.set(time);
            logger.info("Clock set for node {}: {}", nodeId, time);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 与其他节点同步时钟
     */
    public long syncWithNode(String otherNodeId) {
        try {
            String otherClockKey = LAMPORT_CLOCK_KEY + otherNodeId;
            String otherTimeStr = redisTemplate.opsForValue().get(otherClockKey);
            
            if (otherTimeStr != null) {
                long otherTime = Long.parseLong(otherTimeStr);
                return sync(otherTime);
            } else {
                logger.warn("Cannot sync with node {}: clock not found", otherNodeId);
                return tick();
            }
        } catch (Exception e) {
            logger.error("Error syncing with node {}: {}", otherNodeId, e.getMessage());
            return tick();
        }
    }
    
    /**
     * 获取全局最大时钟值
     */
    public long getGlobalMaxClock() {
        try {
            String globalTimeStr = redisTemplate.opsForValue().get(GLOBAL_CLOCK_KEY);
            return globalTimeStr != null ? Long.parseLong(globalTimeStr) : 0L;
        } catch (Exception e) {
            logger.error("Error getting global max clock: {}", e.getMessage());
            return 0L;
        }
    }
    
    /**
     * 更新全局最大时钟值
     */
    public void updateGlobalMaxClock(long time) {
        try {
            // 使用Redis的原子操作确保全局时钟单调递增
            String script = 
                "local current = redis.call('GET', KEYS[1]) " +
                "if current == false or tonumber(current) < tonumber(ARGV[1]) then " +
                "  redis.call('SET', KEYS[1], ARGV[1]) " +
                "  return ARGV[1] " +
                "else " +
                "  return current " +
                "end";
            
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                return connection.eval(script.getBytes(), 
                                     java.util.Collections.singletonList(GLOBAL_CLOCK_KEY.getBytes()),
                                     java.util.Collections.singletonList(String.valueOf(time).getBytes()));
            });
        } catch (Exception e) {
            logger.error("Error updating global max clock: {}", e.getMessage());
        }
    }
    
    /**
     * 从Redis恢复时钟状态
     */
    private void recoverClockFromRedis() {
        try {
            String clockKey = LAMPORT_CLOCK_KEY + nodeId;
            String timeStr = redisTemplate.opsForValue().get(clockKey);
            
            if (timeStr != null) {
                long recoveredTime = Long.parseLong(timeStr);
                logicalClock.set(recoveredTime);
                logger.info("Recovered Lamport clock for node {}: {}", nodeId, recoveredTime);
            } else {
                // 如果没有恢复数据，尝试与全局时钟同步
                long globalTime = getGlobalMaxClock();
                if (globalTime > 0) {
                    logicalClock.set(globalTime);
                    logger.info("Initialized Lamport clock from global time for node {}: {}", 
                               nodeId, globalTime);
                }
            }
        } catch (Exception e) {
            logger.error("Error recovering clock from Redis: {}", e.getMessage());
        }
    }
    
    /**
     * 持久化时钟到Redis
     */
    private void persistClockToRedis(long time) {
        try {
            String clockKey = LAMPORT_CLOCK_KEY + nodeId;
            redisTemplate.opsForValue().set(clockKey, String.valueOf(time));
            
            // 更新全局最大时钟
            updateGlobalMaxClock(time);
        } catch (Exception e) {
            logger.error("Error persisting clock to Redis: {}", e.getMessage());
        }
    }
    
    /**
     * 重置时钟（主要用于测试）
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            logicalClock.set(0);
            if (persistenceEnabled) {
                try {
                    String clockKey = LAMPORT_CLOCK_KEY + nodeId;
                    redisTemplate.delete(clockKey);
                } catch (Exception e) {
                    logger.error("Error resetting clock in Redis: {}", e.getMessage());
                }
            }
            logger.info("Lamport clock reset for node: {}", nodeId);
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