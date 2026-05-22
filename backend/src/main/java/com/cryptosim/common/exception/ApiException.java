package com.cryptosim.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base application exception. All custom exceptions carry an HTTP status + machine code
 * so the global handler can return a consistent {@link ApiError}.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
