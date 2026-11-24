package queueless.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import queueless.db.Database;
import queueless.exception.QueueLessException;
import queueless.model.Priority;
import queueless.model.ServiceType;
import queueless.model.Token;
import queueless.model.TokenStatus;

/** Token storage: JDBC when the database is active, otherwise an in-memory LinkedHashMap. */
public class TokenRepository implements Repository<Token, Integer> {

    private static final String COLS =
            "id, token_number, customer_id, service_type, priority, status, issued_at, counter_id, called_at";

    private final Map<Integer, Token> store = new LinkedHashMap<>();

    @Override
    public void save(Token t) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) {
                if (store.containsKey(t.getTokenId())) throw new QueueLessException("Duplicate id: " + t.getTokenId());
                store.put(t.getTokenId(), t);
            }
            return;
        }
        Database.execute(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO tokens (" + COLS + ") VALUES (?,?,?,?,?,?,?,?,?)")) {
                ps.setInt(1, t.getTokenId());
                ps.setString(2, t.getTokenNumber());
                ps.setInt(3, t.getCustomerId());
                ps.setString(4, t.getServiceType().name());
                ps.setString(5, t.getPriority().name());
                ps.setString(6, t.getStatus().name());
                ps.setString(7, t.getIssuedAt().toString());
                if (t.getCounterId() == 0) ps.setNull(8, Types.INTEGER); else ps.setInt(8, t.getCounterId());
                if (t.getCalledAt() == null) ps.setNull(9, Types.VARCHAR); else ps.setString(9, t.getCalledAt().toString());
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public Optional<Token> findById(Integer id) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) { return Optional.ofNullable(store.get(id)); }
        }
        return Database.execute(con -> {
            try (PreparedStatement ps = con.prepareStatement("SELECT " + COLS + " FROM tokens WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<Token>empty();
                }
            }
        });
    }

    @Override
    public List<Token> findAll() throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) { return new ArrayList<>(store.values()); }
        }
        return Database.execute(con -> {
            List<Token> list = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement("SELECT " + COLS + " FROM tokens ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        });
    }

    public List<Token> findByStatus(TokenStatus status) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) {
                return store.values().stream().filter(t -> t.getStatus() == status).collect(Collectors.toList());
            }
        }
        return Database.execute(con -> {
            List<Token> list = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT " + COLS + " FROM tokens WHERE status = ? ORDER BY id")) {
                ps.setString(1, status.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(map(rs));
                }
            }
            return list;
        });
    }

    @Override
    public void update(Token t) throws QueueLessException {
        if (!Database.isActive()) {
            synchronized (store) {
                if (!store.containsKey(t.getTokenId())) {
                    throw new QueueLessException("Nothing to update for id " + t.getTokenId());
                }
                store.put(t.getTokenId(), t);
            }
            return;
        }
        Database.execute(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE tokens SET status = ?, counter_id = ?, called_at = ? WHERE id = ?")) {
                ps.setString(1, t.getStatus().name());
                if (t.getCounterId() == 0) ps.setNull(2, Types.INTEGER); else ps.setInt(2, t.getCounterId());
                if (t.getCalledAt() == null) ps.setNull(3, Types.VARCHAR); else ps.setString(3, t.getCalledAt().toString());
                ps.setInt(4, t.getTokenId());
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
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM tokens WHERE id = ?")) {
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
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM tokens");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    private Token map(ResultSet rs) throws SQLException {
        Token t = new Token(rs.getInt("id"), rs.getString("token_number"), rs.getInt("customer_id"),
                ServiceType.valueOf(rs.getString("service_type")), Priority.valueOf(rs.getString("priority")),
                LocalDateTime.parse(rs.getString("issued_at")));
        t.setStatus(TokenStatus.valueOf(rs.getString("status")));
        t.setCounterId(rs.getInt("counter_id"));            // NULL becomes 0
        String called = rs.getString("called_at");
        if (called != null) t.setCalledAt(LocalDateTime.parse(called));
        return t;
    }
}
