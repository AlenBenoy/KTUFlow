package com.ktu.scholar.core.db;

import com.ktu.scholar.core.model.Profile;
import com.ktu.scholar.core.util.CryptoUtil;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;

public class ProfileRepository {

    private final Connection conn;

    public ProfileRepository(Connection conn) {
        this.conn = conn;
    }

    /** Returns the first (and only) stored profile, or null if not configured. */
    public Profile getProfile() throws SQLException {
        String sql = "SELECT * FROM profiles LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return mapRow(rs);
            return null;
        }
    }

    /** Inserts or updates the single student profile. */
    public Profile saveProfile(Profile p) throws SQLException {
        Profile existing = getProfile();
        String now = Instant.now().toString();

        if (existing == null) {
            p.setId(UUID.randomUUID().toString());
            String sql = """
                INSERT INTO profiles
                (id, register_number, name, email, date_of_birth, branch, semester, college,
                 ktu_password_enc, ezygo_password_enc, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
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
                ps.setString(9, encryptIfPresent(p.getKtuPassword()));
                ps.setString(10, encryptIfPresent(p.getEzygoPassword()));
                ps.setString(11, now);
                ps.setString(12, now);
                ps.executeUpdate();
            }
        } else {
            p.setId(existing.getId());
            String sql = """
                UPDATE profiles SET
                  register_number=?, name=?, email=?, date_of_birth=?,
                  branch=?, semester=?, college=?,
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
                // Only update if a new password was provided
                ps.setString(8, p.hasKtuCredentials()
                    ? encryptIfPresent(p.getKtuPassword())
                    : existing.getKtuPassword() != null ? encryptIfPresent(existing.getKtuPassword()) : null);
                ps.setString(9, p.hasEzygoCredentials()
                    ? encryptIfPresent(p.getEzygoPassword())
                    : existing.getEzygoPassword() != null ? encryptIfPresent(existing.getEzygoPassword()) : null);
                ps.setString(10, now);
                ps.setString(11, p.getId());
                ps.executeUpdate();
            }
        }
        return getProfile();
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

        // Decrypt credentials back to plaintext for in-memory use
        String ktuEnc = rs.getString("ktu_password_enc");
        if (ktuEnc != null) p.setKtuPassword(CryptoUtil.decrypt(ktuEnc));
        String ezygoEnc = rs.getString("ezygo_password_enc");
        if (ezygoEnc != null) p.setEzygoPassword(CryptoUtil.decrypt(ezygoEnc));

        return p;
    }

    private String encryptIfPresent(String value) {
        return (value != null && !value.isEmpty()) ? CryptoUtil.encrypt(value) : null;
    }
}
