package com.prreviewer.exception;

import org.springframework.http.HttpStatus;

public class PrReviewException extends RuntimeException {

    private final HttpStatus status;

    public PrReviewException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public PrReviewException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
