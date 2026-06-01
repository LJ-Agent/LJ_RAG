package com.rag.infrastructure.nacos;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Register this service with Nacos on startup and send heartbeats.
 * Uses Nacos HTTP API (no Spring Cloud dependency needed).
 */
@Slf4j
@Component
public class NacosRegistration {

    @Value("${nacos.server-addr:nacos:8848}")
    private String nacosAddr;

    @Value("${nacos.service-name:rag-backend-service}")
    private String serviceName;

    @Value("${server.port:8080}")
    private int servicePort;

    @Value("${nacos.namespace:}")
    private String namespace;

    @Value("${nacos.group:RAG_GROUP}")
    private String group;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private String instanceId;

    @PostConstruct
    public void register() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            instanceId = ip + "#" + servicePort + "#DEFAULT#" + group + "@@" + serviceName;

            // Build URL with query params (same as curl -d)
            String url = String.format(
                "http://%s/nacos/v1/ns/instance?serviceName=%s&ip=%s&port=%d&namespaceId=%s&groupName=%s&enable=true&healthy=true&metadata=%%7B%%7D",
                nacosAddr,
                URLEncoder.encode(serviceName, StandardCharsets.UTF_8),
                ip, servicePort,
                URLEncoder.encode(namespace, StandardCharsets.UTF_8),
                URLEncoder.encode(group, StandardCharsets.UTF_8)
            );

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                log.info("[Nacos] Registered: {} @ {}:{}", serviceName, ip, servicePort);

                // Send heartbeat every 5 seconds
                scheduler.scheduleAtFixedRate(() -> heartbeat(client), 5, 5, TimeUnit.SECONDS);
            } else {
                log.warn("[Nacos] Registration failed: {} {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("[Nacos] Registration error: {}", e.getMessage());
        }
    }

    private void heartbeat(HttpClient client) {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String beatUrl = String.format(
                "http://%s/nacos/v1/ns/instance/beat?serviceName=%s&ip=%s&port=%d&namespaceId=%s&groupName=%s&beat=%%7B%%7D",
                nacosAddr,
                URLEncoder.encode(serviceName, StandardCharsets.UTF_8),
                ip, servicePort,
                URLEncoder.encode(namespace, StandardCharsets.UTF_8),
                URLEncoder.encode(group, StandardCharsets.UTF_8)
            );
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(beatUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            // heartbeat failure is non-fatal
        }
    }

    @PreDestroy
    public void deregister() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String url = String.format("http://%s/nacos/v1/ns/instance", nacosAddr);
            String params = String.format(
                "serviceName=%s&ip=%s&port=%d&namespaceId=%s&groupName=%s",
                URLEncoder.encode(serviceName, StandardCharsets.UTF_8),
                ip, servicePort,
                URLEncoder.encode(namespace, StandardCharsets.UTF_8),
                URLEncoder.encode(group, StandardCharsets.UTF_8)
            );
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "?" + params))
                .DELETE().build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
        scheduler.shutdown();
    }
}
