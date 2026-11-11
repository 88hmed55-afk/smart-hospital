package com.smarthospital.common.error;

import com.smarthospital.common.api.ApiError;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;

/**
 * Distinguishes client JSON syntax errors (400) from genuine server-side processing
 * faults (500) when Jersey wraps a message-body reader failure in
 * {@link ProcessingException}.
 */
@Provider
public class ProcessingExceptionMapper implements ExceptionMapper<ProcessingException> {

    @Context private UriInfo uriInfo;

    @Override
    public Response toResponse(ProcessingException exception) {
        boolean malformedJson = false;
        Throwable cause = exception.getCause();
        while (cause != null) {
            String name = cause.getClass().getName();
            if (cause instanceof jakarta.json.stream.JsonParsingException
                    || name.contains("JsonParseException")
                    || name.contains("JsonbException")) {
                malformedJson = true;
                break;
            }
            cause = cause.getCause();
        }

        ApiError error = new ApiError();
        error.timestamp = Instant.now();
        error.status = malformedJson ? 400 : 500;
        error.code = malformedJson ? "MALFORMED_JSON" : "REQUEST_PROCESSING_FAILED";
        error.title = malformedJson ? "Bad Request" : "Internal Server Error";
        error.detail = malformedJson
                ? "The request body is not valid JSON."
                : "The request could not be processed.";
        error.path = uriInfo == null ? null : uriInfo.getRequestUri().getPath();
        return Response.status(error.status).type(MediaType.APPLICATION_JSON_TYPE).entity(error).build();
    }
}
