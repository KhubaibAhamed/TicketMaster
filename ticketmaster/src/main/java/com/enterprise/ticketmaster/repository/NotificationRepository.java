package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // A notification is visible to a user if it's addressed to them personally,
    // or broadcast to a role they hold (role is null for non-admins, which the
    // ":role IS NOT NULL" guard turns into a no-op rather than matching NULL rows).
    @Query("SELECT n FROM Notification n " +
            "WHERE n.recipientUsername = :username " +
            "   OR (:role IS NOT NULL AND n.recipientRole = :role) " +
            "ORDER BY n.createdAt DESC")
    List<Notification> findRecentForRecipient(@Param("username") String username,
                                              @Param("role") String role,
                                              Pageable pageable);

    @Query("SELECT n FROM Notification n " +
            "WHERE n.read = false " +
            "AND (n.recipientUsername = :username " +
            "     OR (:role IS NOT NULL AND n.recipientRole = :role))")
    List<Notification> findUnreadForRecipient(@Param("username") String username,
                                              @Param("role") String role);
}