import { Router } from "express";
import { db } from "@workspace/db";
import {
  profilesTable,
  todosTable,
  resultsCacheTable,
  attendanceCacheTable,
  announcementsCacheTable,
  focusSettingsTable,
  type InsertProfile,
} from "@workspace/db";
import { eq } from "drizzle-orm";
import { randomUUID } from "crypto";

const router = Router();

// Simple symmetric obfuscation — not production-grade encryption,
// but keeps passwords out of plaintext in the DB.
function obfuscate(text: string): string {
  return Buffer.from(text).toString("base64");
}

function deobfuscate(encoded: string): string {
  return Buffer.from(encoded, "base64").toString("utf8");
}

// GET /api/profile
router.get("/profile", async (req, res) => {
  try {
    const rows = await db.select().from(profilesTable).limit(1);
    if (rows.length === 0) {
      res.status(404).json({ error: "not_found", message: "No profile configured yet" });
      return;
    }
    const row = rows[0]!;
    res.json({
      id: row.id,
      registerNumber: row.registerNumber,
      name: row.name,
      email: row.email,
      dateOfBirth: row.dateOfBirth,
      branch: row.branch,
      semester: row.semester,
      college: row.college,
      hasKtuCredentials: !!row.ktuPasswordEnc,
      hasEzygoCredentials: !!row.ezygoPasswordEnc,
      createdAt: row.createdAt?.toISOString(),
      updatedAt: row.updatedAt?.toISOString(),
    });
  } catch (err) {
    req.log.error({ err }, "Failed to get profile");
    res.status(500).json({ error: "internal_error", message: "Failed to get profile" });
  }
});

// POST /api/profile
router.post("/profile", async (req, res) => {
  try {
    const {
      registerNumber,
      name,
      email,
      dateOfBirth,
      branch,
      semester,
      college,
      ktuPassword,
      ezygoPassword,
    } = req.body as {
      registerNumber: string;
      name?: string;
      email?: string;
      dateOfBirth?: string;
      branch?: string;
      semester?: number;
      college?: string;
      ktuPassword?: string;
      ezygoPassword?: string;
    };

    if (!registerNumber) {
      res.status(400).json({ error: "validation_error", message: "registerNumber is required" });
      return;
    }

    const existing = await db
      .select()
      .from(profilesTable)
      .where(eq(profilesTable.registerNumber, registerNumber))
      .limit(1);

    const now = new Date();

    if (existing.length > 0) {
      const row = existing[0]!;
      await db
        .update(profilesTable)
        .set({
          name: name ?? row.name,
          email: email ?? row.email,
          dateOfBirth: dateOfBirth ?? row.dateOfBirth,
          branch: branch ?? row.branch,
          semester: semester ?? row.semester,
          college: college ?? row.college,
          ktuPasswordEnc: ktuPassword ? obfuscate(ktuPassword) : row.ktuPasswordEnc,
          ezygoPasswordEnc: ezygoPassword ? obfuscate(ezygoPassword) : row.ezygoPasswordEnc,
          updatedAt: now,
        })
        .where(eq(profilesTable.id, row.id));

      const updated = await db
        .select()
        .from(profilesTable)
        .where(eq(profilesTable.id, row.id))
        .limit(1);
      const u = updated[0]!;
      res.json({
        id: u.id,
        registerNumber: u.registerNumber,
        name: u.name,
        email: u.email,
        dateOfBirth: u.dateOfBirth,
        branch: u.branch,
        semester: u.semester,
        college: u.college,
        hasKtuCredentials: !!u.ktuPasswordEnc,
        hasEzygoCredentials: !!u.ezygoPasswordEnc,
        createdAt: u.createdAt?.toISOString(),
        updatedAt: u.updatedAt?.toISOString(),
      });
    } else {
      const id = randomUUID();
      const newProfile: InsertProfile = {
        id,
        registerNumber,
        name,
        email,
        dateOfBirth,
        branch,
        semester,
        college,
        ktuPasswordEnc: ktuPassword ? obfuscate(ktuPassword) : null,
        ezygoPasswordEnc: ezygoPassword ? obfuscate(ezygoPassword) : null,
      };
      await db.insert(profilesTable).values(newProfile);
      res.json({
        id,
        registerNumber,
        name,
        email,
        dateOfBirth,
        branch,
        semester,
        college,
        hasKtuCredentials: !!ktuPassword,
        hasEzygoCredentials: !!ezygoPassword,
        createdAt: now.toISOString(),
        updatedAt: now.toISOString(),
      });
    }
  } catch (err) {
    req.log.error({ err }, "Failed to save profile");
    res.status(500).json({ error: "internal_error", message: "Failed to save profile" });
  }
});

// DELETE /api/profile — remove the local account and every user-derived cache.
router.delete("/profile", async (req, res) => {
  try {
    await db.transaction(async (tx) => {
      await tx.delete(profilesTable);
      await tx.delete(todosTable);
      await tx.delete(resultsCacheTable);
      await tx.delete(attendanceCacheTable);
      await tx.delete(announcementsCacheTable);
      await tx.delete(focusSettingsTable);
    });
    res.status(204).send();
  } catch (err) {
    req.log.error({ err }, "Failed to clear account data");
    res.status(500).json({ error: "internal_error", message: "Failed to clear account data" });
  }
});

// Helper to get decrypted credentials — used internally by other routes
export async function getCredentials(): Promise<{
  registerNumber: string;
  ktuPassword: string | null;
  ezygoPassword: string | null;
} | null> {
  const rows = await db.select().from(profilesTable).limit(1);
  if (rows.length === 0) return null;
  const row = rows[0]!;
  return {
    registerNumber: row.registerNumber,
    ktuPassword: row.ktuPasswordEnc ? deobfuscate(row.ktuPasswordEnc) : null,
    ezygoPassword: row.ezygoPasswordEnc ? deobfuscate(row.ezygoPasswordEnc) : null,
  };
}

export default router;
