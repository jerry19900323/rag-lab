package com.example.raglab.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes lightweight application-level health information. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * Reports whether the application is available to serve requests.
     *
     * @return a minimal application-level health status
     */
    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}

