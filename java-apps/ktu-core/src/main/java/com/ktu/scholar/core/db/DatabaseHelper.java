package com.ktu.scholar.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite database connection and schema creation.
 * Used on the desktop (JDBC). Android uses Room — see ktu-android.
 */
public class DatabaseHelper {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHelper.class);
    private static final String DB_FILE_NAME = "ktu_scholar.db";

    private static DatabaseHelper instance;
    private Connection connection;

    private DatabaseHelper(String dbPath) throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        connection = DriverManager.getConnection(url);
        connection.setAutoCommit(true);
        createTables();
        log.info("Database opened at {}", dbPath);
    }

    /** Singleton — call from the desktop main class after choosing app-data dir. */
    public static DatabaseHelper getInstance(String dbDirectory) throws SQLException {
        if (instance == null) {
            String path = dbDirectory + File.separator + DB_FILE_NAME;
            instance = new DatabaseHelper(path);
        }
        return instance;
    }

    /** Convenience: use the current working directory. */
    public static DatabaseHelper getInstance() throws SQLException {
        return getInstance(System.getProperty("user.home") + File.separator + ".ktu-scholar");
    }

    public Connection getConnection() {
        return connection;
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Profile
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS profiles (
                    id                TEXT PRIMARY KEY,
                    register_number   TEXT NOT NULL UNIQUE,
                    name              TEXT,
                    email             TEXT,
                    date_of_birth     TEXT,
                    branch            TEXT,
                    semester          INTEGER DEFAULT 1,
                    college           TEXT,
                    ezygo_id          TEXT,
                    ktu_password_enc  TEXT,
                    ezygo_password_enc TEXT,
                    created_at        TEXT NOT NULL,
                    updated_at        TEXT NOT NULL
                )
            """);
            ensureProfileColumn("ezygo_id", "TEXT");

            // To-Do list
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS todos (
                    id           TEXT PRIMARY KEY,
                    title        TEXT NOT NULL,
                    description  TEXT,
                    due_date     TEXT,
                    completed    INTEGER NOT NULL DEFAULT 0,
                    priority     TEXT NOT NULL DEFAULT 'MEDIUM',
                    category     TEXT NOT NULL DEFAULT 'OTHER',
                    reminder_time TEXT,
                    created_at   TEXT NOT NULL,
                    updated_at   TEXT NOT NULL
                )
            """);

            // Cached KTU results (JSON blob)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS results_cache (
                    id              TEXT PRIMARY KEY DEFAULT 'singleton',
                    register_number TEXT NOT NULL,
                    data_json       TEXT NOT NULL,
                    synced_at       TEXT NOT NULL
                )
            """);

            // Cached EzyGo attendance (JSON blob)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS attendance_cache (
                    id              TEXT PRIMARY KEY DEFAULT 'singleton',
                    register_number TEXT NOT NULL,
                    data_json       TEXT NOT NULL,
                    synced_at       TEXT NOT NULL
                )
            """);

            // KTU announcements
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS announcements (
                    id         TEXT PRIMARY KEY,
                    title      TEXT NOT NULL,
                    content    TEXT,
                    category   TEXT NOT NULL DEFAULT 'GENERAL',
                    date       TEXT NOT NULL,
                    link       TEXT,
                    is_new     INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL
                )
            """);

            // Focus mode settings (single row)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS focus_settings (
                    id                       TEXT PRIMARY KEY DEFAULT 'singleton',
                    enabled                  INTEGER NOT NULL DEFAULT 0,
                    cooldown_minutes         INTEGER NOT NULL DEFAULT 30,
                    blocked_apps_json        TEXT NOT NULL DEFAULT '[]',
                    blocked_websites_json    TEXT NOT NULL DEFAULT '[]',
                    session_duration_minutes INTEGER NOT NULL DEFAULT 60,
                    updated_at               TEXT NOT NULL
                )
            """);

            log.info("All tables verified/created");
        }
    }

    private void ensureProfileColumn(String columnName, String columnType) throws SQLException {
        try (java.sql.ResultSet rs = connection.getMetaData().getColumns(null, null, "profiles", columnName)) {
            if (!rs.next()) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("ALTER TABLE profiles ADD COLUMN " + columnName + " " + columnType);
                }
            }
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("Database connection closed");
            }
        } catch (SQLException e) {
            log.error("Error closing database", e);
        }
    }
}
