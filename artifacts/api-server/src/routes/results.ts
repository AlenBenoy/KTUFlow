import { Router } from "express";
import axios from "axios";
import * as cheerio from "cheerio";
import https from "node:https";
import { db } from "@workspace/db";
import { resultsCacheTable } from "@workspace/db";
import { eq } from "drizzle-orm";
import { getCredentials } from "./profile.js";

const router = Router();

const KTU_BASE = "https://app.ktu.edu.in";
const RESULTS_URL = `${KTU_BASE}/eu/res/semesterGradeCardListing.htm`;

// Grade → gradePoint map (KTU 2024)
const GRADE_POINTS: Record<string, number> = {
  S: 10, "A+": 9, A: 8.5, "B+": 8, B: 7, "C+": 6, C: 5, D: 4, F: 0, FE: 0, I: 0, W: 0,
};

async function fetchKtuResults(registerNumber: string, password: string, semester: number) {
  const cookieJar = new Map<string, string>();
  const rememberCookies = (setCookies: string[] | undefined) => {
    for (const raw of setCookies ?? []) {
      const pair = raw.split(";", 1)[0];
      const separator = pair.indexOf("=");
      if (separator > 0) cookieJar.set(pair.slice(0, separator), pair.slice(separator + 1));
    }
  };
  const cookieHeader = () =>
    [...cookieJar.entries()].map(([key, value]) => `${key}=${value}`).join("; ");
  const session = axios.create({
    baseURL: KTU_BASE,
    timeout: 20000,
    headers: {
      "User-Agent":
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36",
      "Content-Type": "application/x-www-form-urlencoded",
    },
    withCredentials: true,
    // Scoped to the KTU upstream only: its certificate chain is intermittently
    // incomplete from this environment, while the rest of the API remains strict.
    httpsAgent: new https.Agent({ rejectUnauthorized: false }),
    maxRedirects: 0,
    validateStatus: (status) => status >= 200 && status < 400,
  });

  const request = async (url: string, config: Parameters<typeof session.request>[0] = {}) => {
    const response = await session.request({
      ...config,
      url,
      headers: { ...(config.headers ?? {}), ...(cookieHeader() ? { Cookie: cookieHeader() } : {}) },
    });
    rememberCookies(response.headers["set-cookie"] as string[] | undefined);
    return response;
  };

  // The portal currently redirects this legacy path to /login.htm.
  let loginPage = await request("/eu/bms/userLogin.htm");
  if (loginPage.status >= 300 && loginPage.headers.location) {
    loginPage = await request(loginPage.headers.location);
  }
  const $login = cheerio.load(loginPage.data as string);
  const csrfToken = ($login('input[name="CSRF_TOKEN"]').val() ??
    $login('input[name="_csrf"]').val()) as string | undefined;
  const formAction = $login("form[name='loginform']").attr("action") || "/login.htm";
  const loginPath = new URL(formAction, KTU_BASE).pathname;

  // Step 2: POST login
  const loginParams = new URLSearchParams({
    username: registerNumber,
    password,
    ...(csrfToken ? { CSRF_TOKEN: csrfToken } : {}),
  });

  const loginRes = await request(loginPath, {
    method: "POST",
    data: loginParams.toString(),
    headers: { "Content-Type": "application/x-www-form-urlencoded", Referer: `${KTU_BASE}${loginPath}` },
  });
  if (loginRes.status >= 300 && loginRes.headers.location && /login/i.test(loginRes.headers.location)) {
    throw new Error("KTU rejected the supplied credentials");
  }
  if (loginRes.status === 200 && /name=["']loginform["']/i.test(String(loginRes.data))) {
    throw new Error("KTU rejected the supplied credentials");
  }

  // Step 3: Load the semester grade-card search page, then submit its form.
  let resultsPage = await request(RESULTS_URL);
  if (resultsPage.status >= 300 && resultsPage.headers.location) {
    const target = new URL(resultsPage.headers.location, KTU_BASE).pathname;
    if (/login/i.test(target)) throw new Error("KTU session was not authenticated");
    resultsPage = await request(target);
  }
  if (/name=["']loginform["']/i.test(String(resultsPage.data))) {
    throw new Error("KTU session was not authenticated");
  }

  const $results = cheerio.load(resultsPage.data as string);
  const searchForm = $results("#semesterGradeCardListingSearchForm").length > 0
    ? $results("#semesterGradeCardListingSearchForm").first()
    : $results("form").filter((_index, form) =>
      $results(form).find('select[name="semesterId"]').length > 0).first();
  const searchAction = searchForm.attr("action") || RESULTS_URL;
  const searchPath = new URL(searchAction, KTU_BASE).pathname;
  const stdId = ($results('input[name="stdId"]').val() as string | undefined) ?? "";
  const searchParams = new URLSearchParams({
    semesterId: String(semester),
    stdId,
    search: "Search",
  });
  const searchResponse = await request(searchPath, {
    method: (searchForm.attr("method") || "post").toUpperCase(),
    data: searchParams.toString(),
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Referer: RESULTS_URL,
    },
  });
  if (searchResponse.status >= 300 && searchResponse.headers.location) {
    const target = new URL(searchResponse.headers.location, KTU_BASE).pathname;
    if (/login/i.test(target)) throw new Error("KTU session was not authenticated");
    searchResponse.data = (await request(target)).data;
  }
  if (/name=["']loginform["']/i.test(String(searchResponse.data))) {
    throw new Error("KTU session was not authenticated");
  }

  const parsed = parseResultsHtml(searchResponse.data as string, semester);
  if (parsed.length === 0) {
    throw new Error("KTU results page contained no readable rows");
  }
  return parsed;
}

function parseResultsHtml(html: string, requestedSemester: number) {
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

  // KTU has used several table layouts. Accept rows only when the table
  // headers identify code, name, credits, and grade; never guess credits from
  // marks or column position.
  $("table").each((_i, table) => {
    const rows = $(table).find("tr");
    if (rows.length < 2) return;

    const semesterSubjects: typeof semesters[0]["subjects"] = [];
    const headers = $(rows[0]).find("th, td").toArray().map((cell) => normalize($(cell).text()));
    const findColumn = (...names: string[]) => headers.findIndex((header) =>
      names.some((name) => header.includes(name)));
    const codeIndex = findColumn("subject code", "course code", "sub code");
    const nameIndex = findColumn("subject name", "course name");
    const creditIndex = findColumn("credit", "credits");
    const gradeIndex = findColumn("grade");
    if (codeIndex < 0 || nameIndex < 0 || creditIndex < 0 || gradeIndex < 0) return;

    const sectionText = $(table).parent().text() + " " + $(table).prevAll().slice(0, 3).text();
    const semesterMatch = sectionText.match(/semester\s*[-:]?\s*(\d+)/i);
    const semesterNum = semesterMatch ? Number(semesterMatch[1]) : requestedSemester;

    rows.slice(1).each((_j, row) => {
      const cells = $(row).find("td").toArray();
      const value = (index: number) =>
        index >= 0 && cells[index] ? $(cells[index]).text().replace(/\s+/g, " ").trim() : "";
      const code = value(codeIndex);
      const name = value(nameIndex);
      const grade = value(gradeIndex).toUpperCase();
      const credits = Number(value(creditIndex).replace(",", "."));
      if (code.length > 2 && name.length > 2 && grade in GRADE_POINTS &&
          Number.isFinite(credits) && credits > 0 && credits <= 10) {
          semesterSubjects.push({
            subjectCode: code,
            subjectName: name,
            grade,
            credits,
            gradePoint: GRADE_POINTS[grade] ?? 0,
            result: (GRADE_POINTS[grade] ?? 0) >= 4 ? "PASS" : "FAIL",
          });
      }
    });

    if (semesterSubjects.length > 0) {
      semesters.push({ semester: semesterNum, subjects: semesterSubjects });
    }
  });

  return semesters;
}

function normalize(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
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

    const requestedSemester = Number(req.body?.semester ?? 1);
    if (!Number.isInteger(requestedSemester) || requestedSemester < 1 || requestedSemester > 8) {
      res.status(400).json({ error: "validation_error", message: "semester must be an integer from 1 to 8" });
      return;
    }

    let semesters;
    try {
      try {
        semesters = await fetchKtuResults(creds.registerNumber, creds.ktuPassword, requestedSemester);
      } catch (firstError) {
        // A pasted password can accidentally include surrounding whitespace.
        // Try the normalized value only after the exact stored credential fails.
        const normalizedPassword = creds.ktuPassword.trim();
        if (normalizedPassword === creds.ktuPassword) throw firstError;
        semesters = await fetchKtuResults(creds.registerNumber, normalizedPassword, requestedSemester);
      }
      semesters = computeGpa(semesters);
    } catch (scrapeErr) {
      const portalError = scrapeErr as { message?: string; code?: string; response?: { status?: number } };
      req.log.warn({
        message: portalError.message,
        code: portalError.code,
        status: portalError.response?.status,
      }, "KTU portal scrape failed");
      if (portalError.message && /rejected|not authenticated/i.test(portalError.message)) {
        res.status(401).json({
          error: "ktu_auth_failed",
          message: "KTU rejected the stored ID or password. Update your KTU credentials and try again.",
        });
        return;
      }
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
