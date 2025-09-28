#!/bin/bash

# 分布式时间戳系统初始化设置脚本

set -e

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}正在初始化分布式时间戳系统...${NC}"

# 为脚本添加执行权限
echo "设置脚本执行权限..."
chmod +x scripts/build.sh
chmod +x scripts/deploy.sh
chmod +x scripts/setup.sh
chmod +x scripts/demo.sh

# 创建必要的目录
echo "创建必要的目录..."
mkdir -p logs
mkdir -p target
mkdir -p docker

# 检查Java环境
echo "检查Java环境..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
    echo "Java版本: $JAVA_VERSION"
else
    echo "警告: 未检测到Java环境"
fi

# 检查Maven环境
echo "检查Maven环境..."
if command -v mvn &> /dev/null; then
    MAVEN_VERSION=$(mvn -version | head -n1 | cut -d' ' -f3)
    echo "Maven版本: $MAVEN_VERSION"
else
    echo "警告: 未检测到Maven环境"
fi

# 检查Docker环境
echo "检查Docker环境..."
if command -v docker &> /dev/null; then
    DOCKER_VERSION=$(docker --version | cut -d' ' -f3 | cut -d',' -f1)
    echo "Docker版本: $DOCKER_VERSION"
else
    echo "警告: 未检测到Docker环境"
fi

# 检查kubectl环境
echo "检查kubectl环境..."
if command -v kubectl &> /dev/null; then
    KUBECTL_VERSION=$(kubectl version --client --short | cut -d' ' -f3)
    echo "kubectl版本: $KUBECTL_VERSION"
else
    echo "警告: 未检测到kubectl环境"
fi

echo -e "${GREEN}初始化完成！${NC}"
echo ""
echo "接下来您可以："
echo "1. 运行 './scripts/build.sh' 构建项目"
echo "2. 运行 'mvn spring-boot:run' 启动应用"
echo "3. 运行 './scripts/demo.sh' 演示系统功能"
echo "4. 运行 './scripts/deploy.sh' 部署到Kubernetes"
echo "5. 查看 'DEPLOYMENT_GUIDE.md' 获取详细部署指南"
echo "6. 查看 'docs/api-documentation.md' 获取API文档"