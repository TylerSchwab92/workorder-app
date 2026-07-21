package com.example.workorder.validation;

// The request is valid JSON but breaks a business rule, like trying
// to reopen a work order that's already completed. Turns into a 409.
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
