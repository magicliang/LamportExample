package com.example.dts.repository;

import com.example.dts.model.TimestampEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 时间戳事件数据访问层
 * 
 * @author DTS Team
 */
@Repository
public interface TimestampEventRepository extends JpaRepository<TimestampEvent, Long> {
    
    /**
     * 根据节点ID查找事件，按创建时间降序排列
     */
    List<TimestampEvent> findByNodeIdOrderByCreatedAtDesc(String nodeId, Pageable pageable);
    
    /**
     * 根据节点ID和事件类型查找事件
     */
    List<TimestampEvent> findByNodeIdAndEventTypeOrderByCreatedAtDesc(String nodeId, String eventType, Pageable pageable);
    
    /**
     * 根据Lamport时间戳范围查找事件
     */
    List<TimestampEvent> findByLamportTimestampBetweenOrderByLamportTimestamp(Long startTime, Long endTime);
    
    /**
     * 根据创建时间范围查找事件，按Lamport时间戳排序
     */
    List<TimestampEvent> findByCreatedAtBetweenOrderByLamportTimestamp(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 查找指定节点的最新事件
     */
    TimestampEvent findTopByNodeIdOrderByCreatedAtDesc(String nodeId);
    
    /**
     * 查找指定节点的最大Lamport时间戳
     */
    @Query("SELECT MAX(t.lamportTimestamp) FROM TimestampEvent t WHERE t.nodeId = :nodeId")
    Long findMaxLamportTimestampByNodeId(@Param("nodeId") String nodeId);
    
    /**
     * 查找所有节点的最新事件
     */
    @Query("SELECT t FROM TimestampEvent t WHERE t.id IN " +
           "(SELECT MAX(t2.id) FROM TimestampEvent t2 GROUP BY t2.nodeId)")
    List<TimestampEvent> findLatestEventsByAllNodes();
    
    /**
     * 查找最近的事件（用于冲突检测）
     */
    @Query("SELECT t FROM TimestampEvent t ORDER BY t.createdAt DESC")
    List<TimestampEvent> findTop100ByOrderByCreatedAtDesc();
    
    /**
     * 根据事件类型统计数量
     */
    @Query("SELECT t.eventType, COUNT(t) FROM TimestampEvent t GROUP BY t.eventType")
    List<Object[]> countByEventType();
    
    /**
     * 根据节点ID统计事件数量
     */
    @Query("SELECT t.nodeId, COUNT(t) FROM TimestampEvent t GROUP BY t.nodeId")
    List<Object[]> countByNodeId();
    
    /**
     * 查找指定时间范围内的事件数量
     */
    @Query("SELECT COUNT(t) FROM TimestampEvent t WHERE t.createdAt BETWEEN :startTime AND :endTime")
    Long countByCreatedAtBetween(@Param("startTime") LocalDateTime startTime, 
                                @Param("endTime") LocalDateTime endTime);
    
    /**
     * 删除指定时间之前的事件（用于数据清理）
     */
    void deleteByCreatedAtBefore(LocalDateTime cutoffTime);
    
    /**
     * 查找可能存在冲突的事件对
     */
    @Query("SELECT t1, t2 FROM TimestampEvent t1, TimestampEvent t2 " +
           "WHERE t1.id < t2.id " +
           "AND t1.nodeId != t2.nodeId " +
           "AND t1.createdAt BETWEEN :startTime AND :endTime " +
           "AND t2.createdAt BETWEEN :startTime AND :endTime " +
           "AND t1.versionVector IS NOT NULL " +
           "AND t2.versionVector IS NOT NULL")
    List<Object[]> findPotentialConflictEvents(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);
}