package com.example.raglab.api;

import com.example.raglab.application.ElasticsearchHealthService;
import com.example.raglab.application.ElasticsearchHealthService.ElasticsearchHealth;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes Elasticsearch readiness information. */
@RestController
@RequestMapping("/api/health/elasticsearch")
public class ElasticsearchHealthController {

    private final ElasticsearchHealthService healthService;

    public ElasticsearchHealthController(ElasticsearchHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ResponseEntity<ElasticsearchHealth> health() {
        ElasticsearchHealth health = healthService.health();
        HttpStatus status = health.isUp() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(health);
    }
}
