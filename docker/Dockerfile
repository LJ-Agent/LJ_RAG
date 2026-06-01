FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B -q || true
COPY src ./src
RUN mvn clean package -DskipTests -B -q

FROM amazoncorretto:17-alpine
WORKDIR /app
RUN addgroup -g 1000 rag && adduser -u 1000 -G rag -D rag
RUN apk add --no-cache wget
COPY --from=builder /build/target/*.jar app.jar
RUN mkdir -p /app/logs && chown -R rag:rag /app/logs
USER rag
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -q -O- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT exec java -Xms256m -Xmx512m -jar app.jar
