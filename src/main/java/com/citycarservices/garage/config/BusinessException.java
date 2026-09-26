package com.citycarservices.garage.config;

/** Thrown for rule violations that should be shown to the user as a message. */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }
}
