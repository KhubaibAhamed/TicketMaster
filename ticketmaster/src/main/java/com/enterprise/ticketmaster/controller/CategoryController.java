package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.exception.ResourceNotFoundException;
import com.enterprise.ticketmaster.model.Category;
import com.enterprise.ticketmaster.repository.CategoryRepository;
import com.enterprise.ticketmaster.repository.RoutingRuleRepository;
import com.enterprise.ticketmaster.repository.SubCategoryRepository;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final TicketRepository ticketRepository;
    private final RoutingRuleRepository routingRuleRepository;

    public CategoryController(CategoryRepository categoryRepository,
                              SubCategoryRepository subCategoryRepository,
                              TicketRepository ticketRepository,
                              RoutingRuleRepository routingRuleRepository) {
        this.categoryRepository = categoryRepository;
        this.subCategoryRepository = subCategoryRepository;
        this.ticketRepository = ticketRepository;
        this.routingRuleRepository = routingRuleRepository;
    }

    // Lightweight shape for the dropdown — keeps the lazy subCategories list out of the response
    public static class CategoryResponse {
        public Long id;
        public String name;

        public CategoryResponse(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // Fuller shape for the admin management screen
    public static class SubCategoryResponse {
        public Long id;
        public String name;

        public SubCategoryResponse(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class CategoryDetailResponse {
        public Long id;
        public String name;
        public String description;
        public List<SubCategoryResponse> subCategories;

        public CategoryDetailResponse(Category c, List<SubCategoryResponse> subs) {
            this.id = c.getId();
            this.name = c.getName();
            this.description = c.getDescription();
            this.subCategories = subs;
        }
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        List<CategoryResponse> categories = categoryRepository.findAll().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(categories);
    }

    // Admin management view — includes description + nested sub-categories
    @GetMapping("/detailed")
    public ResponseEntity<List<CategoryDetailResponse>> getAllCategoriesDetailed() {
        List<CategoryDetailResponse> result = categoryRepository.findAll().stream()
                .map(c -> {
                    List<SubCategoryResponse> subs = subCategoryRepository.findByCategoryId(c.getId()).stream()
                            .map(sc -> new SubCategoryResponse(sc.getId(), sc.getName()))
                            .collect(Collectors.toList());
                    return new CategoryDetailResponse(c, subs);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> createCategory(@RequestBody Category category) {
        if (category.getName() == null || category.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Category name cannot be blank.");
        }
        if (categoryRepository.existsByName(category.getName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("A category with that name already exists.");
        }
        category.setId(null); // never trust a client-supplied id — this is always a new row
        Category saved = categoryRepository.save(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CategoryResponse(saved.getId(), saved.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found with id: " + id);
        }
        if (ticketRepository.existsByCategoryId(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot delete: one or more tickets are still filed under this category.");
        }
        if (routingRuleRepository.existsByCategoryId(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot delete: a routing rule still references this category. Remove or reassign that rule first.");
        }
        // Sub-categories cascade automatically (CascadeType.ALL on Category.subCategories)
        categoryRepository.deleteById(id);
        return ResponseEntity.ok("Category deleted successfully.");
    }

    public record SubCategoryRequest(String name) {}

    @PostMapping("/{categoryId}/subcategories")
    public ResponseEntity<?> addSubCategory(@PathVariable Long categoryId, @RequestBody SubCategoryRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Sub-category name cannot be blank.");
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));

        if (subCategoryRepository.existsByNameAndCategoryId(request.name(), categoryId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("This category already has a sub-category with that name.");
        }

        com.enterprise.ticketmaster.model.SubCategory sub = new com.enterprise.ticketmaster.model.SubCategory();
        sub.setName(request.name());
        sub.setCategory(category);
        com.enterprise.ticketmaster.model.SubCategory saved = subCategoryRepository.save(sub);
        return ResponseEntity.status(HttpStatus.CREATED).body(new SubCategoryResponse(saved.getId(), saved.getName()));
    }

    @DeleteMapping("/subcategories/{id}")
    public ResponseEntity<?> deleteSubCategory(@PathVariable Long id) {
        if (!subCategoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Sub-category not found with id: " + id);
        }
        if (ticketRepository.existsBySubCategoryId(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot delete: one or more tickets are still filed under this sub-category.");
        }
        subCategoryRepository.deleteById(id);
        return ResponseEntity.ok("Sub-category deleted successfully.");
    }
}