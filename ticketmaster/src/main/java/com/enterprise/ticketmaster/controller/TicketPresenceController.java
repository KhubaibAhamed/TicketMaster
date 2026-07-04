package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.service.PresenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets/{ticketId}/presence")
public class TicketPresenceController {

    private final PresenceService presenceService;

    public TicketPresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    // Called every ~8s while the ticket detail modal is open
    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(@PathVariable Long ticketId, Authentication authentication) {
        String username = authentication.getName();
        presenceService.heartbeat(ticketId, username);
        List<String> others = presenceService.getOtherActiveViewers(ticketId, username);
        return ResponseEntity.ok(Map.of("otherViewers", others));
    }

    // Called when the ticket detail modal closes
    @DeleteMapping
    public ResponseEntity<Void> leave(@PathVariable Long ticketId, Authentication authentication) {
        presenceService.leave(ticketId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}