import { Router } from "express";
import { db } from "@workspace/db";
import { todosTable } from "@workspace/db";
import { eq, and } from "drizzle-orm";
import { randomUUID } from "crypto";

const router = Router();

// GET /api/todos?status=all|pending|completed
router.get("/todos", async (req, res) => {
  try {
    const status = (req.query["status"] as string) || "all";
    const rows = await db.select().from(todosTable).orderBy(todosTable.createdAt);

    const filtered = rows.filter((t) => {
      if (status === "pending") return !t.completed;
      if (status === "completed") return t.completed;
      return true;
    });

    const pending = rows.filter((t) => !t.completed).length;
    const completed = rows.filter((t) => t.completed).length;

    res.json({
      todos: filtered.map(mapTodo),
      total: rows.length,
      pending,
      completed,
    });
  } catch (err) {
    req.log.error({ err }, "Failed to get todos");
    res.status(500).json({ error: "internal_error", message: "Failed to get todos" });
  }
});

// POST /api/todos
router.post("/todos", async (req, res) => {
  try {
    const { title, description, dueDate, priority, category, reminderTime } =
      req.body as {
        title: string;
        description?: string;
        dueDate?: string;
        priority?: string;
        category?: string;
        reminderTime?: string;
      };

    if (!title) {
      res.status(400).json({ error: "validation_error", message: "title is required" });
      return;
    }

    const id = randomUUID();
    const now = new Date();

    await db.insert(todosTable).values({
      id,
      title,
      description,
      dueDate,
      completed: false,
      priority: priority || "medium",
      category: category || "other",
      reminderTime,
    });

    const rows = await db
      .select()
      .from(todosTable)
      .where(eq(todosTable.id, id))
      .limit(1);

    res.status(201).json(mapTodo(rows[0]!));
  } catch (err) {
    req.log.error({ err }, "Failed to create todo");
    res.status(500).json({ error: "internal_error", message: "Failed to create todo" });
  }
});

// PUT /api/todos/:id
router.put("/todos/:id", async (req, res) => {
  try {
    const { id } = req.params;
    const existing = await db
      .select()
      .from(todosTable)
      .where(eq(todosTable.id, id!))
      .limit(1);

    if (existing.length === 0) {
      res.status(404).json({ error: "not_found", message: "Todo not found" });
      return;
    }

    const { title, description, dueDate, completed, priority, category, reminderTime } =
      req.body as {
        title?: string;
        description?: string;
        dueDate?: string;
        completed?: boolean;
        priority?: string;
        category?: string;
        reminderTime?: string;
      };

    const row = existing[0]!;
    await db
      .update(todosTable)
      .set({
        title: title ?? row.title,
        description: description ?? row.description,
        dueDate: dueDate ?? row.dueDate,
        completed: completed !== undefined ? completed : row.completed,
        priority: priority ?? row.priority,
        category: category ?? row.category,
        reminderTime: reminderTime ?? row.reminderTime,
        updatedAt: new Date(),
      })
      .where(eq(todosTable.id, id!));

    const updated = await db
      .select()
      .from(todosTable)
      .where(eq(todosTable.id, id!))
      .limit(1);
    res.json(mapTodo(updated[0]!));
  } catch (err) {
    req.log.error({ err }, "Failed to update todo");
    res.status(500).json({ error: "internal_error", message: "Failed to update todo" });
  }
});

// DELETE /api/todos/:id
router.delete("/todos/:id", async (req, res) => {
  try {
    const { id } = req.params;
    const existing = await db
      .select()
      .from(todosTable)
      .where(eq(todosTable.id, id!))
      .limit(1);

    if (existing.length === 0) {
      res.status(404).json({ error: "not_found", message: "Todo not found" });
      return;
    }

    await db.delete(todosTable).where(eq(todosTable.id, id!));
    res.json({ success: true, message: "Todo deleted" });
  } catch (err) {
    req.log.error({ err }, "Failed to delete todo");
    res.status(500).json({ error: "internal_error", message: "Failed to delete todo" });
  }
});

function mapTodo(t: typeof todosTable.$inferSelect) {
  return {
    id: t.id,
    title: t.title,
    description: t.description,
    dueDate: t.dueDate,
    completed: t.completed,
    priority: t.priority,
    category: t.category,
    reminderTime: t.reminderTime,
    createdAt: t.createdAt?.toISOString(),
    updatedAt: t.updatedAt?.toISOString(),
  };
}

export default router;
