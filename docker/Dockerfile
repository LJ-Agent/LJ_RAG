FROM maven:3.9-eclipse-temurin-17-alpine AS builder
RUN apk add --no-cache gcompat
WORKDIR /build

# Copy parent POM and module POMs first (dependency cache)
COPY pom.xml .
COPY rag-common/pom.xml rag-common/
COPY rag-auth/pom.xml rag-auth/
COPY rag-kb/pom.xml rag-kb/
COPY rag-doc/pom.xml rag-doc/
COPY rag-qa/pom.xml rag-qa/
COPY rag-config/pom.xml rag-config/

# Download dependencies
RUN mvn dependency:go-offline -B -q || true

# Copy source code
COPY rag-common/src rag-common/src
COPY rag-auth/src rag-auth/src
COPY rag-kb/src rag-kb/src
COPY rag-doc/src rag-doc/src
COPY rag-qa/src rag-qa/src
COPY rag-config/src rag-config/src

# Build: default is rag-auth (monolith fallback)
RUN mvn clean package -pl rag-auth -am -DskipTests -B -q

FROM amazoncorretto:17-alpine
WORKDIR /app
RUN addgroup -g 1000 rag && adduser -u 1000 -G rag -D rag
RUN apk add --no-cache wget
COPY --from=builder /build/rag-auth/target/*.jar app.jar
RUN mkdir -p /app/logs && chown -R rag:rag /app/logs
USER rag
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -q -O- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT exec java -Xms256m -Xmx512m -jar app.jar
