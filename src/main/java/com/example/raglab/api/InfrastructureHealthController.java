package com.example.raglab.api;

import com.example.raglab.application.InfrastructureHealthService;
import com.example.raglab.application.InfrastructureHealthService.InfrastructureHealth;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes MySQL and Redis readiness information. */
@RestController
@RequestMapping("/api/health/infrastructure")
public class InfrastructureHealthController {

    private final InfrastructureHealthService healthService;

    public InfrastructureHealthController(InfrastructureHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ResponseEntity<InfrastructureHealth> health() {
        InfrastructureHealth health = healthService.health();
        return ResponseEntity.status(health.isUp() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(health);
    }
}
