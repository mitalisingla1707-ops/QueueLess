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

import queueless.db.Database;
import queueless.exception.QueueLessException;
import queueless.model.Customer;
import queueless.model.Priority;

/**
 * Customer storage. Uses JDBC (PreparedStatement + ResultSet mapping) when the database is
 * active, otherwise a synchronized LinkedHashMap (insertion order kept).
 */
public class CustomerRepository implements Repository<Customer, Integer> {

    private static final String COLS = "id, name, phone, age, priority, registered_at";

    private final Map<Integer, Customer> store = new LinkedHashMap<>();

    @Override
    public void save(Customer c) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) {
                if (store.containsKey(c.getId())) throw new QueueLessException("Duplicate id: " + c.getId());
                store.put(c.getId(), c);
            }
            return;
        }
        Database.execute(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO customers (id, name, phone, age, priority, registered_at) VALUES (?,?,?,?,?,?)")) {
                ps.setInt(1, c.getId());
                ps.setString(2, c.getName());
                ps.setString(3, c.getPhone());
                ps.setInt(4, c.getAge());
                ps.setString(5, c.getPriority().name());
                ps.setString(6, c.getRegisteredAt().toString());
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public Optional<Customer> findById(Integer id) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) { return Optional.ofNullable(store.get(id)); }
        }
        return Database.execute(con -> {
            try (PreparedStatement ps = con.prepareStatement("SELECT " + COLS + " FROM customers WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<Customer>empty();
                }
            }
        });
    }

    public Optional<Customer> findByPhone(String phone) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) {
                return store.values().stream().filter(c -> c.getPhone().equals(phone)).findFirst();
            }
        }
        return Database.execute(con -> {
            try (PreparedStatement ps = con.prepareStatement("SELECT " + COLS + " FROM customers WHERE phone = ?")) {
                ps.setString(1, phone);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<Customer>empty();
                }
            }
        });
    }

    @Override
    public List<Customer> findAll() throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) { return new ArrayList<>(store.values()); }
        }
        return Database.execute(con -> {
            List<Customer> list = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement("SELECT " + COLS + " FROM customers ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        });
    }

    @Override
    public void update(Customer c) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) {
                if (!store.containsKey(c.getId())) throw new QueueLessException("Nothing to update for id " + c.getId());
                store.put(c.getId(), c);
            }
            return;
        }
        Database.execute(con -> {
            try (PreparedStatement ps = con.prepareStatement("UPDATE customers SET phone = ? WHERE id = ?")) {
                ps.setString(1, c.getPhone());
                ps.setInt(2, c.getId());
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public boolean deleteById(Integer id) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) { return store.remove(id) != null; }
        }
        return Database.execute(con -> {
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM customers WHERE id = ?")) {
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
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM customers");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    private Customer map(ResultSet rs) throws SQLException {
        return new Customer(rs.getInt("id"), rs.getString("name"), rs.getString("phone"), rs.getInt("age"),
                Priority.valueOf(rs.getString("priority")), LocalDateTime.parse(rs.getString("registered_at")));
    }
}
