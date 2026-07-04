package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.SlaConfig;
import com.enterprise.ticketmaster.service.SlaConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sla-config")
public class SlaConfigController {

    private final SlaConfigService slaConfigService;

    public SlaConfigController(SlaConfigService slaConfigService) {
        this.slaConfigService = slaConfigService;
    }

    @GetMapping
    public ResponseEntity<SlaConfig> getConfig() {
        return ResponseEntity.ok(slaConfigService.getConfig());
    }

    public record SlaConfigRequest(
            int highPriorityHours, int mediumPriorityHours, int lowPriorityHours,
            boolean businessHoursEnabled, int workStartHour, int workEndHour, String workDays) {}

    @PutMapping
    public ResponseEntity<?> updateConfig(@RequestBody SlaConfigRequest request) {
        try {
            SlaConfig updated = slaConfigService.updateConfig(
                    request.highPriorityHours(), request.mediumPriorityHours(), request.lowPriorityHours(),
                    request.businessHoursEnabled(), request.workStartHour(), request.workEndHour(), request.workDays());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}