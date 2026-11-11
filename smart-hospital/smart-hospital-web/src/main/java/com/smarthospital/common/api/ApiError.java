package com.smarthospital.common.api;

import java.time.Instant;
import java.util.List;

public class ApiError {
    public Instant timestamp;
    public int status;
    public String code;
    public String title;
    public String detail;
    public String path;
    public List<FieldViolation> fieldErrors;

    public ApiError() {
    }

    public static class FieldViolation {
        public String field;
        public String message;

        public FieldViolation() {
        }

        public FieldViolation(String field, String message) {
            this.field = field;
            this.message = message;
        }
    }
}
