package com.ktu.scholar.core.db;

import com.ktu.scholar.core.model.Todo;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TodoRepository {

    private final Connection conn;

    public TodoRepository(Connection conn) {
        this.conn = conn;
    }

    public List<Todo> getAll() throws SQLException {
        String sql = "SELECT * FROM todos ORDER BY created_at DESC";
        List<Todo> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        }
        return result;
    }

    public List<Todo> getPending() throws SQLException {
        String sql = "SELECT * FROM todos WHERE completed=0 ORDER BY due_date ASC, created_at DESC";
        List<Todo> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        }
        return result;
    }

    public Todo insert(Todo todo) throws SQLException {
        todo.setId(UUID.randomUUID().toString());
        String now = Instant.now().toString();
        todo.setCreatedAt(now);
        todo.setUpdatedAt(now);

        String sql = """
            INSERT INTO todos (id, title, description, due_date, completed, priority, category,
                               reminder_time, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, todo.getId());
            ps.setString(2, todo.getTitle());
            ps.setString(3, todo.getDescription());
            ps.setString(4, todo.getDueDate());
            ps.setInt(5, todo.isCompleted() ? 1 : 0);
            ps.setString(6, todo.getPriority().name());
            ps.setString(7, todo.getCategory().name());
            ps.setString(8, todo.getReminderTime());
            ps.setString(9, now);
            ps.setString(10, now);
            ps.executeUpdate();
        }
        return todo;
    }

    public void update(Todo todo) throws SQLException {
        todo.setUpdatedAt(Instant.now().toString());
        String sql = """
            UPDATE todos SET title=?, description=?, due_date=?, completed=?,
                priority=?, category=?, reminder_time=?, updated_at=?
            WHERE id=?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, todo.getTitle());
            ps.setString(2, todo.getDescription());
            ps.setString(3, todo.getDueDate());
            ps.setInt(4, todo.isCompleted() ? 1 : 0);
            ps.setString(5, todo.getPriority().name());
            ps.setString(6, todo.getCategory().name());
            ps.setString(7, todo.getReminderTime());
            ps.setString(8, todo.getUpdatedAt());
            ps.setString(9, todo.getId());
            ps.executeUpdate();
        }
    }

    public void delete(String id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM todos WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    private Todo mapRow(ResultSet rs) throws SQLException {
        Todo t = new Todo();
        t.setId(rs.getString("id"));
        t.setTitle(rs.getString("title"));
        t.setDescription(rs.getString("description"));
        t.setDueDate(rs.getString("due_date"));
        t.setCompleted(rs.getInt("completed") == 1);
        try { t.setPriority(Todo.Priority.valueOf(rs.getString("priority"))); }
        catch (Exception ignored) { t.setPriority(Todo.Priority.MEDIUM); }
        try { t.setCategory(Todo.Category.valueOf(rs.getString("category"))); }
        catch (Exception ignored) { t.setCategory(Todo.Category.OTHER); }
        t.setReminderTime(rs.getString("reminder_time"));
        t.setCreatedAt(rs.getString("created_at"));
        t.setUpdatedAt(rs.getString("updated_at"));
        return t;
    }
}
