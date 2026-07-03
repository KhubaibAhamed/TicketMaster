package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.SecurityEvent;
import com.enterprise.ticketmaster.repository.SecurityEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/security-events")
public class SecurityEventController {

    private final SecurityEventRepository securityEventRepository;

    public SecurityEventController(SecurityEventRepository securityEventRepository) {
        this.securityEventRepository = securityEventRepository;
    }

    @GetMapping
    public ResponseEntity<Page<SecurityEvent>> search(
            @RequestParam(required = false) SecurityEvent.EventType eventType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        LocalDateTime fromDateTime = (from != null) ? from.atStartOfDay() : null;
        LocalDateTime toDateTime   = (to != null) ? LocalDateTime.of(to, LocalTime.MAX) : null;

        Page<SecurityEvent> results = securityEventRepository.searchEvents(
                eventType, username, fromDateTime, toDateTime,
                PageRequest.of(page, size));

        return ResponseEntity.ok(results);
    }
}