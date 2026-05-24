# ==================== 构建阶段 ====================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

# Install glibc compatibility for protoc-gen-grpc-java (compiled against glibc)
RUN apk add --no-cache gcompat

WORKDIR /build
COPY pom.xml .
# 缓存依赖层 (利用 Docker layer cache)
RUN mvn dependency:go-offline -B -q || true

COPY src ./src
COPY proto ./proto
RUN mvn clean package -DskipTests -B -q

# ==================== 运行阶段 ====================
FROM amazoncorretto:17-alpine

WORKDIR /app

# 创建非 root 用户
RUN addgroup -g 1000 rag && adduser -u 1000 -G rag -D rag

# 安装 wget 用于健康检查
RUN apk add --no-cache wget

# 从构建阶段复制 jar
COPY --from=builder /build/target/rag-server-*.jar app.jar

# 创建日志目录
RUN mkdir -p /var/log/rag-server && chown -R rag:rag /var/log/rag-server

USER rag

EXPOSE 8080

# JVM 参数可通过 JAVA_OPTS 环境变量覆盖
ENTRYPOINT exec java \
    ${JAVA_OPTS:--Xms512m -Xmx1024m} \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom \
    -jar app.jar
