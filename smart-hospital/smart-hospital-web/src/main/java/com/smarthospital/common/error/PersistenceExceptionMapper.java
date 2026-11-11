package com.smarthospital.common.error;

import com.smarthospital.common.api.ApiError;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.sql.SQLException;
import java.time.Instant;

@Provider
public class PersistenceExceptionMapper implements ExceptionMapper<PersistenceException> {
    @Context private UriInfo uriInfo;

    @Override
    public Response toResponse(PersistenceException exception) {
        String sqlState = findSqlState(exception);
        int status = 500;
        String code = "PERSISTENCE_FAILURE";
        String detail = "The operation could not be completed because of a data persistence error.";

        if (sqlState != null) {
            if ("23P01".equals(sqlState)) {
                status = 409;
                code = "SCHEDULE_CONFLICT";
                detail = "The requested time interval conflicts with an existing booking.";
            } else if ("23505".equals(sqlState)) {
                status = 409;
                code = "DUPLICATE_RESOURCE";
                detail = "A record with the same unique value already exists.";
            } else if ("23503".equals(sqlState)) {
                status = 409;
                code = "REFERENTIAL_CONFLICT";
                detail = "The operation conflicts with an existing related record.";
            } else if ("23514".equals(sqlState)) {
                status = 400;
                code = "DATA_CONSTRAINT_VIOLATION";
                detail = "One or more values violate a data rule.";
            }
        }

        ApiError error = new ApiError();
        error.timestamp = Instant.now();
        error.status = status;
        error.code = code;
        error.title = status == 500 ? "Internal Server Error" : status == 409 ? "Conflict" : "Bad Request";
        error.detail = detail;
        error.path = uriInfo == null ? null : uriInfo.getRequestUri().getPath();
        return Response.status(status).entity(error).build();
    }

    private String findSqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }
}
