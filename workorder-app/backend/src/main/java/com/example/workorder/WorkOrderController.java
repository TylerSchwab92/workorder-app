package com.example.workorder;

import com.example.workorder.model.Status;
import com.example.workorder.model.WorkOrder;
import com.example.workorder.repo.WorkOrderRepository;
import com.example.workorder.validation.ConflictException;
import com.example.workorder.validation.NotFoundException;
import com.example.workorder.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles all the /api/workorders routes:
 *
 *   POST   /api/workorders          create a work order
 *   GET    /api/workorders          list them (optional ?status=OPEN filter)
 *   GET    /api/workorders/{id}     fetch one
 *   PATCH  /api/workorders/{id}     update title/description/assignedTo/status
 */
@RestController
@RequestMapping("/api/workorders")
@CrossOrigin(origins = "*") // the front end runs on a different port
public class WorkOrderController {

    private static final int MAX_TITLE_LEN = 200;
    private static final int MAX_DESCRIPTION_LEN = 4000;
    private static final int MAX_ASSIGNED_TO_LEN = 100;

    private final WorkOrderRepository repository;

    public WorkOrderController(WorkOrderRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<WorkOrder> create(@RequestBody Map<String, Object> body) {
        String title = requireNonBlankString(body, "title", MAX_TITLE_LEN);
        String description = optionalString(body, "description", MAX_DESCRIPTION_LEN);
        String assignedTo = optionalString(body, "assignedTo", MAX_ASSIGNED_TO_LEN);

        // Catch typos like "asignedTo" instead of silently ignoring them.
        rejectUnknownFields(body, "title", "description", "assignedTo");

        WorkOrder created = repository.create(title, description, assignedTo);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String status) {
        Optional<Status> filter = (status == null) ? Optional.empty() : Optional.of(Status.fromString(status));
        List<WorkOrder> all = repository.findAll(filter);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("items", all);
        envelope.put("count", all.size());
        return envelope;
    }

    @GetMapping("/{id}")
    public WorkOrder getOne(@PathVariable long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Work order " + id + " not found"));
    }

    @PatchMapping("/{id}")
    public WorkOrder update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        WorkOrder existing = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Work order " + id + " not found"));

        rejectUnknownFields(body, "title", "description", "assignedTo", "status");

        String newTitle = body.containsKey("title")
                ? requireNonBlankString(body, "title", MAX_TITLE_LEN)
                : existing.title;
        String newDescription = body.containsKey("description")
                ? optionalString(body, "description", MAX_DESCRIPTION_LEN)
                : existing.description;
        String newAssignedTo = body.containsKey("assignedTo")
                ? optionalString(body, "assignedTo", MAX_ASSIGNED_TO_LEN)
                : existing.assignedTo;

        Status newStatus = existing.status;
        if (body.containsKey("status")) {
            Object rawStatus = body.get("status");
            if (!(rawStatus instanceof String)) {
                throw new ValidationException("status must be a string");
            }
            newStatus = Status.fromString((String) rawStatus);
            if (newStatus != existing.status && existing.status.isTerminal()) {
                throw new ConflictException(
                        "Work order " + id + " is " + existing.status + " and cannot be moved to " + newStatus);
            }
        }

        WorkOrder updated = new WorkOrder(
                existing.id, newTitle, newDescription, newStatus, newAssignedTo,
                existing.createdAt, existing.updatedAt // updatedAt gets re-stamped by the repository
        );
        return repository.save(updated);
    }

    //validation helpers

    private static String requireNonBlankString(Map<String, Object> body, String field, int maxLen) {
        Object raw = body.get(field);
        if (!(raw instanceof String) || ((String) raw).isBlank()) {
            throw new ValidationException(field + " is required and must be a non-blank string");
        }
        String value = ((String) raw).trim();
        if (value.length() > maxLen) {
            throw new ValidationException(field + " must be at most " + maxLen + " characters");
        }
        return value;
    }

    private static String optionalString(Map<String, Object> body, String field, int maxLen) {
        if (!body.containsKey(field) || body.get(field) == null) {
            return null;
        }
        Object raw = body.get(field);
        if (!(raw instanceof String)) {
            throw new ValidationException(field + " must be a string or null");
        }
        String value = (String) raw;
        if (value.length() > maxLen) {
            throw new ValidationException(field + " must be at most " + maxLen + " characters");
        }
        return value.isBlank() ? null : value;
    }

    private static void rejectUnknownFields(Map<String, Object> body, String... allowed) {
        List<String> allowedList = List.of(allowed);
        List<String> unknown = body.keySet().stream()
                .filter(k -> !allowedList.contains(k))
                .collect(Collectors.toList());
        if (!unknown.isEmpty()) {
            throw new ValidationException("Unknown field(s): " + String.join(", ", unknown));
        }
    }
}
