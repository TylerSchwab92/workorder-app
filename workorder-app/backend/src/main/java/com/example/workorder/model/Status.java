package com.example.workorder.model;

import com.example.workorder.validation.ValidationException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum Status {
    OPEN,
    IN_PROGRESS,
    ON_HOLD,
    COMPLETED,
    CANCELLED;

    // Once a work order is done or cancelled, it shouldn't be reopened
    // through a normal status update.
    private static final Set<Status> TERMINAL = EnumSet.of(COMPLETED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    // Parses a status from a request and gives a useful error if it's wrong.
    public static Status fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("status is required");
        }
        try {
            return Status.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("status must be one of " + Arrays.toString(values()) + ", got '" + raw + "'");
        }
    }
}
