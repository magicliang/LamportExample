#!/bin/bash

# 编译修复验证测试脚本
# 用于验证之前修复的编译错误是否已解决

echo "=========================================="
echo "分布式时间戳系统 - 编译修复验证测试"
echo "=========================================="

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 记录开始时间
START_TIME=$(date +%s)

echo -e "${BLUE}1. 检查项目结构...${NC}"
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}错误: 未找到 pom.xml 文件${NC}"
    exit 1
fi

echo -e "${GREEN}✓ 项目结构检查通过${NC}"

echo -e "${BLUE}2. 清理之前的编译结果...${NC}"
mvn clean -q
echo -e "${GREEN}✓ 清理完成${NC}"

echo -e "${BLUE}3. 编译项目 (跳过测试)...${NC}"
if mvn compile -q -DskipTests; then
    echo -e "${GREEN}✓ 项目编译成功${NC}"
else
    echo -e "${RED}✗ 项目编译失败${NC}"
    echo -e "${YELLOW}编译错误详情:${NC}"
    mvn compile -DskipTests
    exit 1
fi

echo -e "${BLUE}4. 编译测试代码...${NC}"
if mvn test-compile -q -DskipTests; then
    echo -e "${GREEN}✓ 测试代码编译成功${NC}"
else
    echo -e "${RED}✗ 测试代码编译失败${NC}"
    echo -e "${YELLOW}测试编译错误详情:${NC}"
    mvn test-compile -DskipTests
    exit 1
fi

echo -e "${BLUE}5. 运行单元测试...${NC}"

# 运行特定的测试类来验证修复
echo -e "${YELLOW}5.1 运行 TimestampService 相关测试...${NC}"
if mvn test -Dtest=TimestampServiceIntegrationTest -q; then
    echo -e "${GREEN}✓ TimestampService 测试通过${NC}"
else
    echo -e "${RED}✗ TimestampService 测试失败${NC}"
    echo -e "${YELLOW}详细错误信息:${NC}"
    mvn test -Dtest=TimestampServiceIntegrationTest
fi

echo -e "${YELLOW}5.2 运行 XATransactionService 相关测试...${NC}"
if mvn test -Dtest=XATransactionServiceTest -q; then
    echo -e "${GREEN}✓ XATransactionService 测试通过${NC}"
else
    echo -e "${RED}✗ XATransactionService 测试失败${NC}"
    echo -e "${YELLOW}详细错误信息:${NC}"
    mvn test -Dtest=XATransactionServiceTest
fi

echo -e "${YELLOW}5.3 运行 JTATransactionService 相关测试...${NC}"
if mvn test -Dtest=JTATransactionServiceTest -q; then
    echo -e "${GREEN}✓ JTATransactionService 测试通过${NC}"
else
    echo -e "${RED}✗ JTATransactionService 测试失败${NC}"
    echo -e "${YELLOW}详细错误信息:${NC}"
    mvn test -Dtest=JTATransactionServiceTest
fi

echo -e "${YELLOW}5.4 运行 VectorClock 相关测试...${NC}"
if mvn test -Dtest=VectorClockTest -q; then
    echo -e "${GREEN}✓ VectorClock 测试通过${NC}"
else
    echo -e "${RED}✗ VectorClock 测试失败${NC}"
    echo -e "${YELLOW}详细错误信息:${NC}"
    mvn test -Dtest=VectorClockTest
fi

echo -e "${YELLOW}5.5 运行 LamportClockManager 相关测试...${NC}"
if mvn test -Dtest=LamportClockManagerTest -q; then
    echo -e "${GREEN}✓ LamportClockManager 测试通过${NC}"
else
    echo -e "${RED}✗ LamportClockManager 测试失败${NC}"
    echo -e "${YELLOW}详细错误信息:${NC}"
    mvn test -Dtest=LamportClockManagerTest
fi

echo -e "${BLUE}6. 验证修复的方法是否存在...${NC}"

echo -e "${YELLOW}6.1 检查 TimestampService.generateTimestampEvent 方法...${NC}"
if grep -q "generateTimestampEvent" src/main/java/com/example/dts/service/TimestampService.java; then
    echo -e "${GREEN}✓ generateTimestampEvent 方法存在${NC}"
else
    echo -e "${RED}✗ generateTimestampEvent 方法不存在${NC}"
fi

echo -e "${YELLOW}6.2 检查 TimestampEvent getter 方法...${NC}"
MISSING_METHODS=()

if ! grep -q "getEventId()" src/main/java/com/example/dts/model/TimestampEvent.java; then
    MISSING_METHODS+=("getEventId")
fi

if ! grep -q "getTimestamp()" src/main/java/com/example/dts/model/TimestampEvent.java; then
    MISSING_METHODS+=("getTimestamp")
fi

if ! grep -q "getBusinessType()" src/main/java/com/example/dts/model/TimestampEvent.java; then
    MISSING_METHODS+=("getBusinessType")
fi

if ! grep -q "getLamportClock()" src/main/java/com/example/dts/model/TimestampEvent.java; then
    MISSING_METHODS+=("getLamportClock")
fi

if [ ${#MISSING_METHODS[@]} -eq 0 ]; then
    echo -e "${GREEN}✓ 所有必需的 getter 方法都存在${NC}"
else
    echo -e "${RED}✗ 缺少以下 getter 方法: ${MISSING_METHODS[*]}${NC}"
fi

echo -e "${BLUE}7. 生成测试报告...${NC}"
mvn surefire-report:report -q

# 计算总耗时
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "=========================================="
echo -e "${GREEN}编译修复验证测试完成${NC}"
echo "总耗时: ${DURATION} 秒"
echo "=========================================="

# 显示测试结果摘要
echo -e "${BLUE}测试结果摘要:${NC}"
echo "- 项目编译: ✓"
echo "- 测试编译: ✓"
echo "- 方法修复验证: 请查看上述详细结果"
echo ""
echo -e "${YELLOW}如需查看详细测试报告，请查看:${NC}"
echo "target/site/surefire-report.html"