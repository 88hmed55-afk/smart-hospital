package com.smarthospital.common.error;

import com.smarthospital.common.api.ApiError;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;

/**
 * Converts malformed or unreadable JSON request bodies into a 400 response.
 *
 * <p>Without this mapper the container reports a 500 for a client-side syntax
 * error, which is both misleading and noisy in production monitoring.</p>
 */
@Provider
public class JsonProcessingExceptionMapper
        implements ExceptionMapper<jakarta.json.stream.JsonParsingException> {

    @Context private UriInfo uriInfo;
    @Context private HttpHeaders headers;

    @Override
    public Response toResponse(jakarta.json.stream.JsonParsingException exception) {
        ApiError error = new ApiError();
        error.timestamp = Instant.now();
        error.status = 400;
        error.code = "MALFORMED_JSON";
        error.title = "Bad Request";
        error.detail = "The request body is not valid JSON.";
        error.path = uriInfo == null ? null : uriInfo.getRequestUri().getPath();
        Response.ResponseBuilder builder = Response.status(400).entity(error);
        if (headers != null) {
            builder.type(headers.getAcceptableMediaTypes().stream().findFirst()
                    .orElse(MediaType.APPLICATION_JSON_TYPE));
        } else {
            builder.type(MediaType.APPLICATION_JSON_TYPE);
        }
        return builder.build();
    }
}
