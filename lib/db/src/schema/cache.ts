import { pgTable, text, jsonb, timestamp } from "drizzle-orm/pg-core";

// Caches raw JSON from KTU portal & EzyGo
export const resultsCacheTable = pgTable("results_cache", {
  id: text("id").primaryKey().default("singleton"),
  registerNumber: text("register_number").notNull(),
  data: jsonb("data").notNull(),
  syncedAt: timestamp("synced_at").defaultNow().notNull(),
});

export const attendanceCacheTable = pgTable("attendance_cache", {
  id: text("id").primaryKey().default("singleton"),
  registerNumber: text("register_number").notNull(),
  data: jsonb("data").notNull(),
  syncedAt: timestamp("synced_at").defaultNow().notNull(),
});

export const announcementsCacheTable = pgTable("announcements_cache", {
  id: text("id").primaryKey(),
  title: text("title").notNull(),
  content: text("content"),
  category: text("category").notNull().default("general"),
  date: text("date").notNull(),
  link: text("link"),
  isNew: text("is_new").default("true"),
  createdAt: timestamp("created_at").defaultNow().notNull(),
});

export const focusSettingsTable = pgTable("focus_settings", {
  id: text("id").primaryKey().default("singleton"),
  enabled: text("enabled").default("false").notNull(),
  cooldownMinutes: text("cooldown_minutes").default("30").notNull(),
  blockedApps: jsonb("blocked_apps").default([]).notNull(),
  blockedWebsites: jsonb("blocked_websites").default([]).notNull(),
  sessionDurationMinutes: text("session_duration_minutes").default("60"),
  updatedAt: timestamp("updated_at").defaultNow().notNull(),
});
