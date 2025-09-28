#!/bin/bash

# 分布式时间戳系统构建脚本
# 用于编译、测试和打包应用

set -e

# 颜色定义
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

# 检查Java环境
check_java() {
    log_info "检查Java环境..."
    if ! command -v java &> /dev/null; then
        log_error "Java未安装或未配置到PATH"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
    log_info "Java版本: $JAVA_VERSION"
    
    if [[ "$JAVA_VERSION" < "1.8" ]]; then
        log_error "需要Java 8或更高版本"
        exit 1
    fi
    
    log_success "Java环境检查通过"
}

# 检查Maven环境
check_maven() {
    log_info "检查Maven环境..."
    if ! command -v mvn &> /dev/null; then
        log_error "Maven未安装或未配置到PATH"
        exit 1
    fi
    
    MAVEN_VERSION=$(mvn -version | head -n1 | cut -d' ' -f3)
    log_info "Maven版本: $MAVEN_VERSION"
    log_success "Maven环境检查通过"
}

# 清理构建目录
clean_build() {
    log_info "清理构建目录..."
    mvn clean
    log_success "构建目录清理完成"
}

# 编译代码
compile_code() {
    log_info "编译代码..."
    mvn compile
    log_success "代码编译完成"
}

# 运行单元测试
run_unit_tests() {
    log_info "运行单元测试..."
    mvn test
    log_success "单元测试完成"
}

# 运行集成测试
run_integration_tests() {
    log_info "运行集成测试..."
    mvn verify -P integration-test
    log_success "集成测试完成"
}

# 打包应用
package_app() {
    log_info "打包应用..."
    mvn package -DskipTests
    log_success "应用打包完成"
}

# 构建Docker镜像
build_docker_image() {
    log_info "构建Docker镜像..."
    
    # 检查Docker是否可用
    if ! command -v docker &> /dev/null; then
        log_warning "Docker未安装，跳过镜像构建"
        return 0
    fi
    
    # 构建镜像
    docker build -t dts-system:1.0.0 .
    docker tag dts-system:1.0.0 dts-system:latest
    
    log_success "Docker镜像构建完成"
}

# 生成测试报告
generate_reports() {
    log_info "生成测试报告..."
    
    # 生成测试覆盖率报告
    if [ -f "target/site/jacoco/index.html" ]; then
        log_info "测试覆盖率报告: target/site/jacoco/index.html"
    fi
    
    # 生成Surefire测试报告
    if [ -d "target/surefire-reports" ]; then
        log_info "单元测试报告: target/surefire-reports/"
    fi
    
    # 生成Failsafe测试报告
    if [ -d "target/failsafe-reports" ]; then
        log_info "集成测试报告: target/failsafe-reports/"
    fi
    
    log_success "测试报告生成完成"
}

# 主函数
main() {
    log_info "开始构建分布式时间戳系统..."
    
    # 解析命令行参数
    SKIP_TESTS=false
    SKIP_DOCKER=false
    RUN_INTEGRATION_TESTS=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-tests)
                SKIP_TESTS=true
                shift
                ;;
            --skip-docker)
                SKIP_DOCKER=true
                shift
                ;;
            --integration-tests)
                RUN_INTEGRATION_TESTS=true
                shift
                ;;
            -h|--help)
                echo "用法: $0 [选项]"
                echo "选项:"
                echo "  --skip-tests         跳过测试"
                echo "  --skip-docker        跳过Docker镜像构建"
                echo "  --integration-tests  运行集成测试"
                echo "  -h, --help          显示帮助信息"
                exit 0
                ;;
            *)
                log_error "未知选项: $1"
                exit 1
                ;;
        esac
    done
    
    # 执行构建步骤
    check_java
    check_maven
    clean_build
    compile_code
    
    if [ "$SKIP_TESTS" = false ]; then
        run_unit_tests
        if [ "$RUN_INTEGRATION_TESTS" = true ]; then
            run_integration_tests
        fi
        generate_reports
    fi
    
    package_app
    
    if [ "$SKIP_DOCKER" = false ]; then
        build_docker_image
    fi
    
    log_success "构建完成！"
    log_info "构建产物:"
    log_info "  - JAR文件: target/distributed-timestamp-system-1.0.0.jar"
    if [ "$SKIP_DOCKER" = false ] && command -v docker &> /dev/null; then
        log_info "  - Docker镜像: dts-system:1.0.0"
    fi
}

# 执行主函数
main "$@"