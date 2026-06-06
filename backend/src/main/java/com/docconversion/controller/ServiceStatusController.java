package com.docconversion.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceStatusController {

    @GetMapping("/")
    public Map<String, String> status() {
        return Map.of(
            "service", "doc-conversion-backend",
            "status", "UP",
            "healthUrl", "http://localhost:13800/actuator/health",
            "webUrl", "http://localhost:13801"
        );
    }
}
