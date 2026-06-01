FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY ../pom.xml ../
RUN mvn dependency:go-offline -B -q || true
COPY src ./src
RUN mvn package -DskipTests -B -q -pl rag-kb -am

FROM amazoncorretto:17-alpine
WORKDIR /app
RUN addgroup -g 1000 rag && adduser -u 1000 -G rag -D rag
COPY --from=builder /build/rag-kb/target/*.jar app.jar
USER rag
EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
