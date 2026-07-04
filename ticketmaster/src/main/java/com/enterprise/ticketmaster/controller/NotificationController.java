package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.Notification;
import com.enterprise.ticketmaster.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public List<Notification> getRecentNotifications(Authentication authentication) {
        String username = authentication.getName();
        String role = isAdmin(authentication) ? "ADMIN" : null;
        return notificationRepository.findRecentForRecipient(username, role, PageRequest.of(0, 50));
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<String> markAllRead(Authentication authentication) {
        String username = authentication.getName();
        String role = isAdmin(authentication) ? "ADMIN" : null;

        List<Notification> unread = notificationRepository.findUnreadForRecipient(username, role);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok("All notifications marked as read.");
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}