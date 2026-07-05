package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {
    List<WebhookSubscription> findByEnabledTrueAndEventType(WebhookSubscription.WebhookEventType eventType);
}