package com.ktu.scholar.core.db;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ktu.scholar.core.model.SemesterResult;
import com.ktu.scholar.core.service.GradeCalculatorService;

import java.sql.*;
import java.time.Instant;
import java.util.List;

public class ResultsCacheRepository {

    private final Connection conn;
    private final Gson gson = new Gson();

    public ResultsCacheRepository(Connection conn) {
        this.conn = conn;
    }

    public static class CachedResults {
        public List<SemesterResult> semesters;
        public double latestCgpa;
        public String syncedAt;
    }

    public CachedResults get() throws SQLException {
        String sql = "SELECT data_json, synced_at FROM results_cache LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            CachedResults cr = new CachedResults();
            cr.semesters = gson.fromJson(rs.getString("data_json"),
                    new TypeToken<List<SemesterResult>>() {}.getType());
            cr.syncedAt = rs.getString("synced_at");
            cr.latestCgpa = GradeCalculatorService.calculateCgpa(cr.semesters);
            return cr;
        }
    }

    public void save(String registerNumber, List<SemesterResult> semesters) throws SQLException {
        // Upsert
        String sql = """
            INSERT INTO results_cache (id, register_number, data_json, synced_at)
            VALUES ('singleton', ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              register_number=excluded.register_number,
              data_json=excluded.data_json,
              synced_at=excluded.synced_at
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, registerNumber);
            ps.setString(2, gson.toJson(semesters));
            ps.setString(3, Instant.now().toString());
            ps.executeUpdate();
        }
    }
}
