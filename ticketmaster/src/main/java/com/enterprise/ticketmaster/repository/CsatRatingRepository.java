package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.CsatRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CsatRatingRepository extends JpaRepository<CsatRating, Long> {
    boolean existsByTicketId(Long ticketId);
    Optional<CsatRating> findByTicketId(Long ticketId);

    @Query("SELECT AVG(c.rating) FROM CsatRating c")
    Double findAverageRating();

    List<CsatRating> findTop10ByOrderBySubmittedAtDesc();
}