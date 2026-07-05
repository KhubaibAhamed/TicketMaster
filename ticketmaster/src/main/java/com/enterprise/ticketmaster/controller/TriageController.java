package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.service.TicketService;
import com.enterprise.ticketmaster.service.TriageAssistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets/{ticketId}/triage")
public class TriageController {

    private final TriageAssistService triageAssistService;
    private final TicketService ticketService;

    public TriageController(TriageAssistService triageAssistService, TicketService ticketService) {
        this.triageAssistService = triageAssistService;
        this.ticketService = ticketService;
    }

    @GetMapping("/duplicates")
    public ResponseEntity<List<Ticket>> duplicates(@PathVariable Long ticketId) {
        Ticket ticket = ticketService.getTicketById(ticketId);
        return ResponseEntity.ok(triageAssistService.findLikelyDuplicates(ticket));
    }
}