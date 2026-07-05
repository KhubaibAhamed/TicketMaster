package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.exception.ResourceNotFoundException;
import com.enterprise.ticketmaster.model.WebhookSubscription;
import com.enterprise.ticketmaster.repository.WebhookSubscriptionRepository;
import com.enterprise.ticketmaster.service.WebhookDispatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookSubscriptionController {

    private final WebhookSubscriptionRepository repository;
    private final WebhookDispatchService dispatchService;

    public WebhookSubscriptionController(WebhookSubscriptionRepository repository, WebhookDispatchService dispatchService) {
        this.repository = repository;
        this.dispatchService = dispatchService;
    }

    @GetMapping
    public List<WebhookSubscription> getAll() {
        return repository.findAll();
    }

    public record WebhookRequest(String url, String platform) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestBody WebhookRequest request, Authentication authentication) {
        if (request.url() == null || request.url().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("URL cannot be blank.");
        }
        WebhookSubscription.Platform platform;
        try {
            platform = WebhookSubscription.Platform.valueOf(request.platform().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Platform must be SLACK, DISCORD, or GENERIC.");
        }

        WebhookSubscription sub = new WebhookSubscription();
        sub.setUrl(request.url().trim());
        sub.setPlatform(platform);
        sub.setCreatedBy(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(sub));
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable Long id) {
        WebhookSubscription sub = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found with id: " + id));
        sub.setEnabled(!sub.isEnabled());
        return ResponseEntity.ok(repository.save(sub));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<?> test(@PathVariable Long id) {
        WebhookSubscription sub = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found with id: " + id));
        dispatchService.send(sub, "✅ Test message from Ticketmaster Enterprise. Your webhook is wired up correctly.");
        return ResponseEntity.ok("Test message sent.");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Webhook not found with id: " + id);
        }
        repository.deleteById(id);
        return ResponseEntity.ok("Webhook deleted.");
    }
}