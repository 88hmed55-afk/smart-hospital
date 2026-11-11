package com.smarthospital.common.error;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class BusinessException extends RuntimeException {
    private final int httpStatus;
    private final String errorCode;
    private final String publicMessage;

    public BusinessException(int httpStatus, String errorCode, String publicMessage) {
        super(publicMessage);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.publicMessage = publicMessage;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }
    public String getPublicMessage() { return publicMessage; }

    public static BusinessException notFound(String resource) {
        return new BusinessException(404, "RESOURCE_NOT_FOUND", resource + " was not found.");
    }

    public static BusinessException forbidden(String code, String message) {
        return new BusinessException(403, code, message);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(409, code, message);
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(400, code, message);
    }

    public static BusinessException unauthorized(String code, String message) {
        return new BusinessException(401, code, message);
    }
}
