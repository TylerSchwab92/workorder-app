package com.example.workorder.repo;

import com.example.workorder.model.Status;
import com.example.workorder.model.WorkOrder;

import java.util.List;
import java.util.Optional;

// Storage layer. There are two implementations of this:
//   - InMemoryWorkOrderRepository, the default (no setup needed)
//   - JdbcWorkOrderRepository, which talks to real SQL Server
//     (turned on by setting workorder.storage=jdbc)
//
// The controller only ever talks to this interface, so it doesn't care
// which one Spring wires in.
public interface WorkOrderRepository {

    WorkOrder create(String title, String description, String assignedTo);

    List<WorkOrder> findAll(Optional<Status> statusFilter);

    Optional<WorkOrder> findById(long id);

    // Saves whatever WorkOrder it's given, overwriting the existing row
    // for that id, and stamps a fresh updatedAt.
    WorkOrder save(WorkOrder updated);
}
