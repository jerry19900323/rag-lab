package com.example.raglab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the independently deployable RAG Lab service. */
@SpringBootApplication
public class RagLabApplication {

    /**
     * Starts the standalone RAG Lab Spring Boot service.
     *
     * @param args command-line arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(RagLabApplication.class, args);
    }
}

