package com.smarthospital.governance.rest;

import com.smarthospital.common.api.PageResponse;
import com.smarthospital.governance.dto.NotificationResponse;
import com.smarthospital.governance.dto.UnreadCountResponse;
import com.smarthospital.governance.service.NotificationInboxService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE", "LAB_TECHNICIAN",
        "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER"})
public class NotificationResource {
    @EJB private NotificationInboxService inbox;

    @GET
    public PageResponse<NotificationResponse> list(
            @DefaultValue("false") @QueryParam("unreadOnly") boolean unreadOnly,
            @DefaultValue("0") @QueryParam("offset") int offset,
            @DefaultValue("20") @QueryParam("limit") int limit) {
        return inbox.list(unreadOnly, offset, limit);
    }

    @GET
    @Path("/unread-count")
    public UnreadCountResponse unreadCount() {
        return inbox.unreadCount();
    }

    @POST
    @Path("/{notificationId}/read")
    public UnreadCountResponse markRead(@PathParam("notificationId") UUID notificationId) {
        return inbox.markRead(notificationId);
    }

    @POST
    @Path("/read-all")
    public UnreadCountResponse markAllRead() {
        return inbox.markAllRead();
    }
}
