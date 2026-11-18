package com.airbnb.warroom.common;

/**
 * Carries an HTTP status with a human-readable message. Thrown anywhere in the reactive
 * pipeline and translated to a JSON {@link ApiError} by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static ApiException notFound(String what) {
        return new ApiException(404, what + " not found");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, message);
    }
}
