FROM maven:3.9-eclipse-temurin-17-alpine AS builder
RUN apk add --no-cache gcompat
WORKDIR /build
COPY pom.xml .
COPY rag-common/pom.xml rag-common/
COPY rag-auth/pom.xml rag-auth/
COPY rag-kb/pom.xml rag-kb/
COPY rag-doc/pom.xml rag-doc/
COPY rag-qa/pom.xml rag-qa/
COPY rag-config/pom.xml rag-config/
COPY rag-monolith/pom.xml rag-monolith/
RUN mvn install -N -q
RUN mvn dependency:go-offline -B -q || true
COPY rag-common/src rag-common/src
COPY rag-auth/src rag-auth/src
COPY rag-kb/src rag-kb/src
COPY rag-doc/src rag-doc/src
COPY rag-qa/src rag-qa/src
COPY rag-config/src rag-config/src
COPY rag-monolith/src rag-monolith/src
COPY proto proto/
RUN mvn package -pl rag-monolith -am -DskipTests -B -q

FROM amazoncorretto:17-alpine
WORKDIR /app
RUN addgroup -g 1000 rag && adduser -u 1000 -G rag -D rag
RUN apk add --no-cache wget
COPY --from=builder /build/rag-monolith/target/*.jar app.jar
RUN mkdir -p /app/logs /var/log/rag-server && chown -R rag:rag /app/logs /var/log/rag-server
USER rag
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -q -O- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT exec java -Xms512m -Xmx1024m -jar app.jar
