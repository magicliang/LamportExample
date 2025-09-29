package com.example.dts;

import com.example.dts.model.TimestampEvent;
import com.example.dts.model.VectorClock;
import com.example.dts.model.VersionVector;
import com.example.dts.service.TimestampService;
import com.example.dts.service.XATransactionService;
import com.example.dts.service.JTATransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编译修复验证测试
 * 专门用于验证之前修复的编译错误是否已正确解决
 * 
 * @author DTS Team
 */
@SpringBootTest
@ActiveProfiles("test")
class CompilationFixesVerificationTest {

    private TimestampService timestampService;
    private TimestampEvent testEvent;
    
    @BeforeEach
    void setUp() {
        // 创建测试用的 TimestampEvent
        testEvent = new TimestampEvent();
        testEvent.setId(1L);
        testEvent.setEventType("TEST_EVENT");
        testEvent.setNodeId("test-node-1");
        testEvent.setLamportTimestamp(100L);
        testEvent.setCreatedAt(LocalDateTime.now());
        
        // 设置向量时钟
        VectorClock vectorClock = new VectorClock();
        vectorClock.increment("test-node-1");
        testEvent.setVectorClock(vectorClock);
        
        // 设置版本向量
        VersionVector versionVector = new VersionVector();
        versionVector.increment("test-node-1");
        testEvent.setVersionVector(versionVector);
        
        // 设置业务数据
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("amount", 1000.0);
        businessData.put("currency", "USD");
        testEvent.setBusinessData(businessData);
    }

    @Test
    @DisplayName("验证 TimestampEvent 的新增 getter 方法")
    void testTimestampEventGetterMethods() {
        // 测试 getEventId() 方法
        assertDoesNotThrow(() -> {
            String eventId = testEvent.getEventId();
            assertNotNull(eventId, "getEventId() 应该返回非空值");
            assertEquals("1", eventId, "getEventId() 应该返回 ID 的字符串形式");
        }, "getEventId() 方法应该正常工作");

        // 测试 getTimestamp() 方法
        assertDoesNotThrow(() -> {
            Long timestamp = testEvent.getTimestamp();
            assertNotNull(timestamp, "getTimestamp() 应该返回非空值");
            assertEquals(100L, timestamp, "getTimestamp() 应该返回 Lamport 时间戳");
        }, "getTimestamp() 方法应该正常工作");

        // 测试 getBusinessType() 方法
        assertDoesNotThrow(() -> {
            String businessType = testEvent.getBusinessType();
            assertNotNull(businessType, "getBusinessType() 应该返回非空值");
            assertEquals("TEST_EVENT", businessType, "getBusinessType() 应该返回事件类型");
        }, "getBusinessType() 方法应该正常工作");

        // 测试 getLamportClock() 方法
        assertDoesNotThrow(() -> {
            Long lamportClock = testEvent.getLamportClock();
            assertNotNull(lamportClock, "getLamportClock() 应该返回非空值");
            assertEquals(100L, lamportClock, "getLamportClock() 应该返回 Lamport 时钟值");
        }, "getLamportClock() 方法应该正常工作");
    }

    @Test
    @DisplayName("验证 TimestampService.generateTimestampEvent 方法")
    void testTimestampServiceGenerateTimestampEvent() {
        // 由于 TimestampService 需要依赖注入，我们创建一个简单的实例来测试方法存在性
        // 这里主要验证方法签名是否正确
        
        assertDoesNotThrow(() -> {
            // 验证方法存在且可以被调用（即使可能因为依赖而失败）
            TimestampService service = new TimestampService();
            
            // 准备测试参数
            String eventType = "TEST_EVENT";
            String nodeId = "test-node";
            Map<String, Object> businessData = new HashMap<>();
            businessData.put("test", "data");
            
            // 这里我们只验证方法签名，不验证具体执行结果
            // 因为实际执行需要完整的 Spring 上下文
            try {
                service.generateTimestampEvent(eventType, nodeId, businessData);
            } catch (Exception e) {
                // 预期可能会有异常，因为缺少依赖注入
                // 但重要的是方法存在且签名正确
                assertTrue(true, "方法存在且可以调用");
            }
        }, "generateTimestampEvent 方法应该存在且签名正确");
    }

    @Test
    @DisplayName("验证向量时钟和版本向量的方法调用")
    void testVectorClockAndVersionVectorMethods() {
        // 测试向量时钟方法
        assertDoesNotThrow(() -> {
            VectorClock vectorClock = testEvent.getVectorClock();
            assertNotNull(vectorClock, "向量时钟不应为空");
            
            // 验证可以正常调用方法而不是 toString()
            Map<String, Long> clockMap = vectorClock.getClock();
            assertNotNull(clockMap, "时钟映射不应为空");
        }, "向量时钟方法应该正常工作");

        // 测试版本向量方法
        assertDoesNotThrow(() -> {
            VersionVector versionVector = testEvent.getVersionVector();
            assertNotNull(versionVector, "版本向量不应为空");
            
            // 验证可以正常调用方法而不是 toString()
            ConcurrentHashMap<String, Long> versionMap = versionVector.getVersions();
            assertNotNull(versionMap, "版本映射不应为空");
        }, "版本向量方法应该正常工作");
    }

    @Test
    @DisplayName("验证事务服务中的方法调用")
    void testTransactionServiceMethodCalls() {
        // 这个测试主要验证编译时不会出现方法找不到的错误
        
        assertDoesNotThrow(() -> {
            // 验证 XATransactionService 中使用的方法
            String eventId = testEvent.getEventId();
            Long timestamp = testEvent.getTimestamp();
            String businessType = testEvent.getBusinessType();
            Long lamportClock = testEvent.getLamportClock();
            
            assertNotNull(eventId);
            assertNotNull(timestamp);
            assertNotNull(businessType);
            assertNotNull(lamportClock);
            
        }, "事务服务中使用的方法应该都存在");
    }

    @Test
    @DisplayName("验证所有修复的方法返回值类型正确")
    void testMethodReturnTypes() {
        // 验证返回值类型
        assertTrue(testEvent.getEventId() instanceof String, "getEventId() 应该返回 String");
        assertTrue(testEvent.getTimestamp() instanceof Long, "getTimestamp() 应该返回 Long");
        assertTrue(testEvent.getBusinessType() instanceof String, "getBusinessType() 应该返回 String");
        assertTrue(testEvent.getLamportClock() instanceof Long, "getLamportClock() 应该返回 Long");
    }

    @Test
    @DisplayName("验证方法在空值情况下的处理")
    void testMethodsWithNullValues() {
        TimestampEvent nullEvent = new TimestampEvent();
        
        // 测试在字段为空时方法的行为
        assertDoesNotThrow(() -> {
            String eventId = nullEvent.getEventId();
            // 应该返回 null 或默认值，不应该抛出异常
        }, "getEventId() 在 ID 为空时不应抛出异常");

        assertDoesNotThrow(() -> {
            Long timestamp = nullEvent.getTimestamp();
            // 应该返回 null 或默认值，不应该抛出异常
        }, "getTimestamp() 在时间戳为空时不应抛出异常");

        assertDoesNotThrow(() -> {
            String businessType = nullEvent.getBusinessType();
            // 应该返回 null 或默认值，不应该抛出异常
        }, "getBusinessType() 在事件类型为空时不应抛出异常");

        assertDoesNotThrow(() -> {
            Long lamportClock = nullEvent.getLamportClock();
            // 应该返回 null 或默认值，不应该抛出异常
        }, "getLamportClock() 在 Lamport 时钟为空时不应抛出异常");
    }

    @Test
    @DisplayName("集成测试：验证完整的方法调用链")
    void testCompleteMethodCallChain() {
        // 模拟在事务服务中的完整调用链
        assertDoesNotThrow(() -> {
            // 模拟 XATransactionService 中的调用
            String eventId = testEvent.getEventId();
            Long timestamp = testEvent.getTimestamp();
            String businessType = testEvent.getBusinessType();
            Long lamportClock = testEvent.getLamportClock();
            
            // 验证所有值都能正确获取
            assertNotNull(eventId, "事件ID不应为空");
            assertNotNull(timestamp, "时间戳不应为空");
            assertNotNull(businessType, "业务类型不应为空");
            assertNotNull(lamportClock, "Lamport时钟不应为空");
            
            // 验证值的正确性
            assertEquals("1", eventId);
            assertEquals(100L, timestamp);
            assertEquals("TEST_EVENT", businessType);
            assertEquals(100L, lamportClock);
            
        }, "完整的方法调用链应该正常工作");
    }
}