package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.SlaConfig;
import com.enterprise.ticketmaster.repository.SlaConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;

@Service
public class SlaConfigService {

    private final SlaConfigRepository slaConfigRepository;

    public SlaConfigService(SlaConfigRepository slaConfigRepository) {
        this.slaConfigRepository = slaConfigRepository;
    }

    /**
     * Returns the singleton config row, creating it with the original
     * hardcoded defaults (4h/24h/72h, business hours off) on first call
     * if it doesn't exist yet.
     */
    @Transactional
    public SlaConfig getConfig() {
        return slaConfigRepository.findById(1L)
                .orElseGet(() -> slaConfigRepository.save(new SlaConfig()));
    }

    @Transactional
    public SlaConfig updateConfig(int highHours, int mediumHours, int lowHours,
                                  boolean businessHoursEnabled, int workStartHour, int workEndHour, String workDays) {
        if (highHours <= 0 || mediumHours <= 0 || lowHours <= 0) {
            throw new IllegalArgumentException("SLA hour thresholds must be positive.");
        }
        if (businessHoursEnabled) {
            if (workStartHour < 0 || workStartHour > 23 || workEndHour < 0 || workEndHour > 23) {
                throw new IllegalArgumentException("Work hours must be between 0 and 23.");
            }
            if (workStartHour >= workEndHour) {
                throw new IllegalArgumentException("Work start hour must be before work end hour.");
            }
            if (workDays == null || parseAndValidateDays(workDays).isEmpty()) {
                throw new IllegalArgumentException("At least one work day must be selected.");
            }
        }

        SlaConfig config = getConfig();
        config.setHighPriorityHours(highHours);
        config.setMediumPriorityHours(mediumHours);
        config.setLowPriorityHours(lowHours);
        config.setBusinessHoursEnabled(businessHoursEnabled);
        config.setWorkStartHour(workStartHour);
        config.setWorkEndHour(workEndHour);
        config.setWorkDays(workDays != null ? workDays : config.getWorkDays());
        return slaConfigRepository.save(config);
    }

    private java.util.List<DayOfWeek> parseAndValidateDays(String csv) {
        java.util.List<DayOfWeek> result = new java.util.ArrayList<>();
        for (String token : csv.split(",")) {
            String trimmed = token.trim().toUpperCase();
            if (trimmed.isEmpty()) continue;
            try {
                result.add(DayOfWeek.valueOf(trimmed));
            } catch (IllegalArgumentException ignored) { /* skip bad token */ }
        }
        return result;
    }
}