package com.smarthospital.common.error;

import com.smarthospital.common.api.ApiError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Collectors;

@Provider
public class ConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {
    @Context private UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        ApiError error = new ApiError();
        error.timestamp = Instant.now();
        error.status = 400;
        error.code = "VALIDATION_FAILED";
        error.title = "Validation Failed";
        error.detail = "One or more request fields are invalid.";
        error.path = uriInfo == null ? null : uriInfo.getRequestUri().getPath();
        error.fieldErrors = exception.getConstraintViolations().stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(this::toFieldViolation)
                .collect(Collectors.toList());
        return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
    }

    private ApiError.FieldViolation toFieldViolation(ConstraintViolation<?> violation) {
        return new ApiError.FieldViolation(violation.getPropertyPath().toString(), violation.getMessage());
    }
}
