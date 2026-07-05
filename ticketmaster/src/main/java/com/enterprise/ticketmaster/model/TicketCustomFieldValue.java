package com.enterprise.ticketmaster.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ticket_custom_field_values")
public class TicketCustomFieldValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "field_definition_id", nullable = false)
    private Long fieldDefinitionId;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName; // denormalized copy so old ticket values still display sensibly if a field is later renamed/deleted

    @Column(columnDefinition = "TEXT")
    private String value;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public Long getFieldDefinitionId() { return fieldDefinitionId; }
    public void setFieldDefinitionId(Long fieldDefinitionId) { this.fieldDefinitionId = fieldDefinitionId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}