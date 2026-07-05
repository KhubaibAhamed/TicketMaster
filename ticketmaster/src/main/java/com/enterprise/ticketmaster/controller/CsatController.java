package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.CsatRating;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.CsatRatingRepository;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/csat")
public class CsatController {

    private final CsatRatingRepository csatRatingRepository;
    private final TicketRepository ticketRepository;

    public CsatController(CsatRatingRepository csatRatingRepository, TicketRepository ticketRepository) {
        this.csatRatingRepository = csatRatingRepository;
        this.ticketRepository = ticketRepository;
    }

    // Public — clicked straight from an email link, no login. Token is the only guard.
    @GetMapping(value = "/rate", produces = MediaType.TEXT_HTML_VALUE)
    public String rate(@RequestParam Long ticketId, @RequestParam String token, @RequestParam int rating) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);

        if (ticket == null || ticket.getCsatToken() == null || !ticket.getCsatToken().equals(token)) {
            return "<h2>Invalid or expired rating link.</h2>";
        }
        if (rating < 1 || rating > 5) {
            return "<h2>Invalid rating.</h2>";
        }
        if (csatRatingRepository.existsByTicketId(ticketId)) {
            return "<h2>You've already rated this ticket. Thanks!</h2>";
        }

        CsatRating csat = new CsatRating();
        csat.setTicketId(ticketId);
        csat.setRating(rating);
        csatRatingRepository.save(csat);

        return "<h2>Thanks for your feedback! ⭐".repeat(1) + " ".repeat(0) + "You rated us " + rating + "/5.</h2>";
    }

    // Admin-only summary for the Reports page
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        Double avg = csatRatingRepository.findAverageRating();
        List<CsatRating> recent = csatRatingRepository.findTop10ByOrderBySubmittedAtDesc();
        return ResponseEntity.ok(Map.of(
                "averageRating", avg != null ? Math.round(avg * 10.0) / 10.0 : null,
                "totalRatings", csatRatingRepository.count(),
                "recent", recent
        ));
    }
}