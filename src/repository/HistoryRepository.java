package queueless.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import queueless.db.Database;
import queueless.exception.QueueLessException;
import queueless.model.ServiceRecord;
import queueless.model.ServiceType;

/** Service-history storage (append-only): JDBC when the database is active, otherwise in-memory. */
public class HistoryRepository implements Repository<ServiceRecord, Integer> {

    private static final String COLS = "id, token_number, customer_id, customer_name, service_type, "
            + "counter_name, issued_at, started_at, completed_at";

    private final Map<Integer, ServiceRecord> store = new LinkedHashMap<>();

    @Override
    public void save(ServiceRecord r) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) {
                if (store.containsKey(r.getRecordId())) throw new QueueLessException("Duplicate id: " + r.getRecordId());
                store.put(r.getRecordId(), r);
            }
            return;
        }
        Database.execute(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO service_history (" + COLS + ") VALUES (?,?,?,?,?,?,?,?,?)")) {
                ps.setInt(1, r.getRecordId());
                ps.setString(2, r.getTokenNumber());
                ps.setInt(3, r.getCustomerId());
                ps.setString(4, r.getCustomerName());
                ps.setString(5, r.getServiceType().name());
                ps.setString(6, r.getCounterName());
                ps.setString(7, r.getIssuedAt().toString());
                ps.setString(8, r.getStartedAt().toString());
                ps.setString(9, r.getCompletedAt().toString());
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public Optional<ServiceRecord> findById(Integer id) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) { return Optional.ofNullable(store.get(id)); }
        }
        return Database.execute(con -> {
            try (PreparedStatement ps = con.prepareStatement("SELECT " + COLS + " FROM service_history WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<ServiceRecord>empty();
                }
            }
        });
    }

    @Override
    public List<ServiceRecord> findAll() throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) { return new ArrayList<>(store.values()); }
        }
        return query("SELECT " + COLS + " FROM service_history ORDER BY id", null);
    }

    public List<ServiceRecord> findByCustomerId(int customerId) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) {
                return store.values().stream().filter(r -> r.getCustomerId() == customerId)
                        .collect(Collectors.toList());
            }
        }
        return query("SELECT " + COLS + " FROM service_history WHERE customer_id = ? ORDER BY id", customerId);
    }

    @Override
    public void update(ServiceRecord entity) throws QueueLessException {
        throw new QueueLessException("Service history is append-only and cannot be updated.");
    }

    @Override
    public boolean deleteById(Integer id) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) { return store.remove(id) != null; }
        }
        return Database.execute(con -> {
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM service_history WHERE id = ?")) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public int count() throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) { return store.size(); }
        }
        return Database.execute(con -> {
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM service_history");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    private List<ServiceRecord> query(String sql, Integer param) throws QueueLessException {
        return Database.execute(con -> {
            List<ServiceRecord> list = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                if (param != null) ps.setInt(1, param);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(map(rs));
                }
            }
            return list;
        });
    }

    private ServiceRecord map(ResultSet rs) throws SQLException {
        return new ServiceRecord(rs.getInt("id"), rs.getString("token_number"), rs.getInt("customer_id"),
                rs.getString("customer_name"), ServiceType.valueOf(rs.getString("service_type")),
                rs.getString("counter_name"), LocalDateTime.parse(rs.getString("issued_at")),
                LocalDateTime.parse(rs.getString("started_at")), LocalDateTime.parse(rs.getString("completed_at")));
    }
}
