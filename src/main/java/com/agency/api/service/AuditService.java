package com.agency.api.service;

import com.agency.api.domain.*;
import com.agency.api.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditEventRepository events; private final UserNotificationRepository notifications; private final AccountRepository accounts;
    public AuditService(AuditEventRepository events,UserNotificationRepository notifications,AccountRepository accounts){this.events=events;this.notifications=notifications;this.accounts=accounts;}
    @Transactional public void record(Account actor,String action,String type,Object id,String details){events.save(new AuditEvent(actor,action,type,id==null?null:id.toString(),details));}
    @Transactional public void notify(UUID accountId,String event,String message,String targetType,UUID targetId){accounts.findById(accountId).ifPresent(a->notifications.save(new UserNotification(a,event,message,targetType,targetId)));}
    @Transactional public void notifyRole(Role role,String event,String message,String targetType,UUID targetId){accounts.findAll().stream().filter(a->a.getRole()==role).forEach(a->notifications.save(new UserNotification(a,event,message,targetType,targetId)));}
}
