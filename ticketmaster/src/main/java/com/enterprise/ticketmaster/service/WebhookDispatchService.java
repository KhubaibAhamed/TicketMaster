package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.WebhookSubscription;
import com.enterprise.ticketmaster.repository.WebhookSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Best-effort webhook fan-out. Failures are logged, never thrown — a broken Slack integration should never break ticket processing. */
@Service
public class WebhookDispatchService {
    private static final Logger log = LoggerFactory.getLogger(WebhookDispatchService.class);

    private final WebhookSubscriptionRepository repository;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public WebhookDispatchService(WebhookSubscriptionRepository repository) {
        this.repository = repository;
    }

    @Async
    public void dispatch(WebhookSubscription.WebhookEventType eventType, String message) {
        List<WebhookSubscription> subscriptions = repository.findByEnabledTrueAndEventType(eventType);
        for (WebhookSubscription sub : subscriptions) {
            send(sub, message);
        }
    }

    public void send(WebhookSubscription sub, String message) {
        try {
            Map<String, String> payload = switch (sub.getPlatform()) {
                case SLACK -> Map.of("text", message);
                case DISCORD -> Map.of("content", message);
                case GENERIC -> Map.of("event", sub.getEventType().name(), "message", message);
            };

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sub.getUrl()))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Webhook #{} ({}) returned {}: {}", sub.getId(), sub.getPlatform(), response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Webhook #{} ({}) delivery failed", sub.getId(), sub.getPlatform(), e);
        }
    }
}