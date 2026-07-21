package com.example.workorder.validation;

// No work order with that id -- turns into a 404 response.
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
