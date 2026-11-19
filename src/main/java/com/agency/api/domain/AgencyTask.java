package com.agency.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agency_tasks", indexes = @Index(name = "idx_agency_tasks_active", columnList = "active"))
public class AgencyTask {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 100) private String name;
    @Column(name="skill_code",nullable=false,length=40) private String skillCode="CLEANING";
    @Column(length = 500) private String description;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    protected AgencyTask() {}
    public AgencyTask(String name, String description) { this(name,description,"CLEANING"); }
    public AgencyTask(String name,String description,String skillCode){this.name=name.trim();this.description=description;this.skillCode=skillCode;}
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSkillCode(){return skillCode;}
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public void update(String name,String description,boolean active,String skillCode){this.name=name.trim();this.description=description;this.active=active;this.skillCode=skillCode;}
}
