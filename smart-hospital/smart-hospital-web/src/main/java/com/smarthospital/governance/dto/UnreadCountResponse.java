package com.smarthospital.governance.dto;

public class UnreadCountResponse {
    public long unreadCount;
    public int updatedCount;

    public UnreadCountResponse() { }
    public UnreadCountResponse(long unreadCount, int updatedCount) {
        this.unreadCount = unreadCount;
        this.updatedCount = updatedCount;
    }
}
