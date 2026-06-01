package com.rag;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.rag.controller.api", "com.rag.service.file", "com.rag.service.review", "com.rag.service.statemachine", "com.rag.infrastructure", "com.rag.common"})
public class RagDocApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagDocApplication.class, args);
    }
}
