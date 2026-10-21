package com.ktu.scholar.core.db;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ktu.scholar.core.model.FocusSettings;

import java.lang.reflect.Type;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FocusSettingsRepository {

    private final Connection conn;
    private final Gson gson = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    public FocusSettingsRepository(Connection conn) {
        this.conn = conn;
    }

    public FocusSettings getSettings() throws SQLException {
        String sql = "SELECT * FROM focus_settings WHERE id = 'singleton'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                FocusSettings s = new FocusSettings();
                s.setEnabled(rs.getInt("enabled") == 1);
                s.setCooldownMinutes(rs.getInt("cooldown_minutes"));
                s.setSessionDurationMinutes(rs.getInt("session_duration_minutes"));

                String appsJson = rs.getString("blocked_apps_json");
                if (appsJson != null && !appsJson.isEmpty()) {
                    List<String> apps = gson.fromJson(appsJson, LIST_TYPE);
                    if (apps != null) s.setBlockedApps(apps);
                }

                String sitesJson = rs.getString("blocked_websites_json");
                if (sitesJson != null && !sitesJson.isEmpty()) {
                    List<String> sites = gson.fromJson(sitesJson, LIST_TYPE);
                    if (sites != null) s.setBlockedWebsites(sites);
                }

                return s;
            }
        }
        return new FocusSettings();
    }

    public void saveSettings(FocusSettings s) throws SQLException {
        String now = Instant.now().toString();
        String appsJson = gson.toJson(s.getBlockedApps() != null ? s.getBlockedApps() : new ArrayList<>());
        String sitesJson = gson.toJson(s.getBlockedWebsites() != null ? s.getBlockedWebsites() : new ArrayList<>());

        String sql = """
            INSERT INTO focus_settings (id, enabled, cooldown_minutes, blocked_apps_json, blocked_websites_json, session_duration_minutes, updated_at)
            VALUES ('singleton', ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                enabled=excluded.enabled,
                cooldown_minutes=excluded.cooldown_minutes,
                blocked_apps_json=excluded.blocked_apps_json,
                blocked_websites_json=excluded.blocked_websites_json,
                session_duration_minutes=excluded.session_duration_minutes,
                updated_at=excluded.updated_at
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, s.isEnabled() ? 1 : 0);
            ps.setInt(2, s.getCooldownMinutes());
            ps.setString(3, appsJson);
            ps.setString(4, sitesJson);
            ps.setInt(5, s.getSessionDurationMinutes());
            ps.setString(6, now);
            ps.executeUpdate();
        }
    }
}
