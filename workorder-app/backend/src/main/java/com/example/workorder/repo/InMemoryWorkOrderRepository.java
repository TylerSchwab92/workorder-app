package com.example.workorder.repo;

import com.example.workorder.model.Status;
import com.example.workorder.model.WorkOrder;
import com.example.workorder.validation.NotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// Just a HashMap under the hood. This is the default -- active unless
// workorder.storage=jdbc is set in application.properties. Nothing is
// written to disk, so a restart wipes it clean.
@Repository
@ConditionalOnProperty(name = "workorder.storage", havingValue = "memory", matchIfMissing = true)
public final class InMemoryWorkOrderRepository implements WorkOrderRepository {

    private final Map<Long, WorkOrder> store = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    // Add a few sample rows on startup so the UI has something to show
    // right away instead of an empty list.
    public InMemoryWorkOrderRepository() {
        create("Replace HVAC filter - Building 3", "Quarterly filter swap, rooftop unit 2", "jsmith");
        WorkOrder wo2 = create("Fix leaking valve - Line 4", "Reported by night shift, drips onto floor", "mrodriguez");
        save(wo2.withStatus(Status.IN_PROGRESS));
        WorkOrder wo3 = create("Calibrate scale #12", null, null);
        save(wo3.withStatus(Status.ON_HOLD));
    }

    @Override
    public synchronized WorkOrder create(String title, String description, String assignedTo) {
        long id = nextId.getAndIncrement();
        Instant now = Instant.now();
        WorkOrder wo = new WorkOrder(id, title, description, Status.OPEN, assignedTo, now, now);
        store.put(id, wo);
        return wo;
    }

    @Override
    public List<WorkOrder> findAll(Optional<Status> statusFilter) {
        List<WorkOrder> all = new ArrayList<>(store.values());
        all.sort(Comparator.comparingLong((WorkOrder w) -> w.id));
        if (statusFilter.isPresent()) {
            all.removeIf(w -> w.status != statusFilter.get());
        }
        return all;
    }

    @Override
    public Optional<WorkOrder> findById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public synchronized WorkOrder save(WorkOrder updated) {
        if (!store.containsKey(updated.id)) {
            throw new NotFoundException("Work order " + updated.id + " not found");
        }
        // Always stamp the current time, so callers can't "forget" to update it.
        WorkOrder stamped = new WorkOrder(
                updated.id, updated.title, updated.description, updated.status,
                updated.assignedTo, updated.createdAt, Instant.now()
        );
        store.put(stamped.id, stamped);
        return stamped;
    }
}
