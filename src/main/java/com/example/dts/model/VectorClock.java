package com.example.dts.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量时钟实现
 * 用于追踪分布式系统中事件的因果关系
 * 
 * @author DTS Team
 */
public class VectorClock {
    
    private final Map<String, Long> clock;
    
    public VectorClock() {
        this.clock = new ConcurrentHashMap<>();
    }
    
    @JsonCreator
    public VectorClock(@JsonProperty("clock") Map<String, Long> clock) {
        this.clock = new ConcurrentHashMap<>(clock != null ? clock : new HashMap<>());
    }
    
    /**
     * 复制构造函数
     */
    public VectorClock(VectorClock other) {
        this.clock = new ConcurrentHashMap<>(other.clock);
    }
    
    /**
     * 增加指定节点的时钟值
     */
    public synchronized VectorClock tick(String nodeId) {
        VectorClock newClock = new VectorClock(this);
        newClock.clock.merge(nodeId, 1L, Long::sum);
        return newClock;
    }
    
    /**
     * 与另一个向量时钟同步
     * 取每个节点的最大值，然后增加当前节点的时钟
     */
    public synchronized VectorClock sync(VectorClock other, String currentNodeId) {
        VectorClock newClock = new VectorClock();
        
        // 合并所有节点的时钟值，取最大值
        for (String nodeId : this.clock.keySet()) {
            long thisValue = this.clock.get(nodeId);
            long otherValue = other.clock.getOrDefault(nodeId, 0L);
            newClock.clock.put(nodeId, Math.max(thisValue, otherValue));
        }
        
        for (String nodeId : other.clock.keySet()) {
            if (!newClock.clock.containsKey(nodeId)) {
                newClock.clock.put(nodeId, other.clock.get(nodeId));
            }
        }
        
        // 增加当前节点的时钟
        newClock.clock.merge(currentNodeId, 1L, Long::sum);
        
        return newClock;
    }
    
    /**
     * 比较两个向量时钟的关系
     */
    public ClockRelation compareTo(VectorClock other) {
        if (this.equals(other)) {
            return ClockRelation.EQUAL;
        }
        
        boolean thisLessOrEqual = true;
        boolean otherLessOrEqual = true;
        boolean hasStrictLess = false;
        boolean hasStrictGreater = false;
        
        // 获取所有节点ID
        Map<String, Long> allNodes = new HashMap<>(this.clock);
        other.clock.forEach((k, v) -> allNodes.putIfAbsent(k, 0L));
        
        for (String nodeId : allNodes.keySet()) {
            long thisValue = this.clock.getOrDefault(nodeId, 0L);
            long otherValue = other.clock.getOrDefault(nodeId, 0L);
            
            if (thisValue < otherValue) {
                thisLessOrEqual = true;
                otherLessOrEqual = false;
                hasStrictLess = true;
            } else if (thisValue > otherValue) {
                thisLessOrEqual = false;
                otherLessOrEqual = true;
                hasStrictGreater = true;
            }
        }
        
        if (thisLessOrEqual && hasStrictLess) {
            return ClockRelation.BEFORE;
        } else if (otherLessOrEqual && hasStrictGreater) {
            return ClockRelation.AFTER;
        } else {
            return ClockRelation.CONCURRENT;
        }
    }
    
    /**
     * 检查是否发生在另一个时钟之前
     */
    public boolean happensBefore(VectorClock other) {
        return this.compareTo(other) == ClockRelation.BEFORE;
    }
    
    /**
     * 检查是否发生在另一个时钟之后
     */
    public boolean happensAfter(VectorClock other) {
        return this.compareTo(other) == ClockRelation.AFTER;
    }
    
    /**
     * 检查是否与另一个时钟并发
     */
    public boolean isConcurrent(VectorClock other) {
        return this.compareTo(other) == ClockRelation.CONCURRENT;
    }
    
    /**
     * 获取指定节点的时钟值
     */
    public long getValue(String nodeId) {
        return clock.getOrDefault(nodeId, 0L);
    }
    
    /**
     * 设置指定节点的时钟值
     */
    public synchronized void setValue(String nodeId, long value) {
        clock.put(nodeId, value);
    }
    
    /**
     * 获取时钟的副本
     */
    public Map<String, Long> getClock() {
        return new HashMap<>(clock);
    }
    
    /**
     * 获取所有节点ID
     */
    public java.util.Set<String> getNodeIds() {
        return clock.keySet();
    }
    
    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return clock.isEmpty();
    }
    
    /**
     * 获取时钟大小（节点数量）
     */
    public int size() {
        return clock.size();
    }
    
    /**
     * 清空时钟
     */
    public synchronized void clear() {
        clock.clear();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VectorClock that = (VectorClock) obj;
        return Objects.equals(clock, that.clock);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(clock);
    }
    
    @Override
    public String toString() {
        return "VectorClock{" + clock + "}";
    }
    
    /**
     * 时钟关系枚举
     */
    public enum ClockRelation {
        BEFORE,     // 发生在之前
        AFTER,      // 发生在之后
        CONCURRENT, // 并发
        EQUAL       // 相等
    }
}