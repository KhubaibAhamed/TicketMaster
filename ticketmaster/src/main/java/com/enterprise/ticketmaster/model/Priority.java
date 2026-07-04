package com.enterprise.ticketmaster.model;

public enum Priority {
    LOW,
    MEDIUM,
    HIGH;

    /** Returns the next priority level up. HIGH stays HIGH (already at the ceiling). */
    public Priority bump() {
        return switch (this) {
            case LOW -> MEDIUM;
            case MEDIUM -> HIGH;
            case HIGH -> HIGH;
        };
    }
}