package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.exception.ResourceNotFoundException;
import com.enterprise.ticketmaster.model.Category;
import com.enterprise.ticketmaster.model.CustomFieldDefinition;
import com.enterprise.ticketmaster.repository.CategoryRepository;
import com.enterprise.ticketmaster.repository.CustomFieldDefinitionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/custom-fields")
public class CustomFieldDefinitionController {

    private final CustomFieldDefinitionRepository fieldRepository;
    private final CategoryRepository categoryRepository;

    public CustomFieldDefinitionController(CustomFieldDefinitionRepository fieldRepository, CategoryRepository categoryRepository) {
        this.fieldRepository = fieldRepository;
        this.categoryRepository = categoryRepository;
    }

    // Open to any authenticated role — needed at ticket-creation time to render the form, including for USER (requesters)
    @GetMapping("/by-category/{categoryId}")
    public List<CustomFieldDefinition> getForCategory(@PathVariable Long categoryId) {
        return fieldRepository.findByCategoryIdOrderByFieldOrderAsc(categoryId);
    }

    public record FieldRequest(Long categoryId, String fieldName, String fieldType, String options, boolean required, int fieldOrder) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestBody FieldRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));

        CustomFieldDefinition.FieldType type;
        try {
            type = CustomFieldDefinition.FieldType.valueOf(request.fieldType().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("fieldType must be TEXT, NUMBER, DATE, or SELECT.");
        }
        if (type == CustomFieldDefinition.FieldType.SELECT && (request.options() == null || request.options().isBlank())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("SELECT fields need at least one option.");
        }

        CustomFieldDefinition field = new CustomFieldDefinition();
        field.setCategory(category);
        field.setFieldName(request.fieldName().trim());
        field.setFieldType(type);
        field.setOptions(request.options());
        field.setRequired(request.required());
        field.setFieldOrder(request.fieldOrder());

        return ResponseEntity.status(HttpStatus.CREATED).body(fieldRepository.save(field));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!fieldRepository.existsById(id)) {
            throw new ResourceNotFoundException("Custom field not found with id: " + id);
        }
        fieldRepository.deleteById(id);
        return ResponseEntity.ok("Custom field deleted.");
    }
}