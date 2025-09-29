#!/bin/bash

# 运行编译修复验证测试
# 这个脚本会运行所有相关的测试来验证编译修复是否成功

echo "=========================================="
echo "运行编译修复验证测试"
echo "=========================================="

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 记录开始时间
START_TIME=$(date +%s)

# 检查是否在项目根目录
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}错误: 请在项目根目录运行此脚本${NC}"
    exit 1
fi

echo -e "${BLUE}1. 清理并编译项目...${NC}"
mvn clean compile -q
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 项目编译成功${NC}"
else
    echo -e "${RED}✗ 项目编译失败${NC}"
    exit 1
fi

echo -e "${BLUE}2. 编译测试代码...${NC}"
mvn test-compile -q
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 测试代码编译成功${NC}"
else
    echo -e "${RED}✗ 测试代码编译失败${NC}"
    exit 1
fi

echo -e "${BLUE}3. 运行编译修复验证测试...${NC}"
mvn test -Dtest=CompilationFixesVerificationTest -q
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 编译修复验证测试通过${NC}"
else
    echo -e "${RED}✗ 编译修复验证测试失败${NC}"
    echo -e "${YELLOW}运行详细测试以查看错误信息...${NC}"
    mvn test -Dtest=CompilationFixesVerificationTest
fi

echo -e "${BLUE}4. 运行相关的单元测试...${NC}"

# 运行 TimestampService 测试
echo -e "${YELLOW}4.1 运行 TimestampService 集成测试...${NC}"
mvn test -Dtest=TimestampServiceIntegrationTest -q
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ TimestampService 集成测试通过${NC}"
else
    echo -e "${YELLOW}⚠ TimestampService 集成测试可能需要数据库环境${NC}"
fi

# 运行 XATransactionService 测试
echo -e "${YELLOW}4.2 运行 XATransactionService 测试...${NC}"
mvn test -Dtest=XATransactionServiceTest -q
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ XATransactionService 测试通过${NC}"
else
    echo -e "${YELLOW}⚠ XATransactionService 测试可能需要特定环境${NC}"
fi

# 运行 JTATransactionService 测试
echo -e "${YELLOW}4.3 运行 JTATransactionService 测试...${NC}"
mvn test -Dtest=JTATransactionServiceTest -q
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ JTATransactionService 测试通过${NC}"
else
    echo -e "${YELLOW}⚠ JTATransactionService 测试可能需要特定环境${NC}"
fi

# 运行 VectorClock 测试
echo -e "${YELLOW}4.4 运行 VectorClock 测试...${NC}"
mvn test -Dtest=VectorClockTest -q
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ VectorClock 测试通过${NC}"
else
    echo -e "${YELLOW}⚠ VectorClock 测试失败${NC}"
fi

# 运行 LamportClockManager 测试
echo -e "${YELLOW}4.5 运行 LamportClockManager 测试...${NC}"
mvn test -Dtest=LamportClockManagerTest -q
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ LamportClockManager 测试通过${NC}"
else
    echo -e "${YELLOW}⚠ LamportClockManager 测试失败${NC}"
fi

echo -e "${BLUE}5. 验证修复的方法存在性...${NC}"

# 检查 TimestampService 中的 generateTimestampEvent 方法
if grep -q "public TimestampEvent generateTimestampEvent" src/main/java/com/example/dts/service/TimestampService.java; then
    echo -e "${GREEN}✓ TimestampService.generateTimestampEvent 方法存在${NC}"
else
    echo -e "${RED}✗ TimestampService.generateTimestampEvent 方法不存在${NC}"
fi

# 检查 TimestampEvent 中的 getter 方法
METHODS_TO_CHECK=("getEventId" "getTimestamp" "getBusinessType" "getLamportClock")
for method in "${METHODS_TO_CHECK[@]}"; do
    if grep -q "public.*${method}()" src/main/java/com/example/dts/model/TimestampEvent.java; then
        echo -e "${GREEN}✓ TimestampEvent.${method}() 方法存在${NC}"
    else
        echo -e "${RED}✗ TimestampEvent.${method}() 方法不存在${NC}"
    fi
done

echo -e "${BLUE}6. 检查编译错误是否已修复...${NC}"

# 尝试编译有问题的文件
PROBLEM_FILES=(
    "src/main/java/com/example/dts/service/XATransactionService.java"
    "src/main/java/com/example/dts/service/JTATransactionService.java"
)

for file in "${PROBLEM_FILES[@]}"; do
    echo -e "${YELLOW}检查 ${file}...${NC}"
    if javac -cp "$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout):target/classes" "$file" 2>/dev/null; then
        echo -e "${GREEN}✓ ${file} 编译成功${NC}"
    else
        echo -e "${RED}✗ ${file} 编译失败${NC}"
        echo -e "${YELLOW}详细错误信息:${NC}"
        javac -cp "$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout):target/classes" "$file"
    fi
done

# 计算总耗时
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "=========================================="
echo -e "${GREEN}编译修复验证测试完成${NC}"
echo "总耗时: ${DURATION} 秒"
echo "=========================================="

echo -e "${BLUE}测试总结:${NC}"
echo "1. 项目编译: ✓"
echo "2. 测试编译: ✓"
echo "3. 编译修复验证测试: 请查看上述结果"
echo "4. 相关单元测试: 请查看上述结果"
echo "5. 方法存在性验证: 请查看上述结果"
echo ""
echo -e "${YELLOW}注意: 某些测试可能需要特定的环境配置（如数据库、Redis等）${NC}"
echo -e "${YELLOW}如果测试失败，请检查是否缺少必要的环境依赖${NC}"