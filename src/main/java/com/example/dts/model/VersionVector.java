package com.example.dts.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 版本向量实现
 * 用于多版本并发控制和冲突检测
 * 
 * @author DTS Team
 */
public class VersionVector {
    
    private final Map<String, Long> vector;
    
    public VersionVector() {
        this.vector = new ConcurrentHashMap<>();
    }
    
    @JsonCreator
    public VersionVector(@JsonProperty("vector") Map<String, Long> vector) {
        this.vector = new ConcurrentHashMap<>(vector != null ? vector : new HashMap<>());
    }
    
    /**
     * 复制构造函数
     */
    public VersionVector(VersionVector other) {
        this.vector = new ConcurrentHashMap<>(other.vector);
    }
    
    /**
     * 增加指定节点的版本号
     */
    public synchronized VersionVector increment(String nodeId) {
        VersionVector newVector = new VersionVector(this);
        newVector.vector.merge(nodeId, 1L, Long::sum);
        return newVector;
    }
    
    /**
     * 设置指定节点的版本号
     */
    public synchronized VersionVector set(String nodeId, long version) {
        VersionVector newVector = new VersionVector(this);
        newVector.vector.put(nodeId, version);
        return newVector;
    }
    
    /**
     * 与另一个版本向量合并
     * 对每个节点取最大版本号
     */
    public synchronized VersionVector merge(VersionVector other) {
        VersionVector merged = new VersionVector();
        
        // 合并当前向量
        for (Map.Entry<String, Long> entry : this.vector.entrySet()) {
            String nodeId = entry.getKey();
            long thisVersion = entry.getValue();
            long otherVersion = other.vector.getOrDefault(nodeId, 0L);
            merged.vector.put(nodeId, Math.max(thisVersion, otherVersion));
        }
        
        // 添加其他向量中的新节点
        for (Map.Entry<String, Long> entry : other.vector.entrySet()) {
            String nodeId = entry.getKey();
            if (!merged.vector.containsKey(nodeId)) {
                merged.vector.put(nodeId, entry.getValue());
            }
        }
        
        return merged;
    }
    
    /**
     * 检查是否与另一个版本向量存在冲突
     * 如果两个向量都有对方没有的更新，则存在冲突
     */
    public boolean hasConflict(VersionVector other) {
        boolean thisHasNewer = false;
        boolean otherHasNewer = false;
        
        // 获取所有节点ID
        Map<String, Long> allNodes = new HashMap<>(this.vector);
        other.vector.forEach((k, v) -> allNodes.putIfAbsent(k, 0L));
        
        for (String nodeId : allNodes.keySet()) {
            long thisVersion = this.vector.getOrDefault(nodeId, 0L);
            long otherVersion = other.vector.getOrDefault(nodeId, 0L);
            
            if (thisVersion > otherVersion) {
                thisHasNewer = true;
            } else if (otherVersion > thisVersion) {
                otherHasNewer = true;
            }
            
            // 如果双方都有更新，则存在冲突
            if (thisHasNewer && otherHasNewer) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 比较两个版本向量的关系
     */
    public VectorRelation compareTo(VersionVector other) {
        if (this.equals(other)) {
            return VectorRelation.EQUAL;
        }
        
        boolean thisIsNewer = false;
        boolean otherIsNewer = false;
        
        // 获取所有节点ID
        Map<String, Long> allNodes = new HashMap<>(this.vector);
        other.vector.forEach((k, v) -> allNodes.putIfAbsent(k, 0L));
        
        for (String nodeId : allNodes.keySet()) {
            long thisVersion = this.vector.getOrDefault(nodeId, 0L);
            long otherVersion = other.vector.getOrDefault(nodeId, 0L);
            
            if (thisVersion > otherVersion) {
                thisIsNewer = true;
            } else if (otherVersion > thisVersion) {
                otherIsNewer = true;
            }
        }
        
        if (thisIsNewer && !otherIsNewer) {
            return VectorRelation.NEWER;
        } else if (otherIsNewer && !thisIsNewer) {
            return VectorRelation.OLDER;
        } else if (thisIsNewer && otherIsNewer) {
            return VectorRelation.CONFLICT;
        } else {
            return VectorRelation.EQUAL;
        }
    }
    
    /**
     * 检查是否比另一个版本向量更新
     */
    public boolean isNewerThan(VersionVector other) {
        return this.compareTo(other) == VectorRelation.NEWER;
    }
    
    /**
     * 检查是否比另一个版本向量更旧
     */
    public boolean isOlderThan(VersionVector other) {
        return this.compareTo(other) == VectorRelation.OLDER;
    }
    
    /**
     * 检查是否与另一个版本向量冲突
     */
    public boolean isConflictWith(VersionVector other) {
        return this.compareTo(other) == VectorRelation.CONFLICT;
    }
    
    /**
     * 获取指定节点的版本号
     */
    public long getVersion(String nodeId) {
        return vector.getOrDefault(nodeId, 0L);
    }
    
    /**
     * 获取向量的副本
     */
    public Map<String, Long> getVector() {
        return new HashMap<>(vector);
    }
    
    /**
     * 获取所有节点ID
     */
    public java.util.Set<String> getNodeIds() {
        return vector.keySet();
    }
    
    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return vector.isEmpty();
    }
    
    /**
     * 获取向量大小（节点数量）
     */
    public int size() {
        return vector.size();
    }
    
    /**
     * 清空向量
     */
    public synchronized void clear() {
        vector.clear();
    }
    
    /**
     * 获取向量的总和（所有版本号之和）
     */
    public long getSum() {
        return vector.values().stream().mapToLong(Long::longValue).sum();
    }
    
    /**
     * 获取最大版本号
     */
    public long getMaxVersion() {
        return vector.values().stream().mapToLong(Long::longValue).max().orElse(0L);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VersionVector that = (VersionVector) obj;
        return Objects.equals(vector, that.vector);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(vector);
    }
    
    @Override
    public String toString() {
        return "VersionVector{" + vector + "}";
    }
    
    /**
     * 版本向量关系枚举
     */
    public enum VectorRelation {
        NEWER,      // 更新
        OLDER,      // 更旧
        CONFLICT,   // 冲突
        EQUAL       // 相等
    }
}