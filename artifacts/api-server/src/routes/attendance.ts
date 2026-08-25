import { Router } from "express";
import axios from "axios";
import { db } from "@workspace/db";
import { attendanceCacheTable } from "@workspace/db";
import { eq } from "drizzle-orm";
import { getCredentials } from "./profile.js";

const router = Router();

const EZYGO_BASE = "https://edu.ezygo.app";

interface SubjectAttendance {
  subjectCode: string;
  subjectName: string;
  totalClasses: number;
  attendedClasses: number;
  percentage: number;
  status: "safe" | "warning" | "danger" | "condonation";
  classesNeeded?: number;
  classesCanSkip?: number;
}

function classifyAttendance(attended: number, total: number) {
  const pct = total > 0 ? (attended / total) * 100 : 0;
  if (pct >= 75) return "safe";
  if (pct >= 60) return "warning";
  if (pct >= 60) return "danger";  // 60–74: danger
  return "condonation"; // <60 — may need condonation if ≥60%
}

function getClassesNeeded(attended: number, total: number): number {
  // How many consecutive classes must be attended to reach 75%
  // 0.75 * (total + x) = attended + x  →  x = (3*total - 4*attended) / 1
  const needed = Math.ceil((0.75 * total - attended) / 0.25);
  return Math.max(0, needed);
}

function getClassesCanSkip(attended: number, total: number): number {
  // How many classes can be missed and still remain ≥75%
  // (attended) / (total + x) >= 0.75  →  x ≤ (attended/0.75) - total
  const canSkip = Math.floor(attended / 0.75 - total);
  return Math.max(0, canSkip);
}

function processSubjects(raw: SubjectAttendance[]): SubjectAttendance[] {
  return raw.map((s) => {
    const pct = s.totalClasses > 0 ? (s.attendedClasses / s.totalClasses) * 100 : 0;
    let status: SubjectAttendance["status"] = "safe";
    if (pct < 60) status = "condonation";
    else if (pct < 75) status = "danger";
    else if (pct < 85) status = "warning";

    return {
      ...s,
      percentage: Math.round(pct * 10) / 10,
      status,
      classesNeeded: getClassesNeeded(s.attendedClasses, s.totalClasses),
      classesCanSkip: getClassesCanSkip(s.attendedClasses, s.totalClasses),
    };
  });
}

async function fetchEzygoAttendance(registerNumber: string, password: string) {
  const session = axios.create({
    baseURL: EZYGO_BASE,
    timeout: 20000,
    headers: {
      "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
      "Content-Type": "application/json",
    },
  });

  // EzyGo uses a SPA (Angular/React) — try their API endpoints
  // Try login
  const loginRes = await session.post("/api/auth/login", {
    username: registerNumber,
    password,
  });

  const token = loginRes.data?.token || loginRes.data?.access_token;
  if (!token) throw new Error("EzyGo login failed — no token returned");

  // Fetch attendance
  const attendanceRes = await session.get("/api/student/attendance", {
    headers: { Authorization: `Bearer ${token}` },
  });

  const rawSubjects = Array.isArray(attendanceRes.data)
    ? attendanceRes.data
    : attendanceRes.data?.subjects || attendanceRes.data?.data || [];
  return rawSubjects.map((s: Record<string, unknown>) => ({
    subjectCode: String(s["subjectCode"] || s["code"] || ""),
    subjectName: String(s["subjectName"] || s["name"] || ""),
    totalClasses: Number(s["totalClasses"] || s["total"] || 0),
    attendedClasses: Number(s["attendedClasses"] || s["attended"] || 0),
    percentage: 0,
    status: "safe" as const,
  }));
}

// GET /api/attendance
router.get("/attendance", async (req, res) => {
  try {
    const rows = await db.select().from(attendanceCacheTable).limit(1);
    if (rows.length === 0) {
      res.status(404).json({
        error: "not_found",
        message: "No attendance data cached. Please sync from EzyGo first.",
      });
      return;
    }
    res.json(rows[0]!.data);
  } catch (err) {
    req.log.error({ err }, "Failed to get attendance");
    res.status(500).json({ error: "internal_error", message: "Failed to get attendance" });
  }
});

// POST /api/attendance/sync
router.post("/attendance/sync", async (req, res) => {
  try {
    const creds = await getCredentials();
    if (!creds) {
      res.status(400).json({ error: "no_profile", message: "No profile configured." });
      return;
    }
    if (!creds.ezygoPassword) {
      res.status(400).json({ error: "no_credentials", message: "EzyGo credentials not configured." });
      return;
    }

    let rawSubjects: SubjectAttendance[] = [];

    try {
      rawSubjects = await fetchEzygoAttendance(creds.registerNumber, creds.ezygoPassword);
    } catch (scrapeErr) {
      req.log.warn({ scrapeErr }, "EzyGo scrape failed");
      res.status(502).json({
        error: "portal_unavailable",
        message: "EzyGo attendance could not be fetched. No sample attendance was used.",
      });
      return;
    }

    if (rawSubjects.length === 0) {
      res.status(502).json({
        error: "attendance_unreadable",
        message: "EzyGo returned no readable attendance rows. No sample attendance was used.",
      });
      return;
    }

    const subjects = processSubjects(rawSubjects);
    if (subjects.length === 0 || subjects.some((subject) =>
      !subject.subjectCode || !subject.subjectName || subject.totalClasses <= 0 ||
      subject.attendedClasses < 0 || subject.attendedClasses > subject.totalClasses
    )) {
      res.status(502).json({
        error: "attendance_unreadable",
        message: "EzyGo returned invalid attendance rows. No sample attendance was used.",
      });
      return;
    }
    const overallPct =
      subjects.length > 0
        ? Math.round(
            (subjects.reduce((a, s) => a + s.attendedClasses, 0) /
              subjects.reduce((a, s) => a + s.totalClasses, 0)) *
              1000
          ) / 10
        : 0;

    const responseData = {
      subjects,
      overallPercentage: overallPct,
      syncedAt: new Date().toISOString(),
    };

    // Upsert cache
    const existing = await db.select().from(attendanceCacheTable).limit(1);
    if (existing.length > 0) {
      await db.update(attendanceCacheTable).set({
        registerNumber: creds.registerNumber,
        data: responseData,
        syncedAt: new Date(),
      }).where(eq(attendanceCacheTable.id, "singleton"));
    } else {
      await db.insert(attendanceCacheTable).values({
        id: "singleton",
        registerNumber: creds.registerNumber,
        data: responseData,
        syncedAt: new Date(),
      });
    }

    res.json(responseData);
  } catch (err) {
    req.log.error({ err }, "Failed to sync attendance");
    res.status(500).json({ error: "internal_error", message: "Failed to sync attendance from EzyGo" });
  }
});

// GET /api/attendance/analysis
router.get("/attendance/analysis", async (req, res) => {
  try {
    const rows = await db.select().from(attendanceCacheTable).limit(1);

    let subjects: SubjectAttendance[] = [];
    if (rows.length > 0) {
      const cached = rows[0]!.data as { subjects?: SubjectAttendance[] };
      subjects = cached.subjects ?? [];
    }

    const analysisSubjects = subjects.map((s) => {
      const condonationSteps: string[] = [];

      if (s.status === "condonation") {
        condonationSteps.push(
          "1. Submit an application to the College Principal with supporting documents (medical certificate / valid reason).",
          "2. The Principal forwards the application to the University after internal approval.",
          "3. Condonation is applicable only if attendance is between 60–74% and the reason is genuine.",
          "4. Maximum condonation granted: up to 10% of total working days.",
          "5. Condonation fee must be paid at the University Cash Counter.",
          "6. Ensure the application is submitted before the examination registration deadline.",
        );
      }

      let recommendation = "";
      if (s.status === "safe") {
        recommendation = s.classesCanSkip && s.classesCanSkip > 0
          ? `You are safe. You can afford to miss up to ${s.classesCanSkip} more class(es) and stay above 75%.`
          : "You are at exactly 75%. Do not miss any more classes.";
      } else if (s.status === "warning") {
        recommendation = `Attendance is below 75%. You need to attend ${s.classesNeeded} consecutive class(es) to reach the minimum requirement.`;
      } else if (s.status === "danger") {
        recommendation = `Critical: Attendance is ${s.percentage.toFixed(1)}%. Attend all remaining ${s.classesNeeded} classes immediately.`;
      } else {
        recommendation = `Attendance is critically low at ${s.percentage.toFixed(1)}%. You are eligible to apply for condonation (requires 60–74% and valid reason). Follow the steps below.`;
      }

      return {
        subjectCode: s.subjectCode,
        subjectName: s.subjectName,
        currentPercentage: s.percentage,
        requiredPercentage: 75,
        status: s.status,
        recommendation,
        classesNeeded: s.classesNeeded ?? 0,
        condonationSteps,
      };
    });

    const overallPct =
      subjects.length > 0
        ? subjects.reduce((a, s) => a + s.attendedClasses, 0) /
          subjects.reduce((a, s) => a + s.totalClasses, 0) * 100
        : 0;

    let overallStatus: "safe" | "warning" | "danger" = "safe";
    if (overallPct < 60) overallStatus = "danger";
    else if (overallPct < 75) overallStatus = "warning";

    res.json({
      overallStatus,
      overallPercentage: Math.round(overallPct * 10) / 10,
      subjects: analysisSubjects,
      summary:
        subjects.length === 0
          ? "No attendance data. Sync from EzyGo to see your analysis."
          : `Overall attendance: ${Math.round(overallPct * 10) / 10}%. ${
              analysisSubjects.filter((s) => s.status !== "safe").length
            } subject(s) need attention.`,
    });
  } catch (err) {
    req.log.error({ err }, "Failed to get attendance analysis");
    res.status(500).json({ error: "internal_error", message: "Failed to get attendance analysis" });
  }
});

export default router;
