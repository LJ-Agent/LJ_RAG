package com.rag.infrastructure.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcConfig {

    @Value("${grpc.retrieval.host:localhost}")
    private String retrievalHost;

    @Value("${grpc.retrieval.port:50051}")
    private int retrievalPort;

    @Value("${grpc.generation.host:localhost}")
    private String generationHost;

    @Value("${grpc.generation.port:50052}")
    private int generationPort;

    @Bean
    public ManagedChannel retrievalChannel() {
        return ManagedChannelBuilder.forAddress(retrievalHost, retrievalPort)
                .usePlaintext()
                .keepAliveWithoutCalls(true)
                .idleTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    @Value("${grpc.que.host:localhost}")
    private String queHost;

    @Value("${grpc.que.port:50055}")
    private int quePort;

    @Bean
    public ManagedChannel retrievalChannel() {
        return ManagedChannelBuilder.forAddress(retrievalHost, retrievalPort)
                .usePlaintext()
                .keepAliveWithoutCalls(true)
                .idleTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    @Bean
    public ManagedChannel generationChannel() {
        return ManagedChannelBuilder.forAddress(generationHost, generationPort)
                .usePlaintext()
                .keepAliveWithoutCalls(true)
                .idleTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    @Bean
    public ManagedChannel queChannel() {
        return ManagedChannelBuilder.forAddress(queHost, quePort)
                .usePlaintext()
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(50 * 1024 * 1024)  // 50MB for large contexts
                .idleTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }
}
