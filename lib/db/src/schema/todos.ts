import { pgTable, text, boolean, timestamp } from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";

export const todosTable = pgTable("todos", {
  id: text("id").primaryKey(),
  title: text("title").notNull(),
  description: text("description"),
  dueDate: text("due_date"),
  completed: boolean("completed").default(false).notNull(),
  priority: text("priority").notNull().default("medium"), // low | medium | high
  category: text("category").notNull().default("other"), // assignment | exam | project | personal | other
  reminderTime: text("reminder_time"),
  createdAt: timestamp("created_at").defaultNow().notNull(),
  updatedAt: timestamp("updated_at").defaultNow().notNull(),
});

export const insertTodoSchema = createInsertSchema(todosTable).omit({
  createdAt: true,
  updatedAt: true,
});
export type InsertTodo = z.infer<typeof insertTodoSchema>;
export type Todo = typeof todosTable.$inferSelect;
