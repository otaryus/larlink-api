package com.agency.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="user_notifications",indexes=@Index(name="idx_notification_user_read",columnList="account_id,read_at"))
public class UserNotification {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="account_id",nullable=false) private Account account;
    @Column(nullable=false,length=80) private String eventType;
    @Column(nullable=false,length=240) private String message;
    @Column(length=100) private String targetType;
    private UUID targetId;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    private Instant readAt;
    protected UserNotification(){}
    public UserNotification(Account account,String type,String message,String targetType,UUID targetId){this.account=account;eventType=type;this.message=message;this.targetType=targetType;this.targetId=targetId;}
    public UUID getId(){return id;} public Account getAccount(){return account;} public String getEventType(){return eventType;} public String getMessage(){return message;} public String getTargetType(){return targetType;} public UUID getTargetId(){return targetId;} public Instant getCreatedAt(){return createdAt;} public Instant getReadAt(){return readAt;} public boolean isRead(){return readAt!=null;} public void markRead(){if(readAt==null)readAt=Instant.now();}
}
