package com.enterprise.ticketmaster.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(name = "escalation_contact_username")
    private String escalationContactUsername; // username of who gets notified when a ticket in this category breaches SLA

    @Column(name = "auto_bump_priority_on_breach", nullable = false)
    private boolean autoBumpPriorityOnBreach = true; // bump priority one level on breach; on by default

    // A category can have many sub-categories
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL)
    private List<SubCategory> subCategories;

    public Category() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEscalationContactUsername() { return escalationContactUsername; }
    public void setEscalationContactUsername(String escalationContactUsername) { this.escalationContactUsername = escalationContactUsername; }

    public boolean isAutoBumpPriorityOnBreach() { return autoBumpPriorityOnBreach; }
    public void setAutoBumpPriorityOnBreach(boolean autoBumpPriorityOnBreach) { this.autoBumpPriorityOnBreach = autoBumpPriorityOnBreach; }
}