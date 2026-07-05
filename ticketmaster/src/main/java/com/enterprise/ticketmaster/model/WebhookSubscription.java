package com.enterprise.ticketmaster.model;

import jakarta.persistence.*;

@Entity
@Table(name = "webhook_subscriptions")
public class WebhookSubscription {

    public enum Platform { SLACK, DISCORD, GENERIC }
    public enum WebhookEventType { SLA_BREACHED } // add more values here later without touching the schema

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private WebhookEventType eventType = WebhookEventType.SLA_BREACHED;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }
    public WebhookEventType getEventType() { return eventType; }
    public void setEventType(WebhookEventType eventType) { this.eventType = eventType; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}