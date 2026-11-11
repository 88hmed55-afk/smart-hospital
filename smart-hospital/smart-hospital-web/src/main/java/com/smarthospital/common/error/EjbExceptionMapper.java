package com.smarthospital.common.error;

import com.smarthospital.common.api.ApiError;
import jakarta.ejb.EJBException;
import jakarta.ejb.EJBAccessException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.sql.SQLException;
import java.time.Instant;

@Provider
public class EjbExceptionMapper implements ExceptionMapper<EJBException> {
    @Context private UriInfo uriInfo;

    @Override
    public Response toResponse(EJBException exception) {
        String sqlState = findSqlState(exception);
        int status = 500;
        String code = "BUSINESS_SERVICE_FAILURE";
        String detail = "The requested operation could not be completed.";
        if (containsAccessDenied(exception)) {
            status = 403;
            code = "ACCESS_DENIED";
            detail = "The authenticated caller is not permitted to perform this operation.";
        } else if ("23505".equals(sqlState)) {
            status = 409;
            code = "DUPLICATE_RESOURCE";
            detail = "A record with the same unique value already exists.";
        } else if ("23503".equals(sqlState)) {
            status = 409;
            code = "REFERENTIAL_CONFLICT";
            detail = "The operation conflicts with a related record.";
        } else if ("23514".equals(sqlState)) {
            status = 400;
            code = "DATA_CONSTRAINT_VIOLATION";
            detail = "One or more values violate a data rule.";
        } else if ("23P01".equals(sqlState)) {
            status = 409;
            code = "SCHEDULE_CONFLICT";
            detail = "The requested time interval conflicts with an existing booking.";
        }
        ApiError error = new ApiError();
        error.timestamp = Instant.now();
        error.status = status;
        error.code = code;
        error.title = status == 500 ? "Internal Server Error"
                : status == 403 ? "Forbidden"
                : status == 409 ? "Conflict" : "Bad Request";
        error.detail = detail;
        error.path = uriInfo == null ? null : uriInfo.getRequestUri().getPath();
        return Response.status(status).entity(error).build();
    }

    private boolean containsAccessDenied(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof EJBAccessException) return true;
            current = current.getCause();
        }
        return false;
    }

    private String findSqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sql && sql.getSQLState() != null) return sql.getSQLState();
            current = current.getCause();
        }
        return null;
    }
}
