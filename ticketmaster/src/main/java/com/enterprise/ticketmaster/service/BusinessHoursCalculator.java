package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.SlaConfig;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Computes SLA due dates and elapsed/remaining time while respecting an
 * admin-configured business-hours calendar (work days + work start/end hour).
 * If SlaConfig.businessHoursEnabled is false, every method behaves exactly
 * like plain 24/7 calendar-time math (original behavior preserved).
 */
@Service
public class BusinessHoursCalculator {

    private static final int MAX_DAY_ITERATIONS = 400; // safety cap, ~13 months

    /** Adds `hoursToAdd` business hours to `start`, returning the resulting due date. */
    public LocalDateTime addBusinessHours(LocalDateTime start, long hoursToAdd, SlaConfig config) {
        if (!config.isBusinessHoursEnabled()) {
            return start.plusHours(hoursToAdd);
        }

        long minutesBudget = hoursToAdd * 60L;
        Set<DayOfWeek> workDays = config.getWorkDaySet();
        int startHour = config.getWorkStartHour();
        int endHour = config.getWorkEndHour();

        LocalDateTime cursor = snapToNextBusinessMoment(start, workDays, startHour, endHour);

        for (int i = 0; i < MAX_DAY_ITERATIONS && minutesBudget > 0; i++) {
            LocalDateTime dayEnd = cursor.toLocalDate().atTime(endHour, 0);
            long minutesLeftToday = Duration.between(cursor, dayEnd).toMinutes();

            if (minutesBudget <= minutesLeftToday) {
                return cursor.plusMinutes(minutesBudget);
            }

            minutesBudget -= minutesLeftToday;
            // jump to next business day's start
            cursor = snapToNextBusinessMoment(dayEnd.plusDays(1).toLocalDate().atTime(startHour, 0), workDays, startHour, endHour);
        }
        return cursor; // safety fallback, should not normally be reached
    }

    /**
     * Business minutes elapsed between two timestamps (used for SLA
     * warning/critical percent-remaining checks). Falls back to plain
     * calendar minutes when business hours are disabled.
     */
    public long businessMinutesBetween(LocalDateTime from, LocalDateTime to, SlaConfig config) {
        if (!config.isBusinessHoursEnabled()) {
            return Duration.between(from, to).toMinutes();
        }
        if (!to.isAfter(from)) return 0;

        Set<DayOfWeek> workDays = config.getWorkDaySet();
        int startHour = config.getWorkStartHour();
        int endHour = config.getWorkEndHour();

        LocalDateTime cursor = snapToNextBusinessMoment(from, workDays, startHour, endHour);
        long minutes = 0;

        for (int i = 0; i < MAX_DAY_ITERATIONS && cursor.isBefore(to); i++) {
            LocalDateTime dayEnd = cursor.toLocalDate().atTime(endHour, 0);
            LocalDateTime segmentEnd = dayEnd.isBefore(to) ? dayEnd : to;

            if (segmentEnd.isAfter(cursor)) {
                minutes += Duration.between(cursor, segmentEnd).toMinutes();
            }

            cursor = snapToNextBusinessMoment(dayEnd.plusDays(1).toLocalDate().atTime(startHour, 0), workDays, startHour, endHour);
        }
        return minutes;
    }

    /** Moves a timestamp forward to the nearest point that falls within business hours/days. */
    private LocalDateTime snapToNextBusinessMoment(LocalDateTime point, Set<DayOfWeek> workDays, int startHour, int endHour) {
        LocalDateTime cursor = point;
        for (int i = 0; i < MAX_DAY_ITERATIONS; i++) {
            boolean isWorkDay = workDays.contains(cursor.getDayOfWeek());
            if (!isWorkDay) {
                cursor = cursor.plusDays(1).toLocalDate().atTime(startHour, 0);
                continue;
            }
            if (cursor.getHour() < startHour) {
                cursor = cursor.toLocalDate().atTime(startHour, 0);
                return cursor;
            }
            if (cursor.getHour() >= endHour) {
                cursor = cursor.plusDays(1).toLocalDate().atTime(startHour, 0);
                continue;
            }
            return cursor; // already inside a business window
        }
        return cursor;
    }
}