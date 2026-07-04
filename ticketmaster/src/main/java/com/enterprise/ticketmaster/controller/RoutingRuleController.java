package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.exception.ResourceNotFoundException;
import com.enterprise.ticketmaster.model.Category;
import com.enterprise.ticketmaster.model.RoutingRule;
import com.enterprise.ticketmaster.repository.CategoryRepository;
import com.enterprise.ticketmaster.repository.RoutingRuleRepository;
import com.enterprise.ticketmaster.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routing-rules")
public class RoutingRuleController {

    private final RoutingRuleRepository routingRuleRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public RoutingRuleController(RoutingRuleRepository routingRuleRepository, CategoryRepository categoryRepository,
                                 UserRepository userRepository) {
        this.routingRuleRepository = routingRuleRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<RoutingRule> getAllRules() {
        return routingRuleRepository.findAllByOrderByRuleOrderAsc();
    }

    @PostMapping
    public ResponseEntity<?> createRule(@RequestBody RoutingRule rule) {
        String validationError = validateEligibleAgents(rule);
        if (validationError != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(validationError);
        }
        resolveCategory(rule);
        rule.setId(null); // never trust a client-supplied id on create
        return ResponseEntity.status(HttpStatus.CREATED).body(routingRuleRepository.save(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRule(@PathVariable Long id, @RequestBody RoutingRule updatedRule) {
        RoutingRule existing = routingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Routing rule not found with id: " + id));

        String validationError = validateEligibleAgents(updatedRule);
        if (validationError != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(validationError);
        }

        resolveCategory(updatedRule);
        existing.setRuleOrder(updatedRule.getRuleOrder());
        existing.setCategory(updatedRule.getCategory());
        existing.setPriority(updatedRule.getPriority());
        existing.setTargetQueue(updatedRule.getTargetQueue());
        existing.setAutoAssignEnabled(updatedRule.isAutoAssignEnabled());
        existing.setEligibleAgents(updatedRule.getEligibleAgents());

        return ResponseEntity.ok(routingRuleRepository.save(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteRule(@PathVariable Long id) {
        if (!routingRuleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Routing rule not found with id: " + id);
        }
        routingRuleRepository.deleteById(id);
        return ResponseEntity.ok("Routing rule deleted successfully.");
    }

    // Resolves a client-sent {"id": X} category reference against the DB —
    // same fix we already applied to TicketService, applied here proactively
    // so this controller never hits the same Hibernate transient-entity crash.
    private void resolveCategory(RoutingRule rule) {
        if (rule.getCategory() != null && rule.getCategory().getId() != null) {
            Category category = categoryRepository.findById(rule.getCategory().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + rule.getCategory().getId()));
            rule.setCategory(category);
        }
    }

    /** Returns an error message if auto-assign is on but the eligible-agent list is empty/invalid, else null. */
    private String validateEligibleAgents(RoutingRule rule) {
        if (!rule.isAutoAssignEnabled()) return null;

        List<String> agents = rule.getEligibleAgentList();
        if (agents.isEmpty()) {
            return "Auto-assign is enabled but no eligible agents were provided.";
        }
        for (String username : agents) {
            if (!userRepository.existsByUsername(username)) {
                return "No user found with username: " + username;
            }
        }
        return null;
    }
}