package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.SlaConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaConfigRepository extends JpaRepository<SlaConfig, Long> {
}