package com.cryptosim.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Structured error payload returned by the API.
 * Avoids leaking raw exception messages and gives the frontend a consistent contract.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    private Instant timestamp;
    private int status;
    private String error;
    private String code;
    private String message;
    private String path;
    private List<FieldViolation> fieldErrors;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class FieldViolation {
        private String field;
        private String message;
    }
}
