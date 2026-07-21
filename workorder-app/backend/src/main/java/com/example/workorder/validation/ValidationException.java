package com.example.workorder.validation;

// Bad input from the client -- turns into a 400 response.
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
