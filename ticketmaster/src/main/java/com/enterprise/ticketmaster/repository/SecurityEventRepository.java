package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.SecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    @Query("SELECT e FROM SecurityEvent e " +
            "WHERE (:eventType IS NULL OR e.eventType = :eventType) " +
            "AND (:username IS NULL OR LOWER(e.username) = LOWER(:username)) " +
            "AND (:from IS NULL OR e.occurredAt >= :from) " +
            "AND (:to IS NULL OR e.occurredAt <= :to) " +
            "ORDER BY e.occurredAt DESC")
    Page<SecurityEvent> searchEvents(
            @Param("eventType") SecurityEvent.EventType eventType,
            @Param("username") String username,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            Pageable pageable);
}