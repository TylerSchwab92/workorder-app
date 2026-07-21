package com.example.workorder.repo;

import com.example.workorder.model.Status;
import com.example.workorder.model.WorkOrder;
import com.example.workorder.validation.NotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// The real repository -- talks to SQL Server over JDBC, using the
// schema in sql/schema.sql. Turned on by setting workorder.storage=jdbc
// plus the spring.datasource.* properties (see application.properties).
// Spring builds the DataSource for us from those properties.
//
// Plain JDBC on purpose instead of an ORM -- it's a handful of queries
// against one table.
@Repository
@ConditionalOnProperty(name = "workorder.storage", havingValue = "jdbc")
public final class JdbcWorkOrderRepository implements WorkOrderRepository {

    private final DataSource dataSource;

    public JdbcWorkOrderRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public WorkOrder create(String title, String description, String assignedTo) {
        String sql = "INSERT INTO wo.WorkOrders (Title, Description, Status, AssignedTo) "
                + "OUTPUT INSERTED.Id, INSERTED.Title, INSERTED.Description, INSERTED.Status, "
                + "       INSERTED.AssignedTo, INSERTED.CreatedAt, INSERTED.UpdatedAt "
                + "VALUES (?, ?, 'OPEN', ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            setNullableString(ps, 2, description);
            setNullableString(ps, 3, assignedTo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create work order", e);
        }
    }

    @Override
    public List<WorkOrder> findAll(Optional<Status> statusFilter) {
        String sql = "SELECT Id, Title, Description, Status, AssignedTo, CreatedAt, UpdatedAt "
                + "FROM wo.WorkOrders"
                + (statusFilter.isPresent() ? " WHERE Status = ?" : "")
                + " ORDER BY Id";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (statusFilter.isPresent()) {
                ps.setString(1, statusFilter.get().name());
            }
            List<WorkOrder> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list work orders", e);
        }
    }

    @Override
    public Optional<WorkOrder> findById(long id) {
        String sql = "SELECT Id, Title, Description, Status, AssignedTo, CreatedAt, UpdatedAt "
                + "FROM wo.WorkOrders WHERE Id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch work order " + id, e);
        }
    }

    @Override
    public WorkOrder save(WorkOrder updated) {
        // The UpdatedAt trigger in schema.sql sets this too, but we set
        // it here as well so the row we hand back is accurate right
        // away.
        String sql = "UPDATE wo.WorkOrders "
                + "SET Title = ?, Description = ?, Status = ?, AssignedTo = ?, UpdatedAt = SYSUTCDATETIME() "
                + "OUTPUT INSERTED.Id, INSERTED.Title, INSERTED.Description, INSERTED.Status, "
                + "       INSERTED.AssignedTo, INSERTED.CreatedAt, INSERTED.UpdatedAt "
                + "WHERE Id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, updated.title);
            setNullableString(ps, 2, updated.description);
            ps.setString(3, updated.status.name());
            setNullableString(ps, 4, updated.assignedTo);
            ps.setLong(5, updated.id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("Work order " + updated.id + " not found");
                }
                return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update work order " + updated.id, e);
        }
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NVARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    // Turns a result set row into a WorkOrder object.
    private static WorkOrder map(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("CreatedAt");
        Timestamp updated = rs.getTimestamp("UpdatedAt");
        return new WorkOrder(
                rs.getLong("Id"),
                rs.getString("Title"),
                rs.getString("Description"),
                Status.valueOf(rs.getString("Status")),
                rs.getString("AssignedTo"),
                created.toInstant(),
                updated.toInstant()
        );
    }
}
