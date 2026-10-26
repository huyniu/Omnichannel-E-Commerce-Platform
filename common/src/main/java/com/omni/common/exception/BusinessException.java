package com.omni.common.exception;

/**
 * Domain error carrying a stable machine-readable code and the HTTP status to answer with.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public BusinessException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BusinessException notFound(String what) {
        return new BusinessException("NOT_FOUND", what + " not found", 404);
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(code, message, 400);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, 409);
    }

    public static BusinessException forbidden() {
        return new BusinessException("FORBIDDEN", "You do not have permission to perform this action", 403);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException("UNAUTHORIZED", message, 401);
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
