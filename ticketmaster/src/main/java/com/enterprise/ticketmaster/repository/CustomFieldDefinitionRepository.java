package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.CustomFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomFieldDefinitionRepository extends JpaRepository<CustomFieldDefinition, Long> {
    List<CustomFieldDefinition> findByCategoryIdOrderByFieldOrderAsc(Long categoryId);
}