package com.example.raglab;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "rag.elasticsearch.initialize-index=false",
        "spring.flyway.enabled=false"
})
class RagLabApplicationTests {

    /** Verifies that the complete Spring application context can start. */
    @Test
    void contextLoads() {
    }
}

