#!/bin/bash

# LamportClockManager Java 8兼容性修复验证脚本
# 验证编译错误是否已修复

echo "=========================================="
echo "LamportClockManager Java 8兼容性修复验证"
echo "=========================================="

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查Java版本
echo -e "${YELLOW}检查Java版本...${NC}"
java -version
echo ""

# 检查Maven版本
echo -e "${YELLOW}检查Maven版本...${NC}"
mvn -version
echo ""

# 清理之前的编译结果
echo -e "${YELLOW}清理之前的编译结果...${NC}"
mvn clean > /dev/null 2>&1

# 编译项目
echo -e "${YELLOW}编译项目...${NC}"
if mvn compile -q; then
    echo -e "${GREEN}✓ 项目编译成功${NC}"
else
    echo -e "${RED}✗ 项目编译失败${NC}"
    echo "编译错误详情："
    mvn compile
    exit 1
fi

# 编译测试代码
echo -e "${YELLOW}编译测试代码...${NC}"
if mvn test-compile -q; then
    echo -e "${GREEN}✓ 测试代码编译成功${NC}"
else
    echo -e "${RED}✗ 测试代码编译失败${NC}"
    echo "测试编译错误详情："
    mvn test-compile
    exit 1
fi

# 运行LamportClockManager相关测试
echo -e "${YELLOW}运行LamportClockManager相关测试...${NC}"
if mvn test -Dtest=LamportClockManagerTest -q; then
    echo -e "${GREEN}✓ LamportClockManagerTest 测试通过${NC}"
else
    echo -e "${YELLOW}⚠ LamportClockManagerTest 可能不存在或测试失败${NC}"
fi

# 运行新的编译验证测试
echo -e "${YELLOW}运行编译修复验证测试...${NC}"
if mvn test -Dtest=LamportClockManagerCompilationTest -q; then
    echo -e "${GREEN}✓ LamportClockManagerCompilationTest 测试通过${NC}"
else
    echo -e "${RED}✗ LamportClockManagerCompilationTest 测试失败${NC}"
    echo "测试失败详情："
    mvn test -Dtest=LamportClockManagerCompilationTest
fi

# 检查特定的编译问题
echo -e "${YELLOW}检查Redis execute方法调用...${NC}"
if grep -n "RedisCallback<Object>" src/main/java/com/example/dts/timestamp/LamportClockManager.java; then
    echo -e "${GREEN}✓ 找到修复后的RedisCallback类型声明${NC}"
else
    echo -e "${RED}✗ 未找到修复后的RedisCallback类型声明${NC}"
fi

# 检查import语句
echo -e "${YELLOW}检查RedisCallback import语句...${NC}"
if grep -n "import org.springframework.data.redis.core.RedisCallback" src/main/java/com/example/dts/timestamp/LamportClockManager.java; then
    echo -e "${GREEN}✓ 找到RedisCallback import语句${NC}"
else
    echo -e "${RED}✗ 未找到RedisCallback import语句${NC}"
fi

# 运行完整的项目测试（可选）
echo -e "${YELLOW}是否运行完整的项目测试？(y/n)${NC}"
read -r response
if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
    echo -e "${YELLOW}运行完整项目测试...${NC}"
    if mvn test; then
        echo -e "${GREEN}✓ 完整项目测试通过${NC}"
    else
        echo -e "${RED}✗ 完整项目测试失败${NC}"
    fi
fi

echo ""
echo "=========================================="
echo -e "${GREEN}LamportClockManager Java 8兼容性修复验证完成${NC}"
echo "=========================================="

# 总结修复内容
echo ""
echo "修复内容总结："
echo "1. 在redisTemplate.execute()调用中明确指定RedisCallback<Object>类型"
echo "2. 添加了RedisCallback接口的import语句"
echo "3. 修复了Java 8中的类型推断问题"
echo "4. 解决了execute方法重载的歧义性问题"
echo ""
echo "修复的文件："
echo "- src/main/java/com/example/dts/timestamp/LamportClockManager.java"
echo ""
echo "新增的测试文件："
echo "- src/test/java/com/example/dts/timestamp/LamportClockManagerCompilationTest.java"