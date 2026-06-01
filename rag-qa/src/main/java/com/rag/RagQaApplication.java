package com.rag;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.rag.controller.api", "com.rag.service.qa", "com.rag.service.feedback", "com.rag.infrastructure", "com.rag.common"})
public class RagQaApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagQaApplication.class, args);
    }
}
