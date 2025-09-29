#!/bin/bash

# 快速编译修复验证脚本
echo "🔍 快速验证编译修复..."

# 检查项目结构
if [ ! -f "pom.xml" ]; then
    echo "❌ 错误: 请在项目根目录运行此脚本"
    exit 1
fi

echo "📦 清理并编译项目..."
mvn clean compile -q

if [ $? -eq 0 ]; then
    echo "✅ 项目编译成功！"
else
    echo "❌ 项目编译失败"
    exit 1
fi

echo "🧪 编译测试代码..."
mvn test-compile -q

if [ $? -eq 0 ]; then
    echo "✅ 测试代码编译成功！"
else
    echo "❌ 测试代码编译失败"
    exit 1
fi

echo "🔬 运行编译修复验证测试..."
mvn test -Dtest=CompilationFixesVerificationTest -q

if [ $? -eq 0 ]; then
    echo "✅ 编译修复验证测试通过！"
    echo ""
    echo "🎉 所有编译错误已成功修复！"
    echo ""
    echo "📋 修复摘要:"
    echo "   • 添加了 TimestampService.generateTimestampEvent() 方法"
    echo "   • 添加了 TimestampEvent 的 getter 方法"
    echo "   • 修复了事务服务中的方法调用错误"
    echo ""
    echo "📖 查看详细报告: TEST_VERIFICATION_REPORT.md"
else
    echo "❌ 编译修复验证测试失败"
    echo "🔍 运行详细测试查看错误信息..."
    mvn test -Dtest=CompilationFixesVerificationTest
fi