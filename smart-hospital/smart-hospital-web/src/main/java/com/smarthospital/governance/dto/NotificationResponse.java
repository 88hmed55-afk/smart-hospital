package com.smarthospital.governance.dto;

import java.time.Instant;
import java.util.UUID;

public class NotificationResponse {
    public UUID id;
    public String notificationType;
    public String title;
    public String body;
    public String referenceType;
    public UUID referenceId;
    public Instant createdAt;
    public Instant readAt;

    public NotificationResponse() { }
}
