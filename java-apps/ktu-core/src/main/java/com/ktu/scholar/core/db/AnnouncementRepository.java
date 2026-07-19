package com.ktu.scholar.core.db;

import com.ktu.scholar.core.model.Announcement;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AnnouncementRepository {

    private final Connection conn;

    public AnnouncementRepository(Connection conn) {
        this.conn = conn;
    }

    public List<Announcement> getAll() throws SQLException {
        String sql = "SELECT * FROM announcements ORDER BY date DESC LIMIT 30";
        List<Announcement> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    /** Replaces all announcements with a freshly fetched list. */
    public void replaceAll(List<Announcement> announcements) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("DELETE FROM announcements");
        }
        String sql = """
            INSERT INTO announcements (id, title, content, category, date, link, is_new, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 1, ?)
        """;
        String now = Instant.now().toString();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Announcement a : announcements) {
                ps.setString(1, a.getId());
                ps.setString(2, a.getTitle());
                ps.setString(3, a.getContent());
                ps.setString(4, a.getCategory().name());
                ps.setString(5, a.getDate());
                ps.setString(6, a.getLink());
                ps.setString(7, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private Announcement mapRow(ResultSet rs) throws SQLException {
        Announcement a = new Announcement();
        a.setId(rs.getString("id"));
        a.setTitle(rs.getString("title"));
        a.setContent(rs.getString("content"));
        try { a.setCategory(Announcement.Category.valueOf(rs.getString("category"))); }
        catch (Exception ignored) { a.setCategory(Announcement.Category.GENERAL); }
        a.setDate(rs.getString("date"));
        a.setLink(rs.getString("link"));
        a.setNew(rs.getInt("is_new") == 1);
        return a;
    }
}
