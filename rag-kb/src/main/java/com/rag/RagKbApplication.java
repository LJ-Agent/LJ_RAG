package com.rag;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.rag.controller.api", "com.rag.service.knowledge", "com.rag.infrastructure", "com.rag.common"})
public class RagKbApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagKbApplication.class, args);
    }
}
