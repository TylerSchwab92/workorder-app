package com.example.workorder.model;

import java.time.Instant;

// A work order. Fields match the columns in sql/schema.sql.
//
// Immutable on purpose -- instead of setters, the repository builds a
// new WorkOrder whenever something changes. Harder to accidentally
// mutate a shared object from two places that way.
public final class WorkOrder {

    public final long id;
    public final String title;
    public final String description; // can be null
    public final Status status;
    public final String assignedTo; // can be null
    public final Instant createdAt;
    public final Instant updatedAt;

    public WorkOrder(long id, String title, String description, Status status,
                      String assignedTo, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.assignedTo = assignedTo;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Shortcut for the most common update: just changing the status.
    public WorkOrder withStatus(Status newStatus) {
        return new WorkOrder(id, title, description, newStatus, assignedTo, createdAt, updatedAt);
    }
}
