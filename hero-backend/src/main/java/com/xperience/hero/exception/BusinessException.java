package com.xperience.hero.exception;

import org.springframework.http.HttpStatus;

/**
 * Single exception class for all expected business errors.
 * Use the static factory methods to create typed instances.
 * The GlobalExceptionHandler maps these to the JSON error envelope defined in §11a.
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    private BusinessException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getHttpStatus() { return httpStatus; }

    public static BusinessException invitationNotFound() {
        return new BusinessException("INVITATION_NOT_FOUND", "Invitation not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException eventNotFound() {
        return new BusinessException("EVENT_NOT_FOUND", "Event not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException eventLocked() {
        return new BusinessException("EVENT_LOCKED",
                "Event is past its start time and no longer accepting responses",
                HttpStatus.CONFLICT);
    }

    public static BusinessException eventClosed() {
        return new BusinessException("EVENT_CLOSED",
                "Event has been closed and is no longer accepting responses",
                HttpStatus.CONFLICT);
    }

    public static BusinessException eventCanceled() {
        return new BusinessException("EVENT_CANCELED", "Event has been canceled", HttpStatus.CONFLICT);
    }

    public static BusinessException forbidden() {
        return new BusinessException("FORBIDDEN",
                "You do not have permission to perform this action",
                HttpStatus.FORBIDDEN);
    }

    public static BusinessException unauthorized() {
        return new BusinessException("UNAUTHORIZED", "X-Host-Id header is required", HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException validationError(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }
}
