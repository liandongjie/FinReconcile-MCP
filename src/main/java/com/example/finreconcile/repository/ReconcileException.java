package com.example.finreconcile.repository;

public final class ReconcileException extends RuntimeException {

    private final String errorCode;

    public ReconcileException(String errorCode, String message) {
        super(errorCode + ": " + message);
        this.errorCode = errorCode;
    }

    public ReconcileException(String errorCode, String message, Throwable cause) {
        super(errorCode + ": " + message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
