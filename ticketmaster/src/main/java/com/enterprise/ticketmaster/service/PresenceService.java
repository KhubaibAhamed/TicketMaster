package com.enterprise.ticketmaster.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory "who's currently looking at this ticket" tracker. Deliberately
 * not persisted — presence is inherently transient, and losing it on a
 * server restart is the correct behavior (nobody is "still viewing" after
 * a restart anyway).
 *
 * A viewer counts as active if their last heartbeat was within
 * ACTIVE_WINDOW_SECONDS. The frontend heartbeats every 8s, so a 25s window
 * comfortably survives 1-2 missed beats without falsely showing "gone".
 */
@Service
public class PresenceService {

    private static final long ACTIVE_WINDOW_SECONDS = 25;

    // ticketId -> (username -> lastSeen)
    private final Map<Long, Map<String, LocalDateTime>> viewers = new ConcurrentHashMap<>();

    public void heartbeat(Long ticketId, String username) {
        viewers.computeIfAbsent(ticketId, id -> new ConcurrentHashMap<>())
                .put(username, LocalDateTime.now());
    }

    public void leave(Long ticketId, String username) {
        Map<String, LocalDateTime> ticketViewers = viewers.get(ticketId);
        if (ticketViewers != null) {
            ticketViewers.remove(username);
        }
    }

    /** Returns usernames actively viewing this ticket, excluding the requesting user themselves. */
    public List<String> getOtherActiveViewers(Long ticketId, String excludeUsername) {
        Map<String, LocalDateTime> ticketViewers = viewers.get(ticketId);
        if (ticketViewers == null) return List.of();

        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(ACTIVE_WINDOW_SECONDS);
        return ticketViewers.entrySet().stream()
                .filter(e -> !e.getKey().equals(excludeUsername))
                .filter(e -> e.getValue().isAfter(cutoff))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /** Periodic sweep so the map doesn't grow unbounded with stale entries from closed tabs/crashed clients. */
    @Scheduled(fixedRate = 60_000)
    public void evictStaleEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(ACTIVE_WINDOW_SECONDS * 4);
        viewers.forEach((ticketId, ticketViewers) ->
                ticketViewers.entrySet().removeIf(e -> e.getValue().isBefore(cutoff)));
        viewers.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}