package com.rag;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.rag.controller.api", "com.rag.service.config", "com.rag.infrastructure", "com.rag.common"})
public class RagConfigApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagConfigApplication.class, args);
    }
}
