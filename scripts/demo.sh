#!/bin/bash

# 分布式时间戳系统功能演示脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# 配置
BASE_URL="http://localhost:8080/api"
CONTENT_TYPE="Content-Type: application/json"

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_demo() {
    echo -e "${PURPLE}[DEMO]${NC} $1"
}

# 检查服务是否可用
check_service() {
    log_info "检查服务状态..."
    
    if curl -s "$BASE_URL/actuator/health" > /dev/null; then
        log_success "服务运行正常"
    else
        log_error "服务不可用，请先启动应用"
        log_info "启动命令: mvn spring-boot:run"
        exit 1
    fi
}

# 演示时间戳功能
demo_timestamp() {
    echo ""
    log_demo "=== 时间戳功能演示 ==="
    
    # 1. 获取当前状态
    log_info "1. 获取当前时间戳状态"
    curl -s "$BASE_URL/v1/timestamp/status" | jq '.' || echo "请安装jq工具以格式化JSON输出"
    echo ""
    
    # 2. 创建事件
    log_info "2. 创建时间戳事件"
    EVENT1_RESPONSE=$(curl -s -X POST "$BASE_URL/v1/timestamp/event" \
        -H "$CONTENT_TYPE" \
        -d '{
            "eventType": "USER_LOGIN",
            "eventData": {
                "userId": "demo-user-001",
                "loginTime": "'$(date -Iseconds)'",
                "clientIP": "192.168.1.100"
            }
        }')
    
    echo "$EVENT1_RESPONSE" | jq '.' 2>/dev/null || echo "$EVENT1_RESPONSE"
    EVENT1_ID=$(echo "$EVENT1_RESPONSE" | jq -r '.eventId' 2>/dev/null || echo "")
    echo ""
    
    # 3. 创建第二个事件
    log_info "3. 创建第二个时间戳事件"
    sleep 1
    EVENT2_RESPONSE=$(curl -s -X POST "$BASE_URL/v1/timestamp/event" \
        -H "$CONTENT_TYPE" \
        -d '{
            "eventType": "DATA_UPDATE",
            "eventData": {
                "resourceId": "resource-123",
                "operation": "UPDATE",
                "timestamp": "'$(date -Iseconds)'"
            }
        }')
    
    echo "$EVENT2_RESPONSE" | jq '.' 2>/dev/null || echo "$EVENT2_RESPONSE"
    EVENT2_ID=$(echo "$EVENT2_RESPONSE" | jq -r '.eventId' 2>/dev/null || echo "")
    echo ""
    
    # 4. 比较事件关系
    if [ -n "$EVENT1_ID" ] && [ -n "$EVENT2_ID" ] && [ "$EVENT1_ID" != "null" ] && [ "$EVENT2_ID" != "null" ]; then
        log_info "4. 比较事件时间关系 (事件$EVENT1_ID vs 事件$EVENT2_ID)"
        curl -s "$BASE_URL/v1/timestamp/compare?eventId1=$EVENT1_ID&eventId2=$EVENT2_ID" | jq '.' 2>/dev/null || echo "比较请求已发送"
        echo ""
    fi
    
    # 5. 同步事件演示
    log_info "5. 演示事件同步"
    curl -s -X POST "$BASE_URL/v1/timestamp/sync" \
        -H "$CONTENT_TYPE" \
        -d '{
            "sourceNodeId": "demo-node-2",
            "lamportTimestamp": 100,
            "vectorClock": {
                "demo-node-1": 10,
                "demo-node-2": 15
            },
            "versionVector": {
                "demo-node-1": 5,
                "demo-node-2": 8
            },
            "eventType": "EXTERNAL_SYNC",
            "eventData": {
                "syncType": "demo",
                "source": "external-system"
            }
        }' | jq '.' 2>/dev/null || echo "同步请求已发送"
    echo ""
    
    # 6. 检测冲突
    log_info "6. 检测版本冲突"
    curl -s "$BASE_URL/v1/timestamp/conflicts?limit=5" | jq '.' 2>/dev/null || echo "冲突检测请求已发送"
    echo ""
}

# 演示分布式事务功能
demo_transaction() {
    echo ""
    log_demo "=== 分布式事务功能演示 ==="
    
    # 1. AT模式事务
    log_info "1. 执行AT模式分布式事务"
    curl -s -X POST "$BASE_URL/v1/transaction/at" \
        -H "$CONTENT_TYPE" \
        -d '{
            "businessType": "ORDER_CREATE",
            "businessData": {
                "orderId": "demo-order-001",
                "userId": "demo-user-001",
                "amount": 99.99,
                "currency": "USD",
                "items": [
                    {"productId": "prod-001", "quantity": 2, "price": 29.99},
                    {"productId": "prod-002", "quantity": 1, "price": 39.99}
                ]
            }
        }' | jq '.' 2>/dev/null || echo "AT事务请求已发送"
    echo ""
    
    # 2. TCC模式事务
    log_info "2. 执行TCC模式分布式事务"
    curl -s -X POST "$BASE_URL/v1/transaction/tcc" \
        -H "$CONTENT_TYPE" \
        -d '{
            "businessType": "PAYMENT_PROCESS",
            "businessData": {
                "paymentId": "demo-payment-001",
                "orderId": "demo-order-001",
                "amount": 99.99,
                "paymentMethod": "CREDIT_CARD",
                "cardNumber": "****-****-****-1234"
            }
        }' | jq '.' 2>/dev/null || echo "TCC事务请求已发送"
    echo ""
    
    # 3. SAGA模式事务
    log_info "3. 执行SAGA模式分布式事务"
    curl -s -X POST "$BASE_URL/v1/transaction/saga" \
        -H "$CONTENT_TYPE" \
        -d '{
            "businessType": "ORDER_PROCESS",
            "businessData": {
                "orderId": "demo-order-002",
                "userId": "demo-user-002",
                "workflow": "complete-order-process",
                "steps": [
                    "validate-order",
                    "reserve-inventory",
                    "process-payment",
                    "ship-order",
                    "send-notification"
                ]
            }
        }' | jq '.' 2>/dev/null || echo "SAGA事务请求已发送"
    echo ""
    
    # 4. 异步事务
    log_info "4. 执行异步分布式事务"
    curl -s -X POST "$BASE_URL/v1/transaction/async" \
        -H "$CONTENT_TYPE" \
        -d '{
            "transactionType": "AT",
            "businessType": "INVENTORY_UPDATE",
            "businessData": {
                "productId": "prod-001",
                "operation": "DECREASE",
                "quantity": 2,
                "reason": "order-fulfillment",
                "orderId": "demo-order-001"
            }
        }' | jq '.' 2>/dev/null || echo "异步事务请求已发送"
    echo ""
    
    # 5. 批量事务
    log_info "5. 执行批量分布式事务"
    curl -s -X POST "$BASE_URL/v1/transaction/batch" \
        -H "$CONTENT_TYPE" \
        -d '{
            "transactionType": "AT",
            "transactions": [
                {
                    "businessType": "USER_REGISTER",
                    "businessData": {
                        "userId": "batch-user-001",
                        "email": "user001@example.com"
                    }
                },
                {
                    "businessType": "USER_REGISTER",
                    "businessData": {
                        "userId": "batch-user-002",
                        "email": "user002@example.com"
                    }
                },
                {
                    "businessType": "USER_REGISTER",
                    "businessData": {
                        "userId": "batch-user-003",
                        "email": "user003@example.com"
                    }
                }
            ]
        }' | jq '.' 2>/dev/null || echo "批量事务请求已发送"
    echo ""
}

# 演示监控功能
demo_monitoring() {
    echo ""
    log_demo "=== 系统监控功能演示 ==="
    
    # 1. 健康检查
    log_info "1. 系统健康检查"
    curl -s "$BASE_URL/actuator/health" | jq '.' 2>/dev/null || echo "健康检查请求已发送"
    echo ""
    
    # 2. 系统信息
    log_info "2. 系统信息"
    curl -s "$BASE_URL/actuator/info" | jq '.' 2>/dev/null || echo "系统信息请求已发送"
    echo ""
    
    # 3. 指标概览
    log_info "3. 系统指标概览"
    curl -s "$BASE_URL/actuator/metrics" | jq '.names | sort' 2>/dev/null || echo "指标列表请求已发送"
    echo ""
    
    # 4. JVM内存使用
    log_info "4. JVM内存使用情况"
    curl -s "$BASE_URL/actuator/metrics/jvm.memory.used" | jq '.' 2>/dev/null || echo "内存指标请求已发送"
    echo ""
}

# 演示数据查询功能
demo_data_query() {
    echo ""
    log_demo "=== 数据查询功能演示 ==="
    
    # 1. 获取节点历史
    log_info "1. 获取当前节点事件历史"
    NODE_ID=$(curl -s "$BASE_URL/v1/timestamp/status" | jq -r '.nodeId' 2>/dev/null || echo "node-1")
    curl -s "$BASE_URL/v1/timestamp/history/$NODE_ID?limit=10" | jq '.' 2>/dev/null || echo "历史查询请求已发送"
    echo ""
    
    # 2. 时间范围查询
    log_info "2. 查询最近1小时的事件"
    START_TIME=$(date -d '1 hour ago' -Iseconds)
    END_TIME=$(date -Iseconds)
    curl -s "$BASE_URL/v1/timestamp/events?startTime=$START_TIME&endTime=$END_TIME" | jq '.' 2>/dev/null || echo "时间范围查询请求已发送"
    echo ""
    
    # 3. 同步所有时间戳
    log_info "3. 同步所有时间戳"
    curl -s -X POST "$BASE_URL/v1/timestamp/sync-all" | jq '.' 2>/dev/null || echo "同步请求已发送"
    echo ""
}

# 性能测试演示
demo_performance() {
    echo ""
    log_demo "=== 性能测试演示 ==="
    
    log_info "执行并发创建事件测试..."
    
    # 创建临时脚本进行并发测试
    cat > /tmp/concurrent_test.sh << 'EOF'
#!/bin/bash
BASE_URL="http://localhost:8080/api"
for i in {1..10}; do
    curl -s -X POST "$BASE_URL/v1/timestamp/event" \
        -H "Content-Type: application/json" \
        -d "{
            \"eventType\": \"PERF_TEST\",
            \"eventData\": {
                \"testId\": \"perf-test-$i\",
                \"timestamp\": \"$(date -Iseconds)\",
                \"threadId\": \"$$\"
            }
        }" > /dev/null &
done
wait
echo "并发测试完成"
EOF
    
    chmod +x /tmp/concurrent_test.sh
    
    # 执行并发测试
    time /tmp/concurrent_test.sh
    
    # 清理临时文件
    rm -f /tmp/concurrent_test.sh
    
    log_success "性能测试完成"
    echo ""
}

# 显示API文档链接
show_api_docs() {
    echo ""
    log_demo "=== API文档和监控链接 ==="
    
    log_info "API文档: http://localhost:8080/api/swagger-ui.html"
    log_info "健康检查: http://localhost:8080/api/actuator/health"
    log_info "系统指标: http://localhost:8080/api/actuator/metrics"
    log_info "Prometheus指标: http://localhost:8080/api/actuator/prometheus"
    
    echo ""
    log_info "详细API文档请查看: docs/api-documentation.md"
    log_info "部署指南请查看: DEPLOYMENT_GUIDE.md"
    log_info "项目总结请查看: PROJECT_SUMMARY.md"
}

# 主函数
main() {
    echo -e "${PURPLE}======================================${NC}"
    echo -e "${PURPLE}  分布式时间戳系统功能演示${NC}"
    echo -e "${PURPLE}======================================${NC}"
    
    # 解析命令行参数
    DEMO_TYPE="all"
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --timestamp)
                DEMO_TYPE="timestamp"
                shift
                ;;
            --transaction)
                DEMO_TYPE="transaction"
                shift
                ;;
            --monitoring)
                DEMO_TYPE="monitoring"
                shift
                ;;
            --performance)
                DEMO_TYPE="performance"
                shift
                ;;
            --query)
                DEMO_TYPE="query"
                shift
                ;;
            --base-url)
                BASE_URL="$2"
                shift 2
                ;;
            -h|--help)
                echo "用法: $0 [选项]"
                echo "选项:"
                echo "  --timestamp    仅演示时间戳功能"
                echo "  --transaction  仅演示分布式事务功能"
                echo "  --monitoring   仅演示监控功能"
                echo "  --performance  仅演示性能测试"
                echo "  --query        仅演示数据查询功能"
                echo "  --base-url URL 指定服务基础URL (默认: http://localhost:8080/api)"
                echo "  -h, --help     显示帮助信息"
                exit 0
                ;;
            *)
                log_error "未知选项: $1"
                exit 1
                ;;
        esac
    done
    
    # 检查服务状态
    check_service
    
    # 根据参数执行相应演示
    case $DEMO_TYPE in
        "timestamp")
            demo_timestamp
            ;;
        "transaction")
            demo_transaction
            ;;
        "monitoring")
            demo_monitoring
            ;;
        "performance")
            demo_performance
            ;;
        "query")
            demo_data_query
            ;;
        "all")
            demo_timestamp
            demo_transaction
            demo_monitoring
            demo_data_query
            demo_performance
            ;;
    esac
    
    # 显示API文档链接
    show_api_docs
    
    echo ""
    log_success "演示完成！"
    log_info "您可以通过以下方式继续探索系统："
    log_info "1. 访问Swagger UI查看完整API文档"
    log_info "2. 使用Postman或curl测试其他API"
    log_info "3. 查看应用日志了解系统运行情况"
    log_info "4. 监控系统指标和性能数据"
}

# 执行主函数
main "$@"