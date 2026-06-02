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

    @Bean
    public ManagedChannel generationChannel() {
        return ManagedChannelBuilder.forAddress(generationHost, generationPort)
                .usePlaintext()
                .keepAliveWithoutCalls(true)
                .idleTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }
}
