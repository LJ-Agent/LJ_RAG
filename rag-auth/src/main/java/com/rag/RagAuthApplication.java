package com.rag;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.rag.controller.api", "com.rag.service.user", "com.rag.infrastructure", "com.rag.common"})
public class RagAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagAuthApplication.class, args);
    }
}
