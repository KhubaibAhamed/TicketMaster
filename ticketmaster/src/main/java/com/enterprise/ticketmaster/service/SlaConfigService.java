package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.SlaConfig;
import com.enterprise.ticketmaster.repository.SlaConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlaConfigService {

    private final SlaConfigRepository slaConfigRepository;

    public SlaConfigService(SlaConfigRepository slaConfigRepository) {
        this.slaConfigRepository = slaConfigRepository;
    }

    /**
     * Returns the singleton config row, creating it with the original
     * hardcoded defaults (4h/24h/72h) on first call if it doesn't exist yet.
     */
    @Transactional
    public SlaConfig getConfig() {
        return slaConfigRepository.findById(1L)
                .orElseGet(() -> slaConfigRepository.save(new SlaConfig()));
    }

    @Transactional
    public SlaConfig updateConfig(int highHours, int mediumHours, int lowHours) {
        if (highHours <= 0 || mediumHours <= 0 || lowHours <= 0) {
            throw new IllegalArgumentException("SLA hour thresholds must be positive.");
        }
        SlaConfig config = getConfig();
        config.setHighPriorityHours(highHours);
        config.setMediumPriorityHours(mediumHours);
        config.setLowPriorityHours(lowHours);
        return slaConfigRepository.save(config);
    }
}