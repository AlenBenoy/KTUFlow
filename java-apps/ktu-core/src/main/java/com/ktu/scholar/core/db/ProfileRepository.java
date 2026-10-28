package com.ktu.scholar.core.db;

import com.ktu.scholar.core.model.Profile;
import com.ktu.scholar.core.util.CryptoUtil;

import java.sql.*;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;

public class ProfileRepository {

    private static final CopyOnWriteArrayList<Runnable> PROFILE_CHANGE_LISTENERS =
            new CopyOnWriteArrayList<>();

    private final Connection conn;

    public ProfileRepository(Connection conn) {
        this.conn = conn;
    }

    /** Returns the first (and only) stored profile, or null if not configured. */
    public Profile getProfile() throws SQLException {
        synchronized (conn) {
            String sql = "SELECT * FROM profiles LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
                return null;
            }
        }
    }

    /** Inserts or updates the single student profile. */
    public Profile saveProfile(Profile p) throws SQLException {
        Profile saved;
        synchronized (conn) {
            saved = saveProfileLocked(p);
        }
        notifyProfileChanged();
        return saved;
    }

    private Profile saveProfileLocked(Profile p) throws SQLException {
        Profile existing = getProfile();
        String now = Instant.now().toString();

        if (existing == null) {
            p.setId(UUID.randomUUID().toString());
            String sql = """
                INSERT INTO profiles
                (id, register_number, name, email, date_of_birth, branch, semester, college,
                 ezygo_id, ktu_password_enc, ezygo_password_enc, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, p.getId());
                ps.setString(2, p.getRegisterNumber());
                ps.setString(3, p.getName());
                ps.setString(4, p.getEmail());
                ps.setString(5, p.getDateOfBirth());
                ps.setString(6, p.getBranch());
                ps.setInt(7, p.getSemester());
                ps.setString(8, p.getCollege());
                ps.setString(9, normalizeOptionalText(p.getEzygoId()));
                ps.setString(10, encryptIfPresent(p.getKtuPassword()));
                ps.setString(11, encryptIfPresent(p.getEzygoPassword()));
                ps.setString(12, now);
                ps.setString(13, now);
                ps.executeUpdate();
            }
        } else {
            p.setId(existing.getId());
            String sql = """
                UPDATE profiles SET
                  register_number=?, name=?, email=?, date_of_birth=?,
                  branch=?, semester=?, college=?, ezygo_id=?,
                  ktu_password_enc=?, ezygo_password_enc=?, updated_at=?
                WHERE id=?
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, p.getRegisterNumber());
                ps.setString(2, p.getName());
                ps.setString(3, p.getEmail());
                ps.setString(4, p.getDateOfBirth());
                ps.setString(5, p.getBranch());
                ps.setInt(6, p.getSemester());
                ps.setString(7, p.getCollege());
                ps.setString(8, normalizeOptionalText(p.getEzygoId()));
                // Only update if a new password was provided
                ps.setString(9, p.hasKtuCredentials()
                    ? encryptIfPresent(p.getKtuPassword())
                    : existing.getKtuPassword() != null ? encryptIfPresent(existing.getKtuPassword()) : null);
                ps.setString(10, p.hasEzygoCredentials()
                    ? encryptIfPresent(p.getEzygoPassword())
                    : existing.getEzygoPassword() != null ? encryptIfPresent(existing.getEzygoPassword()) : null);
                ps.setString(11, now);
                ps.setString(12, p.getId());
                ps.executeUpdate();
            }
        }
        return getProfile();
    }

    /** Updates only the profile contact fields, preserving all other profile data. */
    public boolean updateContactInfo(String ezygoId, boolean updateEzygoId,
                                     String email, boolean updateEmail) throws SQLException {
        if (!updateEzygoId && !updateEmail) return false;
        int updated;
        synchronized (conn) {
            StringBuilder sql = new StringBuilder("UPDATE profiles SET ");
            if (updateEzygoId) sql.append("ezygo_id=?, ");
            if (updateEmail) sql.append("email=?, ");
            sql.append("updated_at=? WHERE id=(SELECT id FROM profiles LIMIT 1)");
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int parameter = 1;
                if (updateEzygoId) ps.setString(parameter++, normalizeOptionalText(ezygoId));
                if (updateEmail) ps.setString(parameter++, normalizeOptionalText(email));
                ps.setString(parameter, Instant.now().toString());
                updated = ps.executeUpdate();
            }
        }
        if (updated > 0) notifyProfileChanged();
        return updated > 0;
    }

    public static void addProfileChangeListener(Runnable listener) {
        PROFILE_CHANGE_LISTENERS.add(listener);
    }

    public static void removeProfileChangeListener(Runnable listener) {
        PROFILE_CHANGE_LISTENERS.remove(listener);
    }

    private void notifyProfileChanged() {
        for (Runnable listener : PROFILE_CHANGE_LISTENERS) {
            listener.run();
        }
    }

    /** Deletes profile and associated cached data on logout. */
    public void deleteProfile() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM profiles");
            stmt.executeUpdate("DELETE FROM results_cache");
            stmt.executeUpdate("DELETE FROM attendance_cache");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Profile mapRow(ResultSet rs) throws SQLException {
        Profile p = new Profile();
        p.setId(rs.getString("id"));
        p.setRegisterNumber(rs.getString("register_number"));
        p.setName(rs.getString("name"));
        p.setEmail(rs.getString("email"));
        p.setDateOfBirth(rs.getString("date_of_birth"));
        p.setBranch(rs.getString("branch"));
        p.setSemester(rs.getInt("semester"));
        p.setCollege(rs.getString("college"));
        p.setEzygoId(rs.getString("ezygo_id"));

        // Decrypt credentials back to plaintext for in-memory use
        String ktuEnc = rs.getString("ktu_password_enc");
        if (ktuEnc != null) p.setKtuPassword(CryptoUtil.decrypt(ktuEnc));
        String ezygoEnc = rs.getString("ezygo_password_enc");
        if (ezygoEnc != null) p.setEzygoPassword(CryptoUtil.decrypt(ezygoEnc));

        return p;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String encryptIfPresent(String value) {
        return (value != null && !value.isEmpty()) ? CryptoUtil.encrypt(value) : null;
    }
}
