package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.exception.ResourceNotFoundException;
import com.enterprise.ticketmaster.model.CannedResponse;
import com.enterprise.ticketmaster.repository.CannedResponseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/canned-responses")
public class CannedResponseController {

    private final CannedResponseRepository cannedResponseRepository;

    public CannedResponseController(CannedResponseRepository cannedResponseRepository) {
        this.cannedResponseRepository = cannedResponseRepository;
    }

    // Any authenticated agent/admin can read the library — they need it to reply to tickets
    @GetMapping
    public ResponseEntity<List<CannedResponse>> getAll() {
        return ResponseEntity.ok(cannedResponseRepository.findAllByOrderByTitleAsc());
    }

    public record CannedResponseRequest(String title, String body) {}

    // Creating/editing/deleting the library is admin-only (locked down in SecurityConfig)
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CannedResponseRequest request, Authentication authentication) {
        if (request.title() == null || request.title().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Title cannot be blank.");
        }
        if (request.body() == null || request.body().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Body cannot be blank.");
        }
        if (cannedResponseRepository.existsByTitle(request.title().trim())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("A canned response with that title already exists.");
        }

        CannedResponse response = new CannedResponse();
        response.setTitle(request.title().trim());
        response.setBody(request.body());
        response.setCreatedBy(authentication != null ? authentication.getName() : "unknown");
        CannedResponse saved = cannedResponseRepository.save(response);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CannedResponseRequest request) {
        CannedResponse response = cannedResponseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Canned response not found with id: " + id));

        if (request.title() != null && !request.title().isBlank()) {
            response.setTitle(request.title().trim());
        }
        if (request.body() != null && !request.body().isBlank()) {
            response.setBody(request.body());
        }
        return ResponseEntity.ok(cannedResponseRepository.save(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!cannedResponseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Canned response not found with id: " + id);
        }
        cannedResponseRepository.deleteById(id);
        return ResponseEntity.ok("Canned response deleted successfully.");
    }
}