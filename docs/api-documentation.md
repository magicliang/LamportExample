# 分布式时间戳系统 API 文档

## 概述

分布式时间戳系统提供了一套完整的REST API，用于管理Lamport时间戳、版本向量、向量时钟和分布式事务。

**基础URL**: `http://localhost:8080/api`

**API版本**: v1

## 认证

目前系统不需要认证，所有API都是公开访问的。在生产环境中建议添加适当的认证机制。

## 响应格式

所有API响应都使用JSON格式，包含以下通用字段：

```json
{
  "success": true,
  "data": {},
  "error": "错误信息（仅在失败时）",
  "timestamp": 1640995200000
}
```

## 时间戳管理 API

### 1. 创建时间戳事件

创建一个新的时间戳事件，自动生成Lamport时间戳、版本向量和向量时钟。

**请求**
```
POST /v1/timestamp/event
Content-Type: application/json

{
  "eventType": "USER_ACTION",
  "eventData": {
    "userId": "12345",
    "action": "login",
    "timestamp": "2023-01-01T10:00:00Z"
  }
}
```

**响应**
```json
{
  "success": true,
  "eventId": 1001,
  "lamportTimestamp": 15,
  "nodeId": "node-1",
  "createdAt": "2023-01-01T10:00:00.123"
}
```

### 2. 同步时间戳事件

接收并同步来自其他节点的时间戳事件。

**请求**
```
POST /v1/timestamp/sync
Content-Type: application/json

{
  "sourceNodeId": "node-2",
  "lamportTimestamp": 20,
  "vectorClock": {
    "node-1": 5,
    "node-2": 8
  },
  "versionVector": {
    "node-1": 3,
    "node-2": 4
  },
  "eventType": "DATA_UPDATE",
  "eventData": {
    "resourceId": "resource-123",
    "operation": "update"
  }
}
```

**响应**
```json
{
  "success": true,
  "eventId": 1002,
  "syncedAt": 1640995200000
}
```

### 3. 获取当前时间戳状态

获取当前节点的时间戳状态信息。

**请求**
```
GET /v1/timestamp/status
```

**响应**
```json
{
  "nodeId": "node-1",
  "lamportTime": 25,
  "vectorClock": {
    "node-1": 10,
    "node-2": 8,
    "node-3": 5
  },
  "versionVector": {
    "node-1": 6,
    "node-2": 4,
    "node-3": 2
  },
  "timestamp": 1640995200000
}
```

### 4. 比较事件时间关系

比较两个事件的时间关系，包括Lamport时间戳、向量时钟和版本向量的比较。

**请求**
```
GET /v1/timestamp/compare?eventId1=1001&eventId2=1002
```

**响应**
```json
{
  "lamportRelation": "BEFORE",
  "lamportTime1": 15,
  "lamportTime2": 20,
  "vectorClockRelation": "BEFORE",
  "vectorClock1": {"node-1": 5, "node-2": 3},
  "vectorClock2": {"node-1": 5, "node-2": 8},
  "versionVectorRelation": "OLDER",
  "versionVector1": {"node-1": 3, "node-2": 2},
  "versionVector2": {"node-1": 3, "node-2": 4},
  "hasConflict": false
}
```

### 5. 获取节点事件历史

获取指定节点的事件历史记录。

**请求**
```
GET /v1/timestamp/history/node-1?limit=50
```

**响应**
```json
{
  "success": true,
  "nodeId": "node-1",
  "events": [
    {
      "id": 1003,
      "nodeId": "node-1",
      "lamportTimestamp": 25,
      "eventType": "USER_ACTION",
      "createdAt": "2023-01-01T10:05:00.123"
    }
  ],
  "count": 1
}
```

### 6. 获取时间范围内的事件

获取指定时间范围内的所有事件。

**请求**
```
GET /v1/timestamp/events?startTime=2023-01-01T09:00:00&endTime=2023-01-01T11:00:00
```

**响应**
```json
{
  "success": true,
  "startTime": "2023-01-01T09:00:00",
  "endTime": "2023-01-01T11:00:00",
  "events": [
    {
      "id": 1001,
      "nodeId": "node-1",
      "lamportTimestamp": 15,
      "eventType": "USER_ACTION",
      "createdAt": "2023-01-01T10:00:00.123"
    }
  ],
  "count": 1
}
```

### 7. 检测冲突

检测系统中可能存在的版本向量冲突。

**请求**
```
GET /v1/timestamp/conflicts?limit=10
```

**响应**
```json
{
  "success": true,
  "conflicts": [
    {
      "event1Id": 1001,
      "event2Id": 1002,
      "node1": "node-1",
      "node2": "node-2",
      "conflictType": "VERSION_VECTOR",
      "detectedAt": 1640995200000
    }
  ],
  "count": 1,
  "detectedAt": 1640995200000
}
```

### 8. 同步所有时间戳

同步当前节点与其他节点的所有时间戳。

**请求**
```
POST /v1/timestamp/sync-all
```

**响应**
```json
{
  "success": true,
  "syncedAt": 1640995200000,
  "lamportTime": 30,
  "vectorClock": {
    "node-1": 12,
    "node-2": 10,
    "node-3": 8
  },
  "versionVector": {
    "node-1": 7,
    "node-2": 5,
    "node-3": 3
  }
}
```

## 分布式事务 API

### 1. 执行AT模式事务

执行Seata AT模式的分布式事务。

**请求**
```
POST /v1/transaction/at
Content-Type: application/json

{
  "businessType": "ORDER_CREATE",
  "businessData": {
    "orderId": "order-123",
    "userId": "user-456",
    "amount": 100.00
  }
}
```

**响应**
```json
{
  "success": true,
  "transactionType": "AT",
  "startEventId": 1004,
  "successEventId": 1005,
  "businessResult": {
    "orderId": "order-123",
    "status": "CREATED",
    "processedAt": 1640995200000,
    "nodeId": "node-1"
  },
  "completedAt": 1640995200000
}
```

### 2. 执行TCC模式事务

执行Seata TCC模式的分布式事务。

**请求**
```
POST /v1/transaction/tcc
Content-Type: application/json

{
  "businessType": "PAYMENT_PROCESS",
  "businessData": {
    "paymentId": "payment-789",
    "amount": 100.00,
    "currency": "USD"
  }
}
```

**响应**
```json
{
  "success": true,
  "transactionType": "TCC",
  "transactionId": "node-1-1640995200000-abc12345",
  "tryResult": {
    "resourceId": "resource-xyz",
    "reserved": true,
    "tryAt": 1640995200000
  },
  "confirmResult": {
    "confirmed": true,
    "confirmAt": 1640995201000
  },
  "completedAt": 1640995201000
}
```

### 3. 执行SAGA模式事务

执行Seata SAGA模式的分布式事务。

**请求**
```
POST /v1/transaction/saga
Content-Type: application/json

{
  "businessType": "ORDER_PROCESS",
  "businessData": {
    "orderId": "order-456",
    "items": [
      {"productId": "prod-1", "quantity": 2},
      {"productId": "prod-2", "quantity": 1}
    ]
  }
}
```

**响应**
```json
{
  "success": true,
  "transactionType": "SAGA",
  "sagaId": "node-1-1640995200000-def67890",
  "stepResults": [
    {
      "stepName": "CREATE_ORDER",
      "stepIndex": 0,
      "executedAt": 1640995200000,
      "success": true
    },
    {
      "stepName": "RESERVE_INVENTORY",
      "stepIndex": 1,
      "executedAt": 1640995200100,
      "success": true
    }
  ],
  "completedAt": 1640995200500
}
```

### 4. 异步执行事务

异步执行分布式事务。

**请求**
```
POST /v1/transaction/async
Content-Type: application/json

{
  "transactionType": "AT",
  "businessType": "INVENTORY_UPDATE",
  "businessData": {
    "productId": "prod-123",
    "quantity": -5,
    "operation": "DECREASE"
  }
}
```

**响应**
```json
{
  "success": true,
  "async": true,
  "transactionType": "AT",
  "businessType": "INVENTORY_UPDATE",
  "taskId": "task-1640995200000-ghi12345",
  "submittedAt": 1640995200000,
  "message": "Transaction submitted for async execution"
}
```

### 5. 批量执行事务

批量执行多个分布式事务。

**请求**
```
POST /v1/transaction/batch
Content-Type: application/json

{
  "transactionType": "AT",
  "transactions": [
    {
      "businessType": "ORDER_CREATE",
      "businessData": {"orderId": "order-1"}
    },
    {
      "businessType": "ORDER_CREATE", 
      "businessData": {"orderId": "order-2"}
    }
  ]
}
```

**响应**
```json
{
  "success": true,
  "batch": true,
  "transactionType": "AT",
  "totalCount": 2,
  "batchId": "task-1640995200000-jkl67890",
  "submittedAt": 1640995200000
}
```

### 6. 获取事务状态

获取指定事务的状态信息。

**请求**
```
GET /v1/transaction/status/node-1-1640995200000-abc12345
```

**响应**
```json
{
  "transactionId": "node-1-1640995200000-abc12345",
  "status": "COMMITTED",
  "createdAt": 1640995140000,
  "completedAt": 1640995200000,
  "duration": 60000
}
```

## 系统监控 API

### 健康检查

**请求**
```
GET /actuator/health
```

**响应**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "6.2.6"
      }
    }
  }
}
```

### 系统指标

**请求**
```
GET /actuator/metrics
```

**响应**
```json
{
  "names": [
    "jvm.memory.used",
    "jvm.gc.pause",
    "http.server.requests",
    "dts.lamport.clock.current",
    "dts.events.created.total"
  ]
}
```

### Prometheus指标

**请求**
```
GET /actuator/prometheus
```

**响应**
```
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="PS Eden Space",} 1.234567E8

# HELP dts_lamport_clock_current Current Lamport clock value
# TYPE dts_lamport_clock_current gauge
dts_lamport_clock_current{node="node-1",} 25.0
```

## 错误码

| 错误码 | 描述 | 解决方案 |
|--------|------|----------|
| 400 | 请求参数错误 | 检查请求参数格式和必填字段 |
| 404 | 资源不存在 | 确认请求的资源ID是否正确 |
| 500 | 服务器内部错误 | 查看服务器日志，联系管理员 |
| 503 | 服务不可用 | 检查依赖服务（数据库、Redis）状态 |

## 使用示例

### 创建和同步事件

```bash
# 1. 创建事件
curl -X POST http://localhost:8080/api/v1/timestamp/event \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "USER_LOGIN",
    "eventData": {
      "userId": "user123",
      "loginTime": "2023-01-01T10:00:00Z"
    }
  }'

# 2. 获取当前状态
curl http://localhost:8080/api/v1/timestamp/status

# 3. 同步事件
curl -X POST http://localhost:8080/api/v1/timestamp/sync \
  -H "Content-Type: application/json" \
  -d '{
    "sourceNodeId": "node-2",
    "lamportTimestamp": 30,
    "vectorClock": {"node-1": 10, "node-2": 15},
    "versionVector": {"node-1": 5, "node-2": 8},
    "eventType": "DATA_SYNC",
    "eventData": {"syncType": "full"}
  }'
```

### 执行分布式事务

```bash
# 1. 执行AT事务
curl -X POST http://localhost:8080/api/v1/transaction/at \
  -H "Content-Type: application/json" \
  -d '{
    "businessType": "ORDER_CREATE",
    "businessData": {
      "orderId": "order-123",
      "amount": 100.00
    }
  }'

# 2. 执行TCC事务
curl -X POST http://localhost:8080/api/v1/transaction/tcc \
  -H "Content-Type: application/json" \
  -d '{
    "businessType": "PAYMENT_PROCESS",
    "businessData": {
      "paymentId": "payment-456",
      "amount": 50.00
    }
  }'
```

## SDK和客户端

目前系统提供REST API接口，可以使用任何支持HTTP的编程语言进行集成。推荐使用以下工具：

- **Java**: Spring RestTemplate, OkHttp, Apache HttpClient
- **Python**: requests, httpx
- **JavaScript**: axios, fetch
- **Go**: net/http, resty
- **C#**: HttpClient

## 版本历史

| 版本 | 发布日期 | 主要变更 |
|------|----------|----------|
| v1.0.0 | 2023-01-01 | 初始版本，支持基础时间戳和事务功能 |

## 联系方式

如有问题或建议，请联系：
- 邮箱: dts@example.com
- 项目地址: https://github.com/example/distributed-timestamp-system