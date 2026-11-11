package com.smarthospital.common.error;

import com.smarthospital.common.api.ApiError;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;

@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {
    @Context private UriInfo uriInfo;
    @Context private HttpHeaders headers;

    @Override
    public Response toResponse(BusinessException exception) {
        ApiError error = new ApiError();
        error.timestamp = Instant.now();
        error.status = exception.getHttpStatus();
        error.code = exception.getErrorCode();
        error.title = titleFor(exception.getHttpStatus());
        error.detail = exception.getPublicMessage();
        error.path = uriInfo == null ? null : uriInfo.getRequestUri().getPath();
        return Response.status(exception.getHttpStatus()).entity(error).build();
    }

    private String titleFor(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            default -> "Request Error";
        };
    }
}
