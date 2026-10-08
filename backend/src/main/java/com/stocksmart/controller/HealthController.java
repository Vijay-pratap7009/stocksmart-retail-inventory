package com.stocksmart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Public liveness probe (Day 1)")
public class HealthController {

    @GetMapping
    @Operation(summary = "Public health check, no authentication required")
    public Map<String, String> health() {
        return Map.of("status", "UP", "application", "StockSmart");
    }
}
