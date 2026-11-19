package com.agency.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="incident_messages",indexes=@Index(name="idx_incident_message_created",columnList="incident_id,created_at"))
public class IncidentMessage {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="incident_id",nullable=false) private IncidentReport incident;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="author_id",nullable=false) private Account author;
    @Column(nullable=false,length=2000) private String message;
    @Column(length=80) private String attachmentContentType;
    @Column(columnDefinition="bytea") private byte[] attachment;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    protected IncidentMessage(){}
    public IncidentMessage(IncidentReport incident,Account author,String message,byte[] attachment,String contentType){this.incident=incident;this.author=author;this.message=message;this.attachment=attachment;this.attachmentContentType=contentType;}
    public UUID getId(){return id;} public IncidentReport getIncident(){return incident;} public Account getAuthor(){return author;} public String getMessage(){return message;} public byte[] getAttachment(){return attachment;} public String getAttachmentContentType(){return attachmentContentType;} public Instant getCreatedAt(){return createdAt;}
}
