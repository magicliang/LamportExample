#!/bin/bash

# XA 和 JTA 分布式事务演示脚本
# 演示各种 XA 和 JTA 事务场景

set -e

# 配置
BASE_URL="http://localhost:8080/api/v1/xa-jta"
CONTENT_TYPE="Content-Type: application/json"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# 检查服务是否运行
check_service() {
    log_info "检查服务状态..."
    
    if curl -s "$BASE_URL/health" > /dev/null 2>&1; then
        log_success "服务运行正常"
    else
        log_error "服务未运行，请先启动应用"
        exit 1
    fi
}

# 演示 XA 事务 - 订单支付
demo_xa_order_payment() {
    log_info "演示 XA 事务 - 订单支付"
    
    local request_data='{
        "businessType": "ORDER_PAYMENT",
        "businessData": {
            "orderId": "ORDER_001",
            "customerId": "CUSTOMER_001",
            "amount": 299.99,
            "currency": "CNY",
            "paymentMethod": "CREDIT_CARD",
            "items": [
                {
                    "productId": "P001",
                    "productName": "智能手机",
                    "price": 199.99,
                    "quantity": 1
                },
                {
                    "productId": "P002",
                    "productName": "手机壳",
                    "price": 99.99,
                    "quantity": 1
                }
            ]
        }
    }'
    
    log_info "发送 XA 订单支付请求..."
    local response=$(curl -s -X POST "$BASE_URL/xa/execute" \
        -H "$CONTENT_TYPE" \
        -d "$request_data")
    
    echo "响应结果:"
    echo "$response" | jq '.'
    
    local status=$(echo "$response" | jq -r '.status')
    if [ "$status" = "SUCCESS" ]; then
        log_success "XA 订单支付事务执行成功"
        local transaction_id=$(echo "$response" | jq -r '.transactionId')
        log_info "事务ID: $transaction_id"
        
        # 查询事务日志
        log_info "查询事务日志..."
        curl -s "$BASE_URL/xa/logs/$transaction_id" | jq '.'
    else
        log_error "XA 订单支付事务执行失败"
    fi
    
    echo ""
}

# 演示 XA 事务 - 库存更新
demo_xa_inventory_update() {
    log_info "演示 XA 事务 - 库存更新"
    
    local request_data='{
        "businessType": "INVENTORY_UPDATE",
        "businessData": {
            "warehouseId": "WH_001",
            "updates": [
                {
                    "productId": "P001",
                    "quantity": -1,
                    "operation": "DECREASE",
                    "reason": "SALE"
                },
                {
                    "productId": "P002",
                    "quantity": -1,
                    "operation": "DECREASE",
                    "reason": "SALE"
                },
                {
                    "productId": "P003",
                    "quantity": 50,
                    "operation": "INCREASE",
                    "reason": "RESTOCK"
                }
            ]
        }
    }'
    
    log_info "发送 XA 库存更新请求..."
    local response=$(curl -s -X POST "$BASE_URL/xa/execute" \
        -H "$CONTENT_TYPE" \
        -d "$request_data")
    
    echo "响应结果:"
    echo "$response" | jq '.'
    
    local status=$(echo "$response" | jq -r '.status')
    if [ "$status" = "SUCCESS" ]; then
        log_success "XA 库存更新事务执行成功"
    else
        log_error "XA 库存更新事务执行失败"
    fi
    
    echo ""
}

# 演示 XA 事务 - 用户注册
demo_xa_user_registration() {
    log_info "演示 XA 事务 - 用户注册"
    
    local request_data='{
        "businessType": "USER_REGISTRATION",
        "businessData": {
            "username": "newuser123",
            "email": "newuser123@example.com",
            "password": "securePassword123",
            "profile": {
                "firstName": "张",
                "lastName": "三",
                "phone": "13800138000",
                "address": {
                    "country": "中国",
                    "province": "北京市",
                    "city": "北京市",
                    "district": "朝阳区",
                    "street": "建国路1号"
                }
            },
            "preferences": {
                "language": "zh-CN",
                "timezone": "Asia/Shanghai",
                "notifications": {
                    "email": true,
                    "sms": false,
                    "push": true
                }
            }
        }
    }'
    
    log_info "发送 XA 用户注册请求..."
    local response=$(curl -s -X POST "$BASE_URL/xa/execute" \
        -H "$CONTENT_TYPE" \
        -d "$request_data")
    
    echo "响应结果:"
    echo "$response" | jq '.'
    
    local status=$(echo "$response" | jq -r '.status')
    if [ "$status" = "SUCCESS" ]; then
        log_success "XA 用户注册事务执行成功"
    else
        log_error "XA 用户注册事务执行失败"
    fi
    
    echo ""
}

# 演示异步 XA 事务
demo_xa_async() {
    log_info "演示异步 XA 事务"
    
    local request_data='{
        "businessType": "ORDER_PAYMENT",
        "businessData": {
            "orderId": "ASYNC_ORDER_001",
            "amount": 199.99,
            "async": true
        }
    }'
    
    log_info "发送异步 XA 事务请求..."
    local response=$(curl -s -X POST "$BASE_URL/xa/execute-async" \
        -H "$CONTENT_TYPE" \
        -d "$request_data")
    
    echo "响应结果:"
    echo "$response" | jq '.'
    
    local status=$(echo "$response" | jq -r '.status')
    if [ "$status" = "ACCEPTED" ]; then
        log_success "异步 XA 事务提交成功"
        log_info "事务正在后台处理..."
    else
        log_error "异步 XA 事务提交失败"
    fi
    
    echo ""
}

# 演示批量 XA 事务
demo_xa_batch() {
    log_info "演示批量 XA 事务"
    
    local request_data='{
        "transactions": [
            {
                "businessType": "ORDER_PAYMENT",
                "businessData": {
                    "orderId": "BATCH_ORDER_001",
                    "amount": 99.99
                }
            },
            {
                "businessType": "INVENTORY_UPDATE",
                "businessData": {
                    "productId": "P001",
                    "quantity": -1
                }
            },
            {
                "businessType": "USER_REGISTRATION",
                "businessData": {
                    "username": "batchuser001",
                    "email": "batchuser001@example.com"
                }
            }
        ]
    }'
    
    log_info "发送批量 XA 事务请求..."
    local response=$(curl -s -X POST "$BASE_URL/xa/batch-execute" \
        -H "$CONTENT_TYPE" \
        -d "$request_data")
    
    echo "响应结果:"
    echo "$response" | jq '.'
    
    local status=$(echo "$response" | jq -r '.status')
    if [ "$status" = "SUCCESS" ]; then
        log_success "批量 XA 事务执行成功"
        local total=$(echo "$response" | jq -r '.totalTransactions')
        log_info "总共处理了 $total 个事务"
    else
        log_error "批量 XA 事务执行失败"
    fi
    
    echo ""
}

# 演示 JTA 事务 - 复杂订单
demo_jta_complex_order() {
    log_info "演示 JTA 事务 - 复杂订单"
    
    local request_data='{
        "businessType": "COMPLEX_ORDER",
        "businessData": {
            "orderId": "COMPLEX_ORDER_001",
            "customerId": "VIP_CUSTOMER_001",
            "items": [
                {
                    "productId": "P001",
                    "productName": "高端笔记本电脑",
                    "price": 8999.99,
                    "quantity": 1,
                    "category": "ELECTRONICS"
                },
                {
                    "productId": "P002",
                    "productName": "无线鼠标",
                    "price": 299.99,
                    "quantity": 2,
                    "category": "ACCESSORIES"
                },
                {
                    "productId": "P003",
                    "productName": "机械键盘",
                    "price": 599.99,
                    "quantity": 1,
                    "category": "ACCESSORIES"
                }
            ],
            "discounts": [
                {
                    "type": "VIP_DISCOUNT",
                    "percentage": 10
                },
                {
                    "type": "BULK_DISCOUNT",
                    "amount": 200
                }
            ],
            "shipping": {
                "method": "EXPRESS",
                "address": {
                    "country": "中国",
                    "province": "上海市",
                    "city": "上海市",
                    "district": "浦东新区",
                    "street": "陆家嘴环路1000号"
                },
                "cost": 50.0
            },
            "payment": {
                "method": "CREDIT_CARD",
                "installments": 12
            }
        }
    }'
    
    log_info "发送 JTA 复杂订单请求..."
    local response=$(curl -s -X POST "$BASE_URL/jta/execute" \
        -H "$CONTENT_TYPE" \
        -d "$request_data")
    
    echo "响应结果:"
    echo "$response" | jq '.'
    
    local status=$(echo "$response" | jq -r '.status')
    if [ "$status" = "SUCCESS" ]; then
        log_success "JTA 复杂订单事务执行成功"
        local transaction_id=$(echo "$response" | jq -r '.transactionId')
        log_info "事务ID: $transaction_id"
        
        # 查询事务状态
        log_info "查询事务状态..."
        curl -s "$BASE_URL/jta/status/$transaction_id" | jq '.'
    else
        log_error "JTA 复杂订单事务执行失败"
    fi
    
    echo ""
}

# 演示 JTA 事务 - 批量更新
demo_jta_batch_update() {
    log_info "演示 JTA 事务 - 批量更新"
    
    local request_data='{
        "businessType": "BATCH_UPDATE",
        "businessData": {
            "updateType": "PRICE_ADJUSTMENT",
            "updates": [
                {
                    "id": "P001",
                    "field": "price",
                    "oldValue": 999.99,
                    "newValue": 899.99,
                    "reason": "PROMOTION"
                },
                {
                    "id": "P002",
                    "field": "stock",
                    "oldValue": 100,
                    "newValue": 150,
                    "reason": "RESTOCK"
                },
                {
                    "id": "P003",
                    "field": "status",
                    "oldValue": "INACTIVE",
                    "newValue": "ACTIVE",
                    "reason": "PRODUCT_LAUNCH"
                },
                {
                    "id": "P004",
                    "field": "category",
                    "oldValue": "ELECTRONICS",
                    "newValue": "SMART_DEVICES",
                    "reason": "CATEGORY_RESTRUCTURE"
                }
            ],
            "batchId": "BATCH_001",
            "operator": "ADMIN_USER",
            "timestamp": "2024-01-15T10:30:00Z"
        }
    }'
    
    log_info "发送 JTA 批量更新请求..."
    local response=$(curl -s -X POST "$BASE_URL/jta/execute" \
        -H "$CONTENT_TYPE" \
        -d "$request_data")
    
    echo "响应结果:"
    echo "$response" | jq '.'
    
    local status=$(echo "$response" | jq -r '.status')
    if [ "$status" = "SUCCESS" ]; then
        log_success "JTA 批量更新事务执行成功"
    else
        log_error "JTA 批量更新事务执行失败"
    fi
    
    echo ""
}

# 演示 JTA 事务 - 数据迁移
demo_jta_data_migration() {
    log_info "演示 JTA 事务 - 数据迁移"
    
    local request_data='{
        "businessType": "DATA_MIGRATION",
        "businessData": {
            "migrationId": "MIGRATION_001",
            "sourceTable": "legacy_users",
            "targetTable": "users_v2",
            "migrationRules": [
                {
                    "sourceField": "user_name",
                    "targetField": "username",
                    "transformation": "LOWERCASE"
                },
                {
                    "sourceField": "email_addr",
                    "targetField": "email",
                    "transformation": "NONE"
                },
                {
                    "sourceField": "create_time",
                    "targetField": "created_at",
                    "transformation": "TIMESTAMP_FORMAT"
                }
            ],
            "batchSize": 1000,
            "totalRecords": 50000,
            "startTime": "2024-01-15T00:00:00Z"
        }
    }'
    
    log_info "发送 JTA 数据迁移请求..."
    local response=$(curl -s -X POST "$BASE_URL/jta/execute" \
        -H "$CONTENT_TYPE" \
        -d "$request_data")
    
    echo "响应结果:"
    echo "$response" | jq '.'
    
    local status=$(echo "$response" | jq -r '.status')
    if [ "$status" = "SUCCESS" ]; then
        log_success "JTA 数据迁移事务执行成功"
    else
        log_error "JTA 数据迁移事务执行失败"
    fi
    
    echo ""
}

# 演示错误场景 - 无效数据
demo_error_scenarios() {
    log_info "演示错误场景 - 无效数据"
    
    # 无效的订单支付（负金额）
    log_info "测试无效订单支付（负金额）..."
    local invalid_payment='{
        "businessType": "ORDER_PAYMENT",
        "businessData": {
            "orderId": "INVALID_ORDER_001",
            "amount": -100.0
        }
    }'
    
    local response=$(curl -s -X POST "$BASE_URL/xa/execute" \
        -H "$CONTENT_TYPE" \
        -d "$invalid_payment")
    
    echo "无效支付响应:"
    echo "$response" | jq '.'
    
    # 缺少必要字段的用户注册
    log_info "测试缺少必要字段的用户注册..."
    local invalid_registration='{
        "businessType": "USER_REGISTRATION",
        "businessData": {
            "username": "incompleteuser"
        }
    }'
    
    response=$(curl -s -X POST "$BASE_URL/xa/execute" \
        -H "$CONTENT_TYPE" \
        -d "$invalid_registration")
    
    echo "无效注册响应:"
    echo "$response" | jq '.'
    
    echo ""
}

# 获取统计信息
get_statistics() {
    log_info "获取 XA 事务统计信息"
    
    local stats=$(curl -s "$BASE_URL/xa/statistics")
    echo "XA 事务统计:"
    echo "$stats" | jq '.'
    
    log_info "获取活跃的 JTA 事务"
    local active=$(curl -s "$BASE_URL/jta/active")
    echo "活跃的 JTA 事务:"
    echo "$active" | jq '.'
    
    echo ""
}

# 清理操作
cleanup() {
    log_info "执行清理操作"
    
    local cleanup_response=$(curl -s -X POST "$BASE_URL/jta/cleanup")
    echo "清理响应:"
    echo "$cleanup_response" | jq '.'
    
    echo ""
}

# 主函数
main() {
    echo "=========================================="
    echo "XA 和 JTA 分布式事务演示"
    echo "=========================================="
    echo ""
    
    # 检查服务状态
    check_service
    echo ""
    
    # XA 事务演示
    echo "========== XA 事务演示 =========="
    demo_xa_order_payment
    demo_xa_inventory_update
    demo_xa_user_registration
    demo_xa_async
    demo_xa_batch
    
    # JTA 事务演示
    echo "========== JTA 事务演示 =========="
    demo_jta_complex_order
    demo_jta_batch_update
    demo_jta_data_migration
    
    # 错误场景演示
    echo "========== 错误场景演示 =========="
    demo_error_scenarios
    
    # 获取统计信息
    echo "========== 统计信息 =========="
    get_statistics
    
    # 清理操作
    echo "========== 清理操作 =========="
    cleanup
    
    log_success "演示完成！"
}

# 执行主函数
main "$@"