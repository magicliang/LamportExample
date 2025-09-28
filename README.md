# 分布式系统时间戳与事务管理方案

## 项目概述

本项目基于Java 8和Spring Boot构建，实现了分布式系统中的时间戳管理和事务协调机制，包括：

- **Lamport时间戳**：逻辑时钟实现
- **版本向量**：分布式版本控制
- **向量时钟**：因果关系追踪
- **Seata分布式事务**：事务一致性保证

## 技术栈

- Java 8
- Spring Boot 2.7.x
- Spring Cloud
- Seata 1.6.x
- MySQL 8.0
- Redis 6.x
- Kubernetes
- Docker
- JUnit 5
- Testcontainers

## 项目结构

```
distributed-timestamp-system/
├── docs/                           # 设计文档
│   ├── system-design.md           # 系统设计文档
│   └── api-documentation.md       # API文档
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/dts/
│   │   │       ├── DtsApplication.java
│   │   │       ├── config/         # 配置类
│   │   │       ├── controller/     # REST控制器
│   │   │       ├── service/        # 业务服务
│   │   │       ├── model/          # 数据模型
│   │   │       ├── repository/     # 数据访问层
│   │   │       └── timestamp/      # 时间戳实现
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/       # 数据库迁移脚本
│   └── test/                       # 测试代码
├── k8s/                           # Kubernetes部署文件
├── docker/                        # Docker配置
├── pom.xml                        # Maven配置
└── Dockerfile                     # Docker镜像构建
```

## 快速开始

### 环境要求
- JDK 1.8+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

### 启动步骤
1. 克隆项目：`git clone <repository-url>`
2. 配置数据库连接：编辑 `application.yml`
3. 启动应用：`mvn spring-boot:run`
4. 访问 API 文档：`http://localhost:8080/api/swagger-ui.html`

### API 示例

#### 时间戳管理
```bash
# 生成 Lamport 时间戳
curl -X POST http://localhost:8080/api/v1/timestamp/lamport \
  -H "Content-Type: application/json" \
  -d '{"eventType": "USER_ACTION", "businessData": {"userId": "123"}}'

# 生成版本向量
curl -X POST http://localhost:8080/api/v1/timestamp/version-vector \
  -H "Content-Type: application/json" \
  -d '{"nodeId": "node1", "eventType": "DATA_UPDATE"}'
```

#### 分布式事务
```bash
# Seata AT 事务
curl -X POST http://localhost:8080/api/v1/transaction/at \
  -H "Content-Type: application/json" \
  -d '{"businessType": "ORDER_PAYMENT", "businessData": {"orderId": "12345", "amount": 100.0}}'

# XA 事务
curl -X POST http://localhost:8080/api/v1/xa-jta/xa/execute \
  -H "Content-Type: application/json" \
  -d '{"businessType": "ORDER_PAYMENT", "businessData": {"orderId": "12345", "amount": 100.0}}'

# JTA 事务
curl -X POST http://localhost:8080/api/v1/xa-jta/jta/execute \
  -H "Content-Type: application/json" \
  -d '{"businessType": "COMPLEX_ORDER", "businessData": {"orderId": "12345", "items": [{"productId": "P001", "price": 50.0, "quantity": 2}]}}'
```

#### 演示脚本
```bash
# 运行完整演示
./scripts/demo.sh

# 运行 XA/JTA 演示
./scripts/xa-jta-demo.sh
```

### K8s部署

```bash
kubectl apply -f k8s/
```

## 核心功能

### 分布式时间戳管理
- **Lamport 时间戳**：逻辑时钟实现，确保事件的因果关系
- **版本向量**：多节点版本控制，支持并发更新检测
- **向量时钟**：分布式系统中的事件排序和同步

### 分布式事务管理
- **Seata AT 模式**：自动补偿事务，支持透明的分布式事务
- **Seata TCC 模式**：Try-Confirm-Cancel 补偿型事务
- **Seata SAGA 模式**：长事务解决方案，支持复杂业务流程
- **XA 事务**：基于 JTA 的两阶段提交协议，确保 ACID 特性
- **JTA 事务**：Java 事务 API，支持复杂的分布式事务场景

### 高可用特性
- 多节点部署支持
- 故障自动恢复
- 数据一致性保证
- 性能监控和告警

## 测试

- 单元测试：`mvn test`
- 集成测试：`mvn verify -P integration-test`
- 性能测试：`mvn verify -P performance-test`

## 监控与运维

- 健康检查：`/actuator/health`
- 指标监控：`/actuator/metrics`
- 链路追踪：集成Zipkin