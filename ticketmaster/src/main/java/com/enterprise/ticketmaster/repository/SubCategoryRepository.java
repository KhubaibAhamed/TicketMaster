package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.SubCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubCategoryRepository extends JpaRepository<SubCategory, Long> {
    List<SubCategory> findByCategoryId(Long categoryId);
    boolean existsByNameAndCategoryId(String name, Long categoryId);
}