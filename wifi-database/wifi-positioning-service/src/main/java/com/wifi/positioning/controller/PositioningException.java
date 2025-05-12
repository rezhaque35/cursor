package com.wifi.positioning.controller;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PositioningException extends RuntimeException {

    private final HttpStatus status;
    
    public PositioningException(String message) {
        super(message);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    
    public PositioningException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
    
    public PositioningException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    
    public PositioningException(String message, Throwable cause, HttpStatus status) {
        super(message, cause);
        this.status = status;
    }
    
    public static PositioningException badRequest(String message) {
        return new PositioningException(message, HttpStatus.BAD_REQUEST);
    }
    
    public static PositioningException notFound(String message) {
        return new PositioningException(message, HttpStatus.NOT_FOUND);
    }
    
    public static PositioningException insufficientData() {
        return new PositioningException("Insufficient data for positioning calculation", HttpStatus.BAD_REQUEST);
    }
    
    public static PositioningException algorithmFailure(String algorithm) {
        return new PositioningException("Algorithm failure: " + algorithm, HttpStatus.INTERNAL_SERVER_ERROR);
    }
} 