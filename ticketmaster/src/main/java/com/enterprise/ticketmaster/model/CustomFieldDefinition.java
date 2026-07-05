package com.enterprise.ticketmaster.model;

import jakarta.persistence.*;

@Entity
@Table(name = "custom_field_definitions")
public class CustomFieldDefinition {

    public enum FieldType { TEXT, NUMBER, DATE, SELECT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false)
    private FieldType fieldType;

    // CSV of options, only used when fieldType == SELECT, e.g. "Laptop,Monitor,Keyboard"
    @Column(length = 500)
    private String options;

    @Column(nullable = false)
    private boolean required = false;

    @Column(name = "field_order", nullable = false)
    private int fieldOrder = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public FieldType getFieldType() { return fieldType; }
    public void setFieldType(FieldType fieldType) { this.fieldType = fieldType; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public int getFieldOrder() { return fieldOrder; }
    public void setFieldOrder(int fieldOrder) { this.fieldOrder = fieldOrder; }
}