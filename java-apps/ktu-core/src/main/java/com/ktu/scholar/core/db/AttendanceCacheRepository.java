package com.ktu.scholar.core.db;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ktu.scholar.core.model.SubjectAttendance;

import java.sql.*;
import java.time.Instant;
import java.util.List;

public class AttendanceCacheRepository {

    private final Connection conn;
    private final Gson gson = new Gson();

    public AttendanceCacheRepository(Connection conn) {
        this.conn = conn;
    }

    public static class CachedAttendance {
        public List<SubjectAttendance> subjects;
        public double overallPercentage;
        public String syncedAt;
    }

    public CachedAttendance get() throws SQLException {
        String sql = "SELECT data_json, synced_at FROM attendance_cache LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            CachedAttendance ca = new CachedAttendance();
            ca.subjects = gson.fromJson(rs.getString("data_json"),
                    new TypeToken<List<SubjectAttendance>>() {}.getType());
            ca.syncedAt = rs.getString("synced_at");

            double total    = ca.subjects.stream().mapToInt(SubjectAttendance::getTotalClasses).sum();
            double attended = ca.subjects.stream().mapToInt(SubjectAttendance::getAttendedClasses).sum();
            ca.overallPercentage = total > 0 ? attended / total * 100 : 0;
            return ca;
        }
    }

    public void save(String registerNumber, List<SubjectAttendance> subjects) throws SQLException {
        String sql = """
            INSERT INTO attendance_cache (id, register_number, data_json, synced_at)
            VALUES ('singleton', ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              register_number=excluded.register_number,
              data_json=excluded.data_json,
              synced_at=excluded.synced_at
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, registerNumber);
            ps.setString(2, gson.toJson(subjects));
            ps.setString(3, Instant.now().toString());
            ps.executeUpdate();
        }
    }
}
