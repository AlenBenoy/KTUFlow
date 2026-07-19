import { pgTable, text, integer, timestamp, boolean } from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";

export const profilesTable = pgTable("profiles", {
  id: text("id").primaryKey(),
  registerNumber: text("register_number").notNull().unique(),
  name: text("name"),
  email: text("email"),
  dateOfBirth: text("date_of_birth"),
  branch: text("branch"),
  semester: integer("semester"),
  college: text("college"),
  // Stored encrypted — never returned as plaintext
  ktuPasswordEnc: text("ktu_password_enc"),
  ezygoPasswordEnc: text("ezygo_password_enc"),
  createdAt: timestamp("created_at").defaultNow().notNull(),
  updatedAt: timestamp("updated_at").defaultNow().notNull(),
});

export const insertProfileSchema = createInsertSchema(profilesTable).omit({
  createdAt: true,
  updatedAt: true,
});
export type InsertProfile = z.infer<typeof insertProfileSchema>;
export type Profile = typeof profilesTable.$inferSelect;
