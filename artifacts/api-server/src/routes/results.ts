import { Router } from "express";
import axios from "axios";
import * as cheerio from "cheerio";
import { db } from "@workspace/db";
import { resultsCacheTable } from "@workspace/db";
import { eq } from "drizzle-orm";
import { getCredentials } from "./profile.js";

const router = Router();

const KTU_BASE = "https://app.ktu.edu.in";
const LOGIN_URL = `${KTU_BASE}/eu/bms/userLogin.htm`;
const RESULTS_URL = `${KTU_BASE}/eu/stu/viewResults.htm`;

// Grade → gradePoint map (KTU 2024)
const GRADE_POINTS: Record<string, number> = {
  S: 10, "A+": 9, A: 8.5, "B+": 8, B: 7, "C+": 6, C: 5, D: 4, F: 0, FE: 0, I: 0, W: 0,
};

async function fetchKtuResults(registerNumber: string, password: string) {
  const session = axios.create({
    baseURL: KTU_BASE,
    timeout: 20000,
    headers: {
      "User-Agent":
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36",
      "Content-Type": "application/x-www-form-urlencoded",
    },
    withCredentials: true,
    maxRedirects: 5,
  });

  // Step 1: GET login page to capture CSRF / hidden fields
  const loginPage = await session.get("/eu/bms/userLogin.htm");
  const $login = cheerio.load(loginPage.data as string);
  const csrfToken = $login('input[name="_csrf"]').val() as string | undefined;
  const formAction = $login("form").attr("action") || "/eu/bms/userLogin.htm";

  const cookies = (loginPage.headers["set-cookie"] || []) as string[];
  const cookieHeader = cookies.map((c) => c.split(";")[0]).join("; ");

  // Step 2: POST login
  const loginParams = new URLSearchParams({
    j_username: registerNumber,
    j_password: password,
    ...(csrfToken ? { _csrf: csrfToken } : {}),
  });

  const loginRes = await session.post(
    formAction.startsWith("http") ? formAction : `${KTU_BASE}${formAction}`,
    loginParams.toString(),
    { headers: { Cookie: cookieHeader } }
  );

  const newCookies = [
    ...cookies,
    ...((loginRes.headers["set-cookie"] || []) as string[]),
  ];
  const authedCookies = newCookies.map((c) => c.split(";")[0]).join("; ");

  // Step 3: Fetch results page
  const resultsPage = await session.get(RESULTS_URL, {
    headers: { Cookie: authedCookies },
  });

  const parsed = parseResultsHtml(resultsPage.data as string);
  if (parsed.length === 0) {
    throw new Error("KTU results page contained no readable rows");
  }
  return parsed;
}

function parseResultsHtml(html: string) {
  const $ = cheerio.load(html);
  const semesters: Array<{
    semester: number;
    sgpa?: number;
    cgpa?: number;
    totalCredits?: number;
    earnedCredits?: number;
    subjects: Array<{
      subjectCode: string;
      subjectName: string;
      credits?: number;
      grade: string;
      gradePoint?: number;
      internalMarks?: number;
      externalMarks?: number;
      totalMarks?: number;
      result: string;
    }>;
  }> = [];

  // Try to extract semester sections
  $(".semester-result, .result-section, table").each((_i, table) => {
    const rows = $(table).find("tr");
    if (rows.length < 2) return;

    const semesterSubjects: typeof semesters[0]["subjects"] = [];
    const sectionText = $(table).parent().text() + " " + $(table).prevAll().slice(0, 3).text();
    const semesterMatch = sectionText.match(/semester\s*[-:]?\s*(\d+)/i);
    let semesterNum = semesterMatch ? Number(semesterMatch[1]) : semesters.length + 1;
    let sgpa: number | undefined;

    rows.each((_j, row) => {
      const cells = $(row).find("td");
      if (cells.length >= 3) {
        const code = $(cells[0]).text().trim();
        const name = $(cells[1]).text().trim();
        const grade = $(cells[cells.length - 1]).text().trim().toUpperCase();

        const creditCell = cells.toArray()
          .slice(2, -1)
          .map((cell) => Number($(cell).text().trim()))
          .find((value) => Number.isFinite(value) && value > 0 && value <= 10);

        if (code.length > 2 && name.length > 2 && grade in GRADE_POINTS && creditCell !== undefined) {
          semesterSubjects.push({
            subjectCode: code,
            subjectName: name,
            grade,
            credits: creditCell,
            gradePoint: GRADE_POINTS[grade] ?? 0,
            result: (GRADE_POINTS[grade] ?? 0) >= 4 ? "PASS" : "FAIL",
          });
        }
      }
    });

    if (semesterSubjects.length > 0) {
      semesters.push({ semester: semesterNum, subjects: semesterSubjects, sgpa });
    }
  });

  return semesters;
}

function computeGpa(semesters: ReturnType<typeof parseResultsHtml>) {
  return semesters.map((sem) => {
    let totalPoints = 0;
    let totalCredits = 0;
    const subjects = sem.subjects.map((s) => {
      if (!s.credits || s.credits <= 0) {
        throw new Error(`KTU result row ${s.subjectCode} has no valid credits`);
      }
      const credits = s.credits;
      const gp = s.gradePoint ?? GRADE_POINTS[s.grade] ?? 0;
      totalPoints += credits * gp;
      totalCredits += credits;
      return { ...s, credits };
    });
    const sgpa = totalCredits > 0 ? Math.round((totalPoints / totalCredits) * 100) / 100 : 0;
    return { ...sem, subjects, sgpa, totalCredits, earnedCredits: subjects.filter(s => (s.gradePoint ?? 0) >= 4).reduce((a, s) => a + s.credits, 0) };
  });
}

// GET /api/results
router.get("/results", async (req, res) => {
  try {
    const rows = await db.select().from(resultsCacheTable).limit(1);
    if (rows.length === 0) {
      res.status(404).json({ error: "not_found", message: "No results cached. Please sync first." });
      return;
    }
    const row = rows[0]!;
    res.json(row.data);
  } catch (err) {
    req.log.error({ err }, "Failed to get results");
    res.status(500).json({ error: "internal_error", message: "Failed to get results" });
  }
});

// POST /api/results/sync
router.post("/results/sync", async (req, res) => {
  try {
    const creds = await getCredentials();
    if (!creds) {
      res.status(400).json({ error: "no_profile", message: "No profile configured. Please set up your profile first." });
      return;
    }
    if (!creds.ktuPassword) {
      res.status(400).json({ error: "no_credentials", message: "KTU portal credentials not configured." });
      return;
    }

    let semesters;
    try {
      semesters = await fetchKtuResults(creds.registerNumber, creds.ktuPassword);
      semesters = computeGpa(semesters);
    } catch (scrapeErr) {
      req.log.warn({ scrapeErr }, "KTU portal scrape failed");
      res.status(502).json({
        error: "portal_unavailable",
        message: "KTU results could not be fetched. No sample results were used.",
      });
      return;
    }

    if (semesters.length === 0) {
      res.status(502).json({
        error: "results_unreadable",
        message: "KTU returned no readable result rows. No sample results were used.",
      });
      return;
    }

    const latestCgpa = semesters[semesters.length - 1]?.cgpa ??
      semesters.reduce((sum, semester) => sum + (semester.sgpa ?? 0) * (semester.totalCredits ?? 0), 0) /
      Math.max(1, semesters.reduce((sum, semester) => sum + (semester.totalCredits ?? 0), 0));

    const responseData = {
      registerNumber: creds.registerNumber,
      studentName: "",
      semesters,
      latestCgpa,
      syncedAt: new Date().toISOString(),
    };

    // Upsert cache
    const existing = await db.select().from(resultsCacheTable).limit(1);
    if (existing.length > 0) {
      await db.update(resultsCacheTable).set({
        registerNumber: creds.registerNumber,
        data: responseData,
        syncedAt: new Date(),
      }).where(eq(resultsCacheTable.id, "singleton"));
    } else {
      await db.insert(resultsCacheTable).values({
        id: "singleton",
        registerNumber: creds.registerNumber,
        data: responseData,
        syncedAt: new Date(),
      });
    }

    res.json(responseData);
  } catch (err) {
    req.log.error({ err }, "Failed to sync results");
    res.status(500).json({ error: "internal_error", message: "Failed to sync results from KTU portal" });
  }
});

// GET /api/results/gpa
router.get("/results/gpa", async (req, res) => {
  try {
    const rows = await db.select().from(resultsCacheTable).limit(1);
    if (rows.length === 0) {
      res.status(404).json({ error: "not_found", message: "No results cached. Please sync first." });
      return;
    }
    const data = rows[0]!.data as {
      semesters?: Array<{ semester: number; sgpa?: number; cgpa?: number; totalCredits?: number }>;
    };
    const semesters = data.semesters ?? [];
    const cgpa = semesters.reduce((sum, semester) => sum + (semester.sgpa ?? 0) * (semester.totalCredits ?? 0), 0) /
      Math.max(1, semesters.reduce((sum, semester) => sum + (semester.totalCredits ?? 0), 0));
    const percentage = Math.max(0, (cgpa - 0.5) * 10);

    let classification = "Fail";
    if (cgpa >= 9.0) classification = "First Class with Distinction";
    else if (cgpa >= 7.5) classification = "First Class";
    else if (cgpa >= 6.5) classification = "Second Class";
    else if (cgpa >= 5.5) classification = "Pass";

    res.json({
      cgpa: Math.round(cgpa * 100) / 100,
      semesterWise: semesters.map((s) => ({ semester: s.semester, sgpa: s.sgpa ?? 0 })),
      percentageEquivalent: Math.round(percentage * 100) / 100,
      classification,
    });
  } catch (err) {
    req.log.error({ err }, "Failed to get GPA summary");
    res.status(500).json({ error: "internal_error", message: "Failed to get GPA summary" });
  }
});

export default router;
