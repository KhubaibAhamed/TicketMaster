package com.enterprise.ticketmaster.model;

import jakarta.persistence.*;

/**
 * Singleton row (id is always 1) holding the SLA hour thresholds per
 * priority. Replaces the values that used to be hardcoded in
 * SlaManagementService. Defaults match the original hardcoded values
 * (4h / 24h / 72h) so behavior is unchanged until an admin edits them.
 */
@Entity
@Table(name = "sla_config")
public class SlaConfig {

    @Id
    private Long id = 1L;

    @Column(name = "high_priority_hours", nullable = false)
    private int highPriorityHours = 4;

    @Column(name = "medium_priority_hours", nullable = false)
    private int mediumPriorityHours = 24;

    @Column(name = "low_priority_hours", nullable = false)
    private int lowPriorityHours = 72;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getHighPriorityHours() { return highPriorityHours; }
    public void setHighPriorityHours(int highPriorityHours) { this.highPriorityHours = highPriorityHours; }

    public int getMediumPriorityHours() { return mediumPriorityHours; }
    public void setMediumPriorityHours(int mediumPriorityHours) { this.mediumPriorityHours = mediumPriorityHours; }

    public int getLowPriorityHours() { return lowPriorityHours; }
    public void setLowPriorityHours(int lowPriorityHours) { this.lowPriorityHours = lowPriorityHours; }

    public long hoursFor(Priority priority) {
        return switch (priority) {
            case HIGH -> highPriorityHours;
            case MEDIUM -> mediumPriorityHours;
            case LOW -> lowPriorityHours;
        };
    }
}