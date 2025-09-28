# 技术实现细节补充文档

## 目录

1. [核心算法实现](#核心算法实现)
2. [配置详解](#配置详解)
3. [关键代码实现](#关键代码实现)
4. [最佳实践](#最佳实践)
5. [故障排除](#故障排除)

---

## 核心算法实现

### 1.1 Lamport时间戳算法

#### 算法原理
Lamport时间戳是一种逻辑时钟，用于在分布式系统中对事件进行排序。

```java
@Component
public class LamportClockManager {
    private final AtomicLong logicalClock = new AtomicLong(0);
    private final RedisTemplate<String, String> redisTemplate;
    private final String nodeId;
    
    /**
     * 本地事件发生时递增时钟
     */
    public long tick() {
        long newTime = logicalClock.incrementAndGet();
        persistToRedis(newTime);
        return newTime;
    }
    
    /**
     * 接收到远程事件时同步时钟
     */
    public long sync(long receivedTimestamp) {
        long currentTime = logicalClock.get();
        long newTime = Math.max(currentTime, receivedTimestamp) + 1;
        logicalClock.set(newTime);
        persistToRedis(newTime);
        return newTime;
    }
    
    private void persistToRedis(long timestamp) {
        String key = "lamport:" + nodeId;
        redisTemplate.opsForValue().set(key, String.valueOf(timestamp), 
                                       Duration.ofHours(24));
    }
}
```

### 1.2 向量时钟算法

#### 算法原理
向量时钟为每个节点维护一个向量，记录对所有节点事件的观察。

```java
public class VectorClock {
    private final Map<String, Long> clock;
    
    public VectorClock(Map<String, Long> clock) {
        this.clock = new ConcurrentHashMap<>(clock);
    }
    
    /**
     * 本地事件发生时递增对应节点的时钟
     */
    public VectorClock tick(String nodeId) {
        Map<String, Long> newClock = new HashMap<>(this.clock);
        newClock.merge(nodeId, 1L, Long::sum);
        return new VectorClock(newClock);
    }
    
    /**
     * 接收远程事件时更新向量时钟
     */
    public VectorClock update(VectorClock other) {
        Map<String, Long> newClock = new HashMap<>(this.clock);
        other.clock.forEach((nodeId, timestamp) -> 
            newClock.merge(nodeId, timestamp, Long::max));
        return new VectorClock(newClock);
    }
    
    /**
     * 比较两个向量时钟的关系
     */
    public ClockRelation compareTo(VectorClock other) {
        boolean thisLessOrEqual = true;
        boolean otherLessOrEqual = true;
        boolean equal = true;
        
        Set<String> allNodes = new HashSet<>(this.clock.keySet());
        allNodes.addAll(other.clock.keySet());
        
        for (String nodeId : allNodes) {
            long thisTime = this.clock.getOrDefault(nodeId, 0L);
            long otherTime = other.clock.getOrDefault(nodeId, 0L);
            
            if (thisTime > otherTime) {
                otherLessOrEqual = false;
            }
            if (otherTime > thisTime) {
                thisLessOrEqual = false;
            }
            if (thisTime != otherTime) {
                equal = false;
            }
        }
        
        if (equal) return ClockRelation.EQUAL;
        if (thisLessOrEqual && !otherLessOrEqual) return ClockRelation.BEFORE;
        if (otherLessOrEqual && !thisLessOrEqual) return ClockRelation.AFTER;
        return ClockRelation.CONCURRENT;
    }
}
```

### 1.3 版本向量算法

#### 算法原理
版本向量用于多版本并发控制，支持冲突检测和版本合并。

```java
public class VersionVector {
    private final Map<String, Long> vector;
    
    /**
     * 检测版本冲突
     */
    public boolean hasConflictWith(VersionVector other) {
        Set<String> allNodes = new HashSet<>(this.vector.keySet());
        allNodes.addAll(other.vector.keySet());
        
        boolean thisNewer = false;
        boolean otherNewer = false;
        
        for (String nodeId : allNodes) {
            long thisVersion = this.vector.getOrDefault(nodeId, 0L);
            long otherVersion = other.vector.getOrDefault(nodeId, 0L);
            
            if (thisVersion > otherVersion) {
                thisNewer = true;
            } else if (otherVersion > thisVersion) {
                otherNewer = true;
            }
        }
        
        return thisNewer && otherNewer; // 双向都有更新则冲突
    }
    
    /**
     * 合并版本向量
     */
    public VersionVector merge(VersionVector other) {
        Map<String, Long> merged = new HashMap<>(this.vector);
        other.vector.forEach((nodeId, version) -> 
            merged.merge(nodeId, version, Long::max));
        return new VersionVector(merged);
    }
}
```

---

## 配置详解

### 2.1 数据库配置

#### MySQL配置优化
```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://11.142.154.110:3306/gfaeofw8?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048
    username: with_zqwjnhlyktudftww
    password: "IvE48V)DoJNcN&"
    hikari:
      maximum-pool-size: 20          # 最大连接数
      minimum-idle: 5                # 最小空闲连接
      idle-timeout: 300000           # 空闲超时时间(5分钟)
      connection-timeout: 20000      # 连接超时时间(20秒)
      max-lifetime: 1200000          # 连接最大生命周期(20分钟)
      leak-detection-threshold: 60000 # 连接泄漏检测阈值(1分钟)
      validation-timeout: 5000       # 连接验证超时时间
      connection-test-query: SELECT 1 # 连接测试查询
```

#### JTA事务配置
```yaml
spring:
  jta:
    enabled: true
    atomikos:
      datasource:
        borrow-connection-timeout: 30    # 借用连接超时时间
        default-isolation-level: 2       # 默认隔离级别(READ_COMMITTED)
        login-timeout: 0                 # 登录超时时间
        maintenance-interval: 60         # 维护间隔
        max-idle-time: 60               # 最大空闲时间
        max-lifetime: 0                 # 最大生命周期
        max-pool-size: 20               # 最大连接池大小
        min-pool-size: 5                # 最小连接池大小
        test-query: SELECT 1            # 测试查询
        xa-data-source-class-name: com.mysql.cj.jdbc.MysqlXADataSource
        xa-properties:
          url: jdbc:mysql://11.142.154.110:3306/gfaeofw8?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
          user: with_zqwjnhlyktudftww
          password: "IvE48V)DoJNcN&"
      properties:
        enable-logging: true            # 启用日志
        log-base-dir: ./logs           # 日志基础目录
        log-base-name: transactions    # 日志基础名称
        max-actives: 50                # 最大活跃事务数
        max-timeout: 300000            # 最大超时时间(5分钟)
        default-jta-timeout: 10000     # 默认JTA超时时间(10秒)
        serial-jta-transactions: true   # 串行JTA事务
        allow-sub-transactions: true    # 允许子事务
```

### 2.2 Redis配置

```yaml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    database: 0
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 20      # 最大活跃连接数
        max-idle: 10        # 最大空闲连接数
        min-idle: 5         # 最小空闲连接数
        max-wait: 2000ms    # 最大等待时间
      shutdown-timeout: 100ms
    connect-timeout: 2000ms
    command-timeout: 1000ms
```

### 2.3 Seata配置

```yaml
seata:
  enabled: true
  application-id: ${spring.application.name}
  tx-service-group: default_tx_group
  enable-auto-data-source-proxy: true
  data-source-proxy-mode: AT
  config:
    type: file
    file:
      name: file.conf
  registry:
    type: file
    file:
      name: registry.conf
  client:
    rm:
      async-commit-buffer-limit: 10000
      report-retry-count: 5
      table-meta-check-enable: false
      report-success-enable: false
      saga-branch-register-enable: false
      saga-json-parser: fastjson
      saga-retry-persist-mode-update: false
      saga-compensate-persist-mode-update: false
    tm:
      commit-retry-count: 5
      rollback-retry-count: 5
      default-global-transaction-timeout: 60000
      degrade-check: false
      degrade-check-period: 2000
      degrade-check-allow-times: 10
    undo:
      data-validation: true
      log-serialization: jackson
      log-table: undo_log
      only-care-update-columns: true
    log:
      exceptionRate: 100
```

---

## 关键代码实现

### 3.1 时间戳服务实现

```java
@Service
@Transactional
public class TimestampService {
    
    private final LamportClockManager lamportClockManager;
    private final VectorClockManager vectorClockManager;
    private final VersionVectorManager versionVectorManager;
    private final TimestampEventRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 创建时间戳事件
     */
    public TimestampEventResponse createEvent(TimestampEventRequest request) {
        // 生成Lamport时间戳
        long lamportTime = lamportClockManager.tick();
        
        // 生成向量时钟
        VectorClock vectorClock = vectorClockManager.tick();
        
        // 生成版本向量
        VersionVector versionVector = versionVectorManager.increment();
        
        // 创建事件实体
        TimestampEvent event = TimestampEvent.builder()
                .nodeId(vectorClockManager.getNodeId())
                .lamportTimestamp(lamportTime)
                .vectorClock(vectorClock.toJson())
                .versionVector(versionVector.toJson())
                .eventType(request.getEventType())
                .businessType(request.getBusinessType())
                .eventData(request.getEventData())
                .build();
        
        // 保存到数据库
        TimestampEvent savedEvent = repository.save(event);
        
        // 缓存到Redis
        cacheEvent(savedEvent);
        
        // 发布事件通知
        publishEventNotification(savedEvent);
        
        return TimestampEventResponse.from(savedEvent);
    }
    
    /**
     * 同步时间戳事件
     */
    public TimestampEventResponse syncEvent(TimestampSyncRequest request) {
        // 同步Lamport时钟
        long syncedLamportTime = lamportClockManager.sync(request.getLamportTimestamp());
        
        // 同步向量时钟
        VectorClock receivedVectorClock = VectorClock.fromJson(request.getVectorClock());
        VectorClock syncedVectorClock = vectorClockManager.update(receivedVectorClock);
        
        // 同步版本向量
        VersionVector receivedVersionVector = VersionVector.fromJson(request.getVersionVector());
        VersionVector syncedVersionVector = versionVectorManager.merge(receivedVersionVector);
        
        // 创建同步事件
        TimestampEvent syncEvent = TimestampEvent.builder()
                .nodeId(request.getSourceNodeId())
                .lamportTimestamp(syncedLamportTime)
                .vectorClock(syncedVectorClock.toJson())
                .versionVector(syncedVersionVector.toJson())
                .eventType(request.getEventType())
                .businessType(request.getBusinessType())
                .eventData(request.getEventData())
                .build();
        
        TimestampEvent savedEvent = repository.save(syncEvent);
        
        return TimestampEventResponse.from(savedEvent);
    }
    
    private void cacheEvent(TimestampEvent event) {
        String key = "event:" + event.getId();
        redisTemplate.opsForValue().set(key, event, Duration.ofHours(1));
    }
    
    private void publishEventNotification(TimestampEvent event) {
        EventNotification notification = EventNotification.builder()
                .eventId(event.getId())
                .nodeId(event.getNodeId())
                .eventType(event.getEventType())
                .timestamp(event.getCreatedAt())
                .build();
        
        redisTemplate.convertAndSend("timestamp-events", notification);
    }
}
```

### 3.2 分布式事务服务实现

```java
@Service
public class DistributedTransactionService {
    
    private final XATransactionService xaTransactionService;
    private final JTATransactionService jtaTransactionService;
    private final TransactionLogRepository transactionLogRepository;
    
    /**
     * 执行AT模式事务
     */
    @GlobalTransactional(rollbackFor = Exception.class, timeoutMills = 30000)
    public Map<String, Object> executeATTransaction(String businessType, 
                                                   Map<String, Object> businessData) {
        String xid = RootContext.getXID();
        
        TransactionLog log = createTransactionLog(xid, "AT", businessType, businessData);
        
        try {
            Map<String, Object> result = processATBusiness(businessType, businessData);
            
            updateTransactionLog(log, "COMMITTED", null);
            return result;
            
        } catch (Exception e) {
            updateTransactionLog(log, "ROLLBACK", e.getMessage());
            throw new TransactionException("AT transaction failed", e);
        }
    }
    
    /**
     * 执行TCC模式事务
     */
    @GlobalTransactional(rollbackFor = Exception.class)
    public Map<String, Object> executeTCCTransaction(String businessType, 
                                                    Map<String, Object> businessData) {
        String xid = RootContext.getXID();
        
        TransactionLog log = createTransactionLog(xid, "TCC", businessType, businessData);
        
        try {
            // Try阶段
            boolean tryResult = tryTCCBusiness(businessType, businessData);
            if (!tryResult) {
                throw new TransactionException("TCC Try phase failed");
            }
            
            // Confirm阶段由Seata自动调用
            Map<String, Object> result = Map.of(
                "xid", xid,
                "status", "SUCCESS",
                "businessType", businessType
            );
            
            updateTransactionLog(log, "COMMITTED", null);
            return result;
            
        } catch (Exception e) {
            // Cancel阶段由Seata自动调用
            updateTransactionLog(log, "ROLLBACK", e.getMessage());
            throw new TransactionException("TCC transaction failed", e);
        }
    }
    
    /**
     * 执行SAGA模式事务
     */
    @SagaOrchestrationStart
    public Map<String, Object> executeSAGATransaction(String businessType, 
                                                     Map<String, Object> businessData) {
        String sagaId = generateSagaId();
        
        TransactionLog log = createTransactionLog(sagaId, "SAGA", businessType, businessData);
        
        try {
            Map<String, Object> result = processSAGABusiness(businessType, businessData);
            
            updateTransactionLog(log, "COMMITTED", null);
            return result;
            
        } catch (Exception e) {
            updateTransactionLog(log, "ROLLBACK", e.getMessage());
            throw new TransactionException("SAGA transaction failed", e);
        }
    }
    
    private TransactionLog createTransactionLog(String xid, String type, 
                                              String businessType, 
                                              Map<String, Object> businessData) {
        TransactionLog log = TransactionLog.builder()
                .xid(xid)
                .transactionType(type)
                .businessType(businessType)
                .status("ACTIVE")
                .beginTime(Timestamp.from(Instant.now()))
                .businessData(businessData)
                .build();
        
        return transactionLogRepository.save(log);
    }
    
    private void updateTransactionLog(TransactionLog log, String status, String errorMessage) {
        log.setStatus(status);
        log.setErrorMessage(errorMessage);
        
        if ("COMMITTED".equals(status)) {
            log.setCommitTime(Timestamp.from(Instant.now()));
        } else if ("ROLLBACK".equals(status)) {
            log.setRollbackTime(Timestamp.from(Instant.now()));
        }
        
        transactionLogRepository.save(log);
    }
}
```

### 3.3 XA事务服务实现

```java
@Service
public class XATransactionService {
    
    @Autowired
    private UserTransactionManager userTransactionManager;
    
    @Autowired
    private DataSource xaDataSource;
    
    /**
     * 执行XA事务
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeXATransaction(String businessType, 
                                                   Map<String, Object> businessData) {
        String transactionId = generateTransactionId();
        
        try {
            // 开始XA事务
            userTransactionManager.begin();
            
            Map<String, Object> result = processXABusiness(businessType, businessData);
            
            // 提交XA事务
            userTransactionManager.commit();
            
            return Map.of(
                "transactionId", transactionId,
                "status", "SUCCESS",
                "result", result
            );
            
        } catch (Exception e) {
            try {
                userTransactionManager.rollback();
            } catch (SystemException rollbackException) {
                log.error("Failed to rollback XA transaction", rollbackException);
            }
            
            throw new TransactionException("XA transaction failed", e);
        }
    }
    
    /**
     * 批量执行XA事务
     */
    public List<Map<String, Object>> batchExecuteXATransactions(
            List<Map<String, Object>> transactions) {
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Map<String, Object> transaction : transactions) {
            try {
                String businessType = (String) transaction.get("businessType");
                Map<String, Object> businessData = 
                    (Map<String, Object>) transaction.get("businessData");
                
                Map<String, Object> result = executeXATransaction(businessType, businessData);
                results.add(result);
                
            } catch (Exception e) {
                Map<String, Object> errorResult = Map.of(
                    "status", "ERROR",
                    "error", e.getMessage(),
                    "transaction", transaction
                );
                results.add(errorResult);
            }
        }
        
        return results;
    }
    
    private Map<String, Object> processXABusiness(String businessType, 
                                                 Map<String, Object> businessData) {
        switch (businessType) {
            case "ORDER_PAYMENT":
                return processOrderPayment(businessData);
            case "INVENTORY_UPDATE":
                return processInventoryUpdate(businessData);
            case "USER_REGISTRATION":
                return processUserRegistration(businessData);
            default:
                throw new IllegalArgumentException("Unsupported business type: " + businessType);
        }
    }
}
```

---

## 最佳实践

### 4.1 时间戳管理最佳实践

#### 4.1.1 时钟同步策略
```java
@Component
public class ClockSyncStrategy {
    
    private static final long SYNC_INTERVAL = 5000; // 5秒同步一次
    private static final long MAX_CLOCK_DRIFT = 1000; // 最大时钟偏移1秒
    
    @Scheduled(fixedRate = SYNC_INTERVAL)
    public void syncClocks() {
        List<String> activeNodes = getActiveNodes();
        
        for (String nodeId : activeNodes) {
            try {
                syncWithNode(nodeId);
            } catch (Exception e) {
                log.warn("Failed to sync with node: {}", nodeId, e);
            }
        }
    }
    
    private void syncWithNode(String nodeId) {
        // 获取远程节点的时钟状态
        ClockStatus remoteStatus = getRemoteClockStatus(nodeId);
        
        // 检查时钟偏移
        long drift = Math.abs(remoteStatus.getLamportTime() - 
                             lamportClockManager.getCurrentTime());
        
        if (drift > MAX_CLOCK_DRIFT) {
            log.warn("Large clock drift detected with node {}: {} ms", nodeId, drift);
            // 触发时钟同步
            lamportClockManager.sync(remoteStatus.getLamportTime());
        }
    }
}
```

#### 4.1.2 向量时钟优化
```java
@Component
public class VectorClockOptimizer {
    
    private static final int MAX_VECTOR_SIZE = 100;
    private static final long CLEANUP_INTERVAL = 300000; // 5分钟清理一次
    
    /**
     * 向量时钟垃圾回收
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL)
    public void cleanupVectorClocks() {
        Set<String> activeNodes = getActiveNodes();
        
        vectorClockManager.cleanup(node -> !activeNodes.contains(node));
    }
    
    /**
     * 向量时钟压缩
     */
    public VectorClock compressVectorClock(VectorClock vectorClock) {
        if (vectorClock.size() <= MAX_VECTOR_SIZE) {
            return vectorClock;
        }
        
        // 保留最近活跃的节点
        Set<String> recentActiveNodes = getRecentActiveNodes();
        Map<String, Long> compressedClock = vectorClock.getClock().entrySet()
                .stream()
                .filter(entry -> recentActiveNodes.contains(entry.getKey()))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                ));
        
        return new VectorClock(compressedClock);
    }
}
```

### 4.2 事务管理最佳实践

#### 4.2.1 事务超时管理
```java
@Component
public class TransactionTimeoutManager {
    
    private static final long DEFAULT_TIMEOUT = 30000; // 30秒
    private static final long MAX_TIMEOUT = 300000; // 5分钟
    
    /**
     * 根据业务类型确定超时时间
     */
    public long getTimeoutForBusinessType(String businessType) {
        switch (businessType) {
            case "ORDER_PAYMENT":
                return 10000; // 10秒
            case "INVENTORY_UPDATE":
                return 5000;  // 5秒
            case "USER_REGISTRATION":
                return 15000; // 15秒
            case "COMPLEX_ORDER":
                return 60000; // 1分钟
            case "DATA_MIGRATION":
                return MAX_TIMEOUT; // 5分钟
            default:
                return DEFAULT_TIMEOUT;
        }
    }
    
    /**
     * 事务超时监控
     */
    @Scheduled(fixedRate = 10000) // 每10秒检查一次
    public void monitorTransactionTimeouts() {
        List<TransactionLog> activeTx = transactionLogRepository
                .findByStatusAndBeginTimeBefore("ACTIVE", 
                    Timestamp.from(Instant.now().minusSeconds(60)));
        
        for (TransactionLog tx : activeTx) {
            long timeout = getTimeoutForBusinessType(tx.getBusinessType());
            long elapsed = System.currentTimeMillis() - tx.getBeginTime().getTime();
            
            if (elapsed > timeout) {
                log.warn("Transaction timeout detected: {}", tx.getXid());
                handleTransactionTimeout(tx);
            }
        }
    }
}
```

#### 4.2.2 事务重试策略
```java
@Component
public class TransactionRetryStrategy {
    
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY = 1000; // 1秒
    
    /**
     * 带重试的事务执行
     */
    public <T> T executeWithRetry(Supplier<T> transactionSupplier, 
                                 String businessType) {
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < MAX_RETRY_COUNT) {
            try {
                return transactionSupplier.get();
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                
                if (isRetryableException(e) && retryCount < MAX_RETRY_COUNT) {
                    log.warn("Transaction failed, retrying ({}/{}): {}", 
                            retryCount, MAX_RETRY_COUNT, e.getMessage());
                    
                    try {
                        Thread.sleep(RETRY_DELAY * retryCount); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new TransactionException("Transaction interrupted", ie);
                    }
                } else {
                    break;
                }
            }
        }
        
        throw new TransactionException("Transaction failed after " + retryCount + " retries", 
                                     lastException);
    }
    
    private boolean isRetryableException(Exception e) {
        return e instanceof SQLException ||
               e instanceof DataAccessException ||
               e instanceof TransientDataAccessException;
    }
}
```

### 4.3 性能优化最佳实践

#### 4.3.1 批量操作优化
```java
@Service
public class BatchOperationService {
    
    private static final int BATCH_SIZE = 100;
    
    /**
     * 批量创建时间戳事件
     */
    @Transactional
    public List<TimestampEventResponse> batchCreateEvents(
            List<TimestampEventRequest> requests) {
        
        List<TimestampEvent> events = new ArrayList<>();
        List<TimestampEventResponse> responses = new ArrayList<>();
        
        for (int i = 0; i < requests.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, requests.size());
            List<TimestampEventRequest> batch = requests.subList(i, endIndex);
            
            List<TimestampEvent> batchEvents = batch.stream()
                    .map(this::createTimestampEvent)
                    .collect(Collectors.toList());
            
            // 批量保存
            List<TimestampEvent> savedEvents = repository.saveAll(batchEvents);
            events.addAll(savedEvents);
            
            // 批量缓存
            batchCacheEvents(savedEvents);
            
            responses.addAll(savedEvents.stream()
                    .map(TimestampEventResponse::from)
                    .collect(Collectors.toList()));
        }
        
        return responses;
    }
    
    private void batchCacheEvents(List<TimestampEvent> events) {
        Map<String, Object> cacheMap = events.stream()
                .collect(Collectors.toMap(
                    event -> "event:" + event.getId(),
                    Function.identity()
                ));
        
        redisTemplate.opsForValue().multiSet(cacheMap);
        
        // 设置过期时间
        cacheMap.keySet().forEach(key -> 
            redisTemplate.expire(key, Duration.ofHours(1)));
    }
}
```

#### 4.3.2 缓存策略优化
```java
@Component
public class CacheOptimizer {
    
    /**
     * 多级缓存策略
     */
    @Cacheable(value = "timestamps", key = "#nodeId", 
               condition = "#nodeId != null")
    public Long getTimestamp(String nodeId) {
        // L1: 本地缓存 (Caffeine)
        Long timestamp = localCache.get(nodeId);
        if (timestamp != null) {
            return timestamp;
        }
        
        // L2: Redis缓存
        timestamp = (Long) redisTemplate.opsForValue()
                .get("timestamp:" + nodeId);
        if (timestamp != null) {
            localCache.put(nodeId, timestamp);
            return timestamp;
        }
        
        // L3: 数据库
        timestamp = repository.findCurrentTimestampByNodeId(nodeId);
        if (timestamp != null) {
            // 写入缓存
            redisTemplate.opsForValue().set("timestamp:" + nodeId, 
                                           timestamp, Duration.ofMinutes(10));
            localCache.put(nodeId, timestamp);
        }
        
        return timestamp;
    }
    
    /**
     * 缓存预热
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmupCache() {
        List<String> activeNodes = getActiveNodes();
        
        activeNodes.parallelStream().forEach(nodeId -> {
            try {
                getTimestamp(nodeId); // 触发缓存加载
            } catch (Exception e) {
                log.warn("Failed to warmup cache for node: {}", nodeId, e);
            }
        });
    }
}
```

---

## 故障排除

### 5.1 常见问题诊断

#### 5.1.1 时钟偏移问题
```bash
# 检查时钟偏移
curl -s http://localhost:8080/api/v1/timestamp/status | jq '.lamportTime'

# 检查节点间时钟差异
curl -s http://localhost:8080/api/v1/timestamp/drift-report

# 强制时钟同步
curl -X POST http://localhost:8080/api/v1/timestamp/force-sync
```

#### 5.1.2 事务问题诊断
```sql
-- 查看活跃事务
SELECT * FROM transaction_logs 
WHERE status = 'ACTIVE' 
AND begin_time < NOW() - INTERVAL 1 MINUTE;

-- 查看失败事务
SELECT transaction_type, business_type, COUNT(*) as failure_count
FROM transaction_logs 
WHERE status = 'ROLLBACK' 
AND created_at > NOW() - INTERVAL 1 HOUR
GROUP BY transaction_type, business_type;

-- 查看事务性能
SELECT 
    business_type,
    AVG(TIMESTAMPDIFF(MICROSECOND, begin_time, commit_time)) as avg_duration_us,
    COUNT(*) as transaction_count
FROM transaction_logs 
WHERE status = 'COMMITTED' 
AND commit_time > NOW() - INTERVAL 1 HOUR
GROUP BY business_type;
```

#### 5.1.3 性能问题诊断
```bash
# JVM内存使用情况
curl -s http://localhost:8080/api/actuator/metrics/jvm.memory.used | jq .

# 数据库连接池状态
curl -s http://localhost:8080/api/actuator/metrics/hikaricp.connections.active | jq .

# Redis连接状态
curl -s http://localhost:8080/api/actuator/metrics/lettuce.command.completion | jq .

# 应用响应时间
curl -s http://localhost:8080/api/actuator/metrics/http.server.requests | jq .
```

### 5.2 故障恢复脚本

#### 5.2.1 自动故障恢复
```bash
#!/bin/bash
# auto-recovery.sh

LOG_FILE="/var/log/dts-recovery.log"

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a $LOG_FILE
}

# 检查应用健康状态
check_health() {
    local health_url="http://localhost:8080/api/actuator/health"
    local response=$(curl -s -o /dev/null -w "%{http_code}" $health_url)
    
    if [ "$response" = "200" ]; then
        return 0
    else
        return 1
    fi
}

# 重启应用
restart_application() {
    log "Restarting application..."
    
    # 停止应用
    pkill -f "distributed-timestamp-system"
    sleep 5
    
    # 启动应用
    nohup java -jar /app/distributed-timestamp-system-1.0.0.jar > /dev/null 2>&1 &
    
    # 等待启动完成
    sleep 30
    
    if check_health; then
        log "Application restarted successfully"
        return 0
    else
        log "Application restart failed"
        return 1
    fi
}

# 清理过期数据
cleanup_expired_data() {
    log "Cleaning up expired data..."
    
    # 清理过期的时间戳事件
    mysql -h 11.142.154.110 -u with_zqwjnhlyktudftww -p'IvE48V)DoJNcN&' gfaeofw8 << EOF
DELETE FROM timestamp_events 
WHERE created_at < NOW() - INTERVAL 7 DAY;

DELETE FROM transaction_logs 
WHERE status IN ('COMMITTED', 'ROLLBACK') 
AND created_at < NOW() - INTERVAL 3 DAY;
EOF
    
    log "Data cleanup completed"
}

# 主恢复流程
main() {
    log "Starting auto recovery process..."
    
    if ! check_health; then
        log "Application health check failed, attempting recovery..."
        
        if restart_application; then
            log "Recovery successful"
        else
            log "Recovery failed, manual intervention required"
            exit 1
        fi
    else
        log "Application is healthy"
    fi
    
    cleanup_expired_data
    
    log "Auto recovery process completed"
}

main "$@"
```

### 5.3 监控告警配置

#### 5.3.1 Prometheus告警规则
```yaml
groups:
- name: dts-system-alerts
  rules:
  - alert: ApplicationDown
    expr: up{job="dts-system"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "DTS System is down"
      description: "DTS System has been down for more than 1 minute"
      
  - alert: HighMemoryUsage
    expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.8
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "High memory usage detected"
      description: "JVM heap memory usage is above 80%"
      
  - alert: DatabaseConnectionPoolExhausted
    expr: hikaricp_connections_active >= hikaricp_connections_max * 0.9
    for: 2m
    labels:
      severity: critical
    annotations:
      summary: "Database connection pool nearly exhausted"
      description: "Active database connections are above 90% of maximum"
      
  - alert: TransactionFailureRateHigh
    expr: rate(transaction_failures_total[5m]) / rate(transaction_total[5m]) > 0.1
    for: 3m
    labels:
      severity: warning
    annotations:
      summary: "High transaction failure rate"
      description: "Transaction failure rate is above 10%"
```

这个技术实现细节文档提供了系统核心算法的具体实现、详细的配置说明、关键代码示例、最佳实践指导和故障排除方案，为开发和运维人员提供了完整的技术参考。