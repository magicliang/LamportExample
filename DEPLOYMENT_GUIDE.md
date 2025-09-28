# 分布式时间戳系统部署指南

## 概述

本指南将帮助您完整部署和运行基于Java 8和Spring Boot的分布式时间戳与事务管理系统。

## 系统要求

### 开发环境
- **Java**: JDK 8 或更高版本
- **Maven**: 3.6.0 或更高版本
- **Docker**: 20.10 或更高版本
- **Kubernetes**: 1.20 或更高版本

### 运行环境
- **MySQL**: 8.0 或更高版本
- **Redis**: 6.0 或更高版本
- **Seata**: 1.6.1 或更高版本
- **Nacos**: 2.0 或更高版本（可选）

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/example/distributed-timestamp-system.git
cd distributed-timestamp-system
```

### 2. 配置数据库

系统已自动创建数据库 `gfaeofw8` 和相关表结构：

**数据库连接信息**：
- 主机: `11.142.154.110`
- 端口: `3306`
- 数据库: `gfaeofw8`
- 用户名: `with_zqwjnhlyktudftww`
- 密码: `IvE48V)DoJNcN&`

### 3. 本地开发运行

```bash
# 构建项目
./scripts/build.sh

# 启动应用
mvn spring-boot:run

# 或者运行JAR文件
java -jar target/distributed-timestamp-system-1.0.0.jar
```

应用启动后访问：
- API文档: http://localhost:8080/api/swagger-ui.html
- 健康检查: http://localhost:8080/api/actuator/health
- 指标监控: http://localhost:8080/api/actuator/metrics

## Docker部署

### 1. 构建Docker镜像

```bash
# 使用构建脚本（推荐）
./scripts/build.sh

# 或手动构建
docker build -t dts-system:1.0.0 .
```

### 2. 运行Docker容器

```bash
# 创建网络
docker network create dts-network

# 启动Redis（如果没有外部Redis）
docker run -d --name redis \
  --network dts-network \
  -p 6379:6379 \
  redis:6-alpine

# 启动应用
docker run -d --name dts-system \
  --network dts-network \
  -p 8080:8080 \
  -e MYSQL_HOST=11.142.154.110 \
  -e MYSQL_PORT=3306 \
  -e MYSQL_DATABASE=gfaeofw8 \
  -e MYSQL_USERNAME=with_zqwjnhlyktudftww \
  -e MYSQL_PASSWORD="IvE48V)DoJNcN&" \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e NODE_ID=docker-node-1 \
  dts-system:1.0.0
```

### 3. 使用Docker Compose

创建 `docker-compose.yml` 文件：

```yaml
version: '3.8'

services:
  redis:
    image: redis:6-alpine
    ports:
      - "6379:6379"
    networks:
      - dts-network

  dts-system:
    image: dts-system:1.0.0
    ports:
      - "8080:8080"
    environment:
      - MYSQL_HOST=11.142.154.110
      - MYSQL_PORT=3306
      - MYSQL_DATABASE=gfaeofw8
      - MYSQL_USERNAME=with_zqwjnhlyktudftww
      - MYSQL_PASSWORD=IvE48V)DoJNcN&
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - NODE_ID=compose-node-1
    depends_on:
      - redis
    networks:
      - dts-network

networks:
  dts-network:
    driver: bridge
```

启动服务：

```bash
docker-compose up -d
```

## Kubernetes部署

### 1. 准备Kubernetes集群

确保您有一个可用的Kubernetes集群，并且 `kubectl` 已正确配置。

### 2. 部署到Kubernetes

```bash
# 使用部署脚本（推荐）
./scripts/deploy.sh

# 或手动部署
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml
```

### 3. 检查部署状态

```bash
# 检查Pod状态
kubectl get pods -n dts-system

# 检查服务状态
kubectl get services -n dts-system

# 查看应用日志
kubectl logs -n dts-system -l app=distributed-timestamp-system
```

### 4. 访问应用

```bash
# 通过NodePort访问
kubectl get service dts-system-nodeport -n dts-system

# 通过端口转发访问
kubectl port-forward -n dts-system service/dts-system-service 8080:8080
```

## 生产环境配置

### 1. 数据库配置

在生产环境中，建议：

- 使用专用的MySQL集群
- 配置主从复制和读写分离
- 启用SSL连接
- 定期备份数据

### 2. Redis配置

生产环境Redis配置：

- 使用Redis集群或哨兵模式
- 启用持久化（AOF + RDB）
- 配置内存限制和淘汰策略
- 启用密码认证

### 3. 应用配置

生产环境应用配置：

```yaml
# application-prod.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

logging:
  level:
    com.example.dts: INFO
    org.springframework.transaction: WARN
  file:
    name: /app/logs/dts-system.log
    max-size: 100MB
    max-history: 30

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

### 4. 监控和告警

#### Prometheus监控

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'dts-system'
    static_configs:
      - targets: ['dts-system-service:8080']
    metrics_path: '/api/actuator/prometheus'
    scrape_interval: 15s
```

#### Grafana仪表板

导入预配置的Grafana仪表板监控以下指标：

- JVM内存和GC
- HTTP请求QPS和延迟
- 数据库连接池状态
- Redis连接状态
- Lamport时钟值
- 事件创建速率
- 事务成功率

#### 告警规则

```yaml
# alerts.yml
groups:
  - name: dts-system
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_total{status=~"5.."}[5m]) > 0.1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "DTS系统错误率过高"
          
      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "DTS系统内存使用率过高"
```

## 性能调优

### 1. JVM参数调优

```bash
# 生产环境JVM参数
JAVA_OPTS="-Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:G1HeapRegionSize=16m \
  -XX:+UseStringDeduplication \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/app/logs/ \
  -Djava.security.egd=file:/dev/./urandom"
```

### 2. 数据库优化

```sql
-- MySQL配置优化
SET GLOBAL innodb_buffer_pool_size = 2147483648;  -- 2GB
SET GLOBAL innodb_log_file_size = 268435456;      -- 256MB
SET GLOBAL innodb_flush_log_at_trx_commit = 2;
SET GLOBAL sync_binlog = 0;

-- 索引优化
CREATE INDEX idx_timestamp_node_type ON timestamp_events(node_id, event_type, created_at);
CREATE INDEX idx_transaction_status_time ON transaction_logs(status, begin_time);
```

### 3. Redis优化

```conf
# redis.conf
maxmemory 1gb
maxmemory-policy allkeys-lru
save 900 1
save 300 10
save 60 10000
appendonly yes
appendfsync everysec
```

## 测试

### 1. 单元测试

```bash
# 运行所有单元测试
mvn test

# 运行特定测试类
mvn test -Dtest=LamportClockManagerTest

# 生成测试覆盖率报告
mvn jacoco:report
```

### 2. 集成测试

```bash
# 运行集成测试
mvn verify -P integration-test

# 使用Testcontainers运行集成测试
mvn verify -P integration-test -Dspring.profiles.active=test
```

### 3. 性能测试

```bash
# 使用JMeter进行性能测试
mvn verify -P performance-test

# 或使用Apache Bench
ab -n 1000 -c 10 http://localhost:8080/api/v1/timestamp/status
```

### 4. API测试

```bash
# 使用curl测试API
curl -X POST http://localhost:8080/api/v1/timestamp/event \
  -H "Content-Type: application/json" \
  -d '{"eventType":"TEST","eventData":{"test":true}}'

# 使用Postman导入API集合
# 导入文件: docs/postman-collection.json
```

## 故障排除

### 1. 常见问题

#### 应用启动失败

```bash
# 检查日志
docker logs dts-system
kubectl logs -n dts-system deployment/dts-system

# 常见原因：
# - 数据库连接失败
# - Redis连接失败
# - 端口被占用
# - 内存不足
```

#### 数据库连接问题

```bash
# 测试数据库连接
mysql -h 11.142.154.110 -P 3306 -u with_zqwjnhlyktudftww -p gfaeofw8

# 检查防火墙设置
telnet 11.142.154.110 3306
```

#### Redis连接问题

```bash
# 测试Redis连接
redis-cli -h redis-host -p 6379 ping

# 检查Redis配置
redis-cli -h redis-host -p 6379 config get "*"
```

### 2. 性能问题

#### 高延迟

- 检查数据库查询性能
- 优化索引
- 增加连接池大小
- 启用查询缓存

#### 高内存使用

- 调整JVM堆大小
- 检查内存泄漏
- 优化对象创建
- 启用G1垃圾收集器

#### 高CPU使用

- 检查死循环或无限递归
- 优化算法复杂度
- 减少不必要的计算
- 使用异步处理

### 3. 日志分析

```bash
# 查看应用日志
tail -f /app/logs/dts-system.log

# 过滤错误日志
grep "ERROR" /app/logs/dts-system.log

# 分析访问日志
awk '{print $1}' access.log | sort | uniq -c | sort -nr
```

## 安全配置

### 1. 网络安全

- 使用HTTPS/TLS加密
- 配置防火墙规则
- 启用网络隔离
- 使用VPN或专线

### 2. 应用安全

- 启用Spring Security
- 配置JWT认证
- 实现API限流
- 添加输入验证

### 3. 数据安全

- 数据库连接加密
- 敏感数据脱敏
- 定期安全扫描
- 访问审计日志

## 备份和恢复

### 1. 数据库备份

```bash
# 全量备份
mysqldump -h 11.142.154.110 -u with_zqwjnhlyktudftww -p gfaeofw8 > backup.sql

# 增量备份
mysqlbinlog --start-datetime="2023-01-01 00:00:00" mysql-bin.000001 > incremental.sql
```

### 2. Redis备份

```bash
# RDB备份
redis-cli -h redis-host -p 6379 BGSAVE

# AOF备份
cp /var/lib/redis/appendonly.aof /backup/
```

### 3. 应用配置备份

```bash
# 备份Kubernetes配置
kubectl get all -n dts-system -o yaml > k8s-backup.yaml

# 备份配置文件
tar -czf config-backup.tar.gz k8s/ src/main/resources/
```

## 升级指南

### 1. 滚动升级

```bash
# 构建新版本镜像
./scripts/build.sh --image-tag 1.1.0

# 更新Kubernetes部署
kubectl set image deployment/dts-system -n dts-system dts-system=dts-system:1.1.0

# 检查升级状态
kubectl rollout status deployment/dts-system -n dts-system
```

### 2. 蓝绿部署

```bash
# 部署绿色环境
kubectl apply -f k8s-green/

# 切换流量
kubectl patch service dts-system-service -n dts-system -p '{"spec":{"selector":{"version":"green"}}}'

# 清理蓝色环境
kubectl delete -f k8s-blue/
```

### 3. 数据库迁移

```bash
# 运行数据库迁移
mvn flyway:migrate

# 或使用Liquibase
mvn liquibase:update
```

## 联系支持

如需技术支持，请联系：

- **邮箱**: dts-support@example.com
- **文档**: https://docs.example.com/dts
- **问题跟踪**: https://github.com/example/dts/issues
- **社区论坛**: https://community.example.com/dts

---

**注意**: 本指南基于系统版本1.0.0编写，请根据实际版本调整相关配置。