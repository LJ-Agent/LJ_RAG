FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -g 1000 rag && adduser -u 1000 -G rag -D rag
USER rag

COPY target/rag-server-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Xms512m", "-Xmx1024m", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
