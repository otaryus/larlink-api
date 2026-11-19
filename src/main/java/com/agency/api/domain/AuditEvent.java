package com.agency.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="audit_events",indexes={@Index(name="idx_audit_created",columnList="created_at"),@Index(name="idx_audit_entity",columnList="entity_type,entity_id")})
public class AuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="actor_id") private Account actor;
    @Column(nullable=false,length=80) private String action;
    @Column(name="entity_type",nullable=false,length=80) private String entityType;
    @Column(name="entity_id",length=80) private String entityId;
    @Column(columnDefinition="text") private String details;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    protected AuditEvent(){}
    public AuditEvent(Account actor,String action,String type,String entityId,String details){this.actor=actor;this.action=action;entityType=type;this.entityId=entityId;this.details=details;}
    public UUID getId(){return id;} public Account getActor(){return actor;} public String getAction(){return action;} public String getEntityType(){return entityType;} public String getEntityId(){return entityId;} public String getDetails(){return details;} public Instant getCreatedAt(){return createdAt;}
}
