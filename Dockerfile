# 使用OpenJDK 8作为基础镜像
FROM openjdk:8-jre-alpine

# 设置维护者信息
LABEL maintainer="DTS Team <dts@example.com>"
LABEL version="1.0.0"
LABEL description="Distributed Timestamp System"

# 设置工作目录
WORKDIR /app

# 创建非root用户
RUN addgroup -g 1000 dts && \
    adduser -D -s /bin/sh -u 1000 -G dts dts

# 安装必要的工具
RUN apk add --no-cache \
    curl \
    tzdata \
    && rm -rf /var/cache/apk/*

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 复制Maven构建的JAR文件
COPY target/distributed-timestamp-system-1.0.0.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs && \
    chown -R dts:dts /app

# 切换到非root用户
USER dts

# 设置JVM参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/"

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]