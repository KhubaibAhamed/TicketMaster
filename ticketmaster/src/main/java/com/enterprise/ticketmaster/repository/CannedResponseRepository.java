package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.CannedResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CannedResponseRepository extends JpaRepository<CannedResponse, Long> {
    boolean existsByTitle(String title);
    List<CannedResponse> findAllByOrderByTitleAsc();
}