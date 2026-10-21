import { Router } from "express";
import { db } from "@workspace/db";
import { focusSettingsTable } from "@workspace/db";
import { eq } from "drizzle-orm";

const router = Router();

const DEFAULT_BLOCKED_APPS = [
  "YouTube",
  "Instagram",
  "Facebook",
  "Snapchat",
  "Netflix",
  "JioHotstar",
  "ZEE5",
  "SonyLIV",
];
const DEFAULT_BLOCKED_WEBSITES = [
  "youtube.com",
  "instagram.com",
  "facebook.com",
  "netflix.com",
  "snapchat.com",
  "jiohotstar.com",
  "zee5.com",
  "sonyliv.com",
];

// GET /api/focus
router.get("/focus", async (req, res) => {
  try {
    const rows = await db.select().from(focusSettingsTable).limit(1);
    if (rows.length === 0) {
      // Return defaults
      res.json({
        enabled: false,
        cooldownMinutes: 30,
        blockedApps: DEFAULT_BLOCKED_APPS,
        blockedWebsites: DEFAULT_BLOCKED_WEBSITES,
        sessionDurationMinutes: 60,
      });
      return;
    }
    const row = rows[0]!;
    res.json({
      enabled: row.enabled === "true",
      cooldownMinutes: parseInt(row.cooldownMinutes, 10),
      blockedApps: Array.isArray(row.blockedApps) ? row.blockedApps : [],
      blockedWebsites: Array.isArray(row.blockedWebsites) ? row.blockedWebsites : [],
      sessionDurationMinutes: row.sessionDurationMinutes
        ? parseInt(row.sessionDurationMinutes, 10)
        : 60,
      updatedAt: row.updatedAt?.toISOString(),
    });
  } catch (err) {
    req.log.error({ err }, "Failed to get focus settings");
    res.status(500).json({ error: "internal_error", message: "Failed to get focus settings" });
  }
});

// PUT /api/focus
router.put("/focus", async (req, res) => {
  try {
    const { enabled, cooldownMinutes, blockedApps, blockedWebsites, sessionDurationMinutes } =
      req.body as {
        enabled?: boolean;
        cooldownMinutes?: number;
        blockedApps?: string[];
        blockedWebsites?: string[];
        sessionDurationMinutes?: number;
      };

    const existing = await db.select().from(focusSettingsTable).limit(1);
    const now = new Date();

    if (existing.length > 0) {
      const row = existing[0]!;
      await db
        .update(focusSettingsTable)
        .set({
          enabled: enabled !== undefined ? String(enabled) : row.enabled,
          cooldownMinutes:
            cooldownMinutes !== undefined ? String(cooldownMinutes) : row.cooldownMinutes,
          blockedApps: blockedApps ?? (row.blockedApps as string[]),
          blockedWebsites: blockedWebsites ?? (row.blockedWebsites as string[]),
          sessionDurationMinutes:
            sessionDurationMinutes !== undefined
              ? String(sessionDurationMinutes)
              : row.sessionDurationMinutes,
          updatedAt: now,
        })
        .where(eq(focusSettingsTable.id, "singleton"));
    } else {
      await db.insert(focusSettingsTable).values({
        id: "singleton",
        enabled: enabled !== undefined ? String(enabled) : "false",
        cooldownMinutes: cooldownMinutes !== undefined ? String(cooldownMinutes) : "30",
        blockedApps: blockedApps ?? DEFAULT_BLOCKED_APPS,
        blockedWebsites: blockedWebsites ?? DEFAULT_BLOCKED_WEBSITES,
        sessionDurationMinutes:
          sessionDurationMinutes !== undefined ? String(sessionDurationMinutes) : "60",
        updatedAt: now,
      });
    }

    const rows = await db.select().from(focusSettingsTable).limit(1);
    const row = rows[0]!;
    res.json({
      enabled: row.enabled === "true",
      cooldownMinutes: parseInt(row.cooldownMinutes, 10),
      blockedApps: Array.isArray(row.blockedApps) ? row.blockedApps : [],
      blockedWebsites: Array.isArray(row.blockedWebsites) ? row.blockedWebsites : [],
      sessionDurationMinutes: row.sessionDurationMinutes
        ? parseInt(row.sessionDurationMinutes, 10)
        : 60,
      updatedAt: row.updatedAt?.toISOString(),
    });
  } catch (err) {
    req.log.error({ err }, "Failed to update focus settings");
    res.status(500).json({ error: "internal_error", message: "Failed to update focus settings" });
  }
});

export default router;
