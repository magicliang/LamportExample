#!/bin/bash

# 分布式时间戳系统部署脚本
# 用于部署应用到Kubernetes集群

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置变量
NAMESPACE="dts-system"
APP_NAME="distributed-timestamp-system"
IMAGE_TAG="1.0.0"
K8S_DIR="k8s"

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

# 检查kubectl
check_kubectl() {
    log_info "检查kubectl..."
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl未安装或未配置到PATH"
        exit 1
    fi
    
    # 检查集群连接
    if ! kubectl cluster-info &> /dev/null; then
        log_error "无法连接到Kubernetes集群"
        exit 1
    fi
    
    log_success "kubectl检查通过"
}

# 检查Docker镜像
check_docker_image() {
    log_info "检查Docker镜像..."
    if ! docker images | grep -q "dts-system.*$IMAGE_TAG"; then
        log_warning "Docker镜像 dts-system:$IMAGE_TAG 不存在"
        log_info "请先运行构建脚本: ./scripts/build.sh"
        
        read -p "是否继续部署？(y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    else
        log_success "Docker镜像检查通过"
    fi
}

# 创建命名空间
create_namespace() {
    log_info "创建命名空间..."
    
    if kubectl get namespace $NAMESPACE &> /dev/null; then
        log_info "命名空间 $NAMESPACE 已存在"
    else
        kubectl apply -f $K8S_DIR/namespace.yaml
        log_success "命名空间创建完成"
    fi
}

# 部署密钥
deploy_secrets() {
    log_info "部署密钥配置..."
    
    # 检查是否需要更新数据库配置
    if [ -n "$MYSQL_HOST" ]; then
        log_info "使用环境变量中的数据库配置"
        
        # 创建临时密钥文件
        cat > /tmp/dts-secrets.yaml << EOF
apiVersion: v1
kind: Secret
metadata:
  name: dts-secrets
  namespace: $NAMESPACE
  labels:
    app: $APP_NAME
type: Opaque
stringData:
  mysql-host: "${MYSQL_HOST:-11.142.154.110}"
  mysql-port: "${MYSQL_PORT:-3306}"
  mysql-database: "${MYSQL_DATABASE:-gfaeofw8}"
  mysql-username: "${MYSQL_USERNAME:-with_zqwjnhlyktudftww}"
  mysql-password: "${MYSQL_PASSWORD:-IvE48V)DoJNcN&}"
  redis-host: "${REDIS_HOST:-redis}"
  redis-port: "${REDIS_PORT:-6379}"
  redis-password: "${REDIS_PASSWORD:-}"
  nacos-server: "${NACOS_SERVER:-nacos:8848}"
  seata-server: "${SEATA_SERVER:-seata:8091}"
EOF
        
        kubectl apply -f /tmp/dts-secrets.yaml
        rm -f /tmp/dts-secrets.yaml
    else
        kubectl apply -f $K8S_DIR/secrets.yaml
    fi
    
    log_success "密钥配置部署完成"
}

# 部署配置映射
deploy_configmap() {
    log_info "部署配置映射..."
    kubectl apply -f $K8S_DIR/configmap.yaml
    log_success "配置映射部署完成"
}

# 部署应用
deploy_application() {
    log_info "部署应用..."
    
    # 更新镜像标签
    sed -i.bak "s|image: dts-system:.*|image: dts-system:$IMAGE_TAG|g" $K8S_DIR/deployment.yaml
    
    kubectl apply -f $K8S_DIR/deployment.yaml
    
    # 恢复原文件
    mv $K8S_DIR/deployment.yaml.bak $K8S_DIR/deployment.yaml
    
    log_success "应用部署完成"
}

# 部署服务
deploy_services() {
    log_info "部署服务..."
    kubectl apply -f $K8S_DIR/service.yaml
    log_success "服务部署完成"
}

# 部署HPA
deploy_hpa() {
    log_info "部署水平Pod自动扩缩器..."
    
    # 检查metrics-server是否可用
    if kubectl get deployment metrics-server -n kube-system &> /dev/null; then
        kubectl apply -f $K8S_DIR/hpa.yaml
        log_success "HPA部署完成"
    else
        log_warning "metrics-server未安装，跳过HPA部署"
    fi
}

# 等待部署完成
wait_for_deployment() {
    log_info "等待部署完成..."
    
    kubectl rollout status deployment/dts-system -n $NAMESPACE --timeout=300s
    
    log_success "部署完成"
}

# 检查部署状态
check_deployment_status() {
    log_info "检查部署状态..."
    
    # 检查Pod状态
    log_info "Pod状态:"
    kubectl get pods -n $NAMESPACE -l app=$APP_NAME
    
    # 检查服务状态
    log_info "服务状态:"
    kubectl get services -n $NAMESPACE
    
    # 检查HPA状态
    if kubectl get hpa -n $NAMESPACE &> /dev/null; then
        log_info "HPA状态:"
        kubectl get hpa -n $NAMESPACE
    fi
    
    # 获取服务访问信息
    NODEPORT=$(kubectl get service dts-system-nodeport -n $NAMESPACE -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || echo "N/A")
    if [ "$NODEPORT" != "N/A" ]; then
        log_info "应用访问地址: http://<节点IP>:$NODEPORT/api"
    fi
    
    CLUSTER_IP=$(kubectl get service dts-system-service -n $NAMESPACE -o jsonpath='{.spec.clusterIP}' 2>/dev/null || echo "N/A")
    if [ "$CLUSTER_IP" != "N/A" ]; then
        log_info "集群内访问地址: http://$CLUSTER_IP:8080/api"
    fi
}

# 显示日志
show_logs() {
    log_info "显示应用日志..."
    kubectl logs -n $NAMESPACE -l app=$APP_NAME --tail=50
}

# 卸载应用
uninstall() {
    log_warning "卸载应用..."
    
    read -p "确定要卸载应用吗？(y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "取消卸载"
        exit 0
    fi
    
    kubectl delete -f $K8S_DIR/hpa.yaml --ignore-not-found=true
    kubectl delete -f $K8S_DIR/service.yaml --ignore-not-found=true
    kubectl delete -f $K8S_DIR/deployment.yaml --ignore-not-found=true
    kubectl delete -f $K8S_DIR/configmap.yaml --ignore-not-found=true
    kubectl delete -f $K8S_DIR/secrets.yaml --ignore-not-found=true
    kubectl delete -f $K8S_DIR/namespace.yaml --ignore-not-found=true
    
    log_success "应用卸载完成"
}

# 显示帮助信息
show_help() {
    echo "用法: $0 [命令] [选项]"
    echo ""
    echo "命令:"
    echo "  deploy     部署应用到Kubernetes集群"
    echo "  status     检查部署状态"
    echo "  logs       显示应用日志"
    echo "  uninstall  卸载应用"
    echo "  help       显示帮助信息"
    echo ""
    echo "选项:"
    echo "  --image-tag TAG    指定镜像标签 (默认: $IMAGE_TAG)"
    echo "  --namespace NS     指定命名空间 (默认: $NAMESPACE)"
    echo ""
    echo "环境变量:"
    echo "  MYSQL_HOST         MySQL主机地址"
    echo "  MYSQL_PORT         MySQL端口"
    echo "  MYSQL_DATABASE     MySQL数据库名"
    echo "  MYSQL_USERNAME     MySQL用户名"
    echo "  MYSQL_PASSWORD     MySQL密码"
    echo "  REDIS_HOST         Redis主机地址"
    echo "  REDIS_PORT         Redis端口"
    echo "  REDIS_PASSWORD     Redis密码"
}

# 主函数
main() {
    # 解析命令行参数
    COMMAND="deploy"
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            deploy|status|logs|uninstall|help)
                COMMAND=$1
                shift
                ;;
            --image-tag)
                IMAGE_TAG="$2"
                shift 2
                ;;
            --namespace)
                NAMESPACE="$2"
                shift 2
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                log_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 执行命令
    case $COMMAND in
        deploy)
            log_info "开始部署分布式时间戳系统..."
            check_kubectl
            check_docker_image
            create_namespace
            deploy_secrets
            deploy_configmap
            deploy_application
            deploy_services
            deploy_hpa
            wait_for_deployment
            check_deployment_status
            log_success "部署完成！"
            ;;
        status)
            check_kubectl
            check_deployment_status
            ;;
        logs)
            check_kubectl
            show_logs
            ;;
        uninstall)
            check_kubectl
            uninstall
            ;;
        help)
            show_help
            ;;
        *)
            log_error "未知命令: $COMMAND"
            show_help
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"