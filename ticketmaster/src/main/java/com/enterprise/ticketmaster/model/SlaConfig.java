package com.enterprise.ticketmaster.model;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Singleton row (id is always 1) holding the SLA hour thresholds per
 * priority, plus the optional business-hours calendar used to compute
 * SLA due dates and remaining-time percentages.
 *
 * businessHoursEnabled defaults to false so existing behavior (24/7 clock)
 * is unchanged until an admin explicitly turns it on.
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

    @Column(name = "business_hours_enabled", nullable = false)
    private boolean businessHoursEnabled = false;

    @Column(name = "work_start_hour", nullable = false)
    private int workStartHour = 9; // 0-23, inclusive start

    @Column(name = "work_end_hour", nullable = false)
    private int workEndHour = 18; // 0-23, exclusive end

    // CSV of DayOfWeek names, e.g. "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"
    @Column(name = "work_days", nullable = false, length = 100)
    private String workDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getHighPriorityHours() { return highPriorityHours; }
    public void setHighPriorityHours(int highPriorityHours) { this.highPriorityHours = highPriorityHours; }

    public int getMediumPriorityHours() { return mediumPriorityHours; }
    public void setMediumPriorityHours(int mediumPriorityHours) { this.mediumPriorityHours = mediumPriorityHours; }

    public int getLowPriorityHours() { return lowPriorityHours; }
    public void setLowPriorityHours(int lowPriorityHours) { this.lowPriorityHours = lowPriorityHours; }

    public boolean isBusinessHoursEnabled() { return businessHoursEnabled; }
    public void setBusinessHoursEnabled(boolean businessHoursEnabled) { this.businessHoursEnabled = businessHoursEnabled; }

    public int getWorkStartHour() { return workStartHour; }
    public void setWorkStartHour(int workStartHour) { this.workStartHour = workStartHour; }

    public int getWorkEndHour() { return workEndHour; }
    public void setWorkEndHour(int workEndHour) { this.workEndHour = workEndHour; }

    public String getWorkDays() { return workDays; }
    public void setWorkDays(String workDays) { this.workDays = workDays; }

    public long hoursFor(Priority priority) {
        return switch (priority) {
            case HIGH -> highPriorityHours;
            case MEDIUM -> mediumPriorityHours;
            case LOW -> lowPriorityHours;
        };
    }

    /** Parses the workDays CSV into a Set of DayOfWeek. Falls back to Mon-Fri if empty/invalid. */
    public Set<DayOfWeek> getWorkDaySet() {
        if (workDays == null || workDays.isBlank()) {
            return defaultWorkDays();
        }
        Set<DayOfWeek> days = new LinkedHashSet<>();
        for (String token : workDays.split(",")) {
            String trimmed = token.trim().toUpperCase();
            if (trimmed.isEmpty()) continue;
            try {
                days.add(DayOfWeek.valueOf(trimmed));
            } catch (IllegalArgumentException ignored) {
                // skip unrecognized tokens rather than fail the whole config
            }
        }
        return days.isEmpty() ? defaultWorkDays() : days;
    }

    private Set<DayOfWeek> defaultWorkDays() {
        return Arrays.stream(new DayOfWeek[]{
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}