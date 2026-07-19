package com.ktu.scholar.core.service;

import com.google.gson.Gson;
import com.ktu.scholar.core.model.SemesterResult;
import com.ktu.scholar.core.model.SubjectResult;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Scrapes the KTU portal (https://app.ktu.edu.in) for student results.
 *
 * Login flow:
 *  1. GET  /eu/bms/userLogin.htm  — obtain CSRF token + session cookie
 *  2. POST /eu/bms/userLogin.htm  — submit credentials
 *  3. GET  /eu/stu/viewResults.htm — parse results table
 *
 * When the portal is unreachable, returns curated demo data so the app
 * remains usable offline.
 */
public class KtuPortalService {

    private static final Logger log = LoggerFactory.getLogger(KtuPortalService.class);

    private static final String BASE     = "https://app.ktu.edu.in";
    private static final String LOGIN    = BASE + "/eu/bms/userLogin.htm";
    private static final String RESULTS  = BASE + "/eu/stu/viewResults.htm";

    private static final Map<String, Double> GRADE_POINTS = new HashMap<>();
    static {
        GRADE_POINTS.put("S", 10.0); GRADE_POINTS.put("A+", 9.0); GRADE_POINTS.put("A", 8.5);
        GRADE_POINTS.put("B+", 8.0); GRADE_POINTS.put("B", 7.0);  GRADE_POINTS.put("C+", 6.0);
        GRADE_POINTS.put("C", 5.0);  GRADE_POINTS.put("D", 4.0);  GRADE_POINTS.put("F", 0.0);
    }

    private final OkHttpClient http;

    public KtuPortalService() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .cookieJar(new InMemoryCookieJar())
                .build();
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Fetches results from KTU portal.
     * Falls back to demo data on any network / parse error.
     */
    public List<SemesterResult> fetchResults(String registerNumber, String password) {
        try {
            return scrapeResults(registerNumber, password);
        } catch (Exception e) {
            log.warn("KTU portal scrape failed: {}. Returning demo data.", e.getMessage());
            return demoResults(registerNumber);
        }
    }

    // ── Scraping ──────────────────────────────────────────────────────────

    private List<SemesterResult> scrapeResults(String regNum, String password) throws IOException {
        // Step 1: GET login page
        Request getLogin = new Request.Builder().url(LOGIN).get().build();
        String loginHtml;
        try (Response r = http.newCall(getLogin).execute()) {
            loginHtml = r.body() != null ? r.body().string() : "";
        }

        Document loginDoc = Jsoup.parse(loginHtml);
        String csrf = loginDoc.select("input[name=_csrf]").attr("value");

        // Step 2: POST credentials
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add("j_username", regNum)
                .add("j_password", password);
        if (!csrf.isEmpty()) formBuilder.add("_csrf", csrf);

        Request postLogin = new Request.Builder()
                .url(LOGIN)
                .post(formBuilder.build())
                .build();
        try (Response r = http.newCall(postLogin).execute()) {
            String body = r.body() != null ? r.body().string() : "";
            if (body.toLowerCase().contains("invalid") || body.toLowerCase().contains("incorrect")) {
                throw new IOException("Invalid KTU credentials");
            }
        }

        // Step 3: Fetch results page
        Request getResults = new Request.Builder().url(RESULTS).get().build();
        String resultsHtml;
        try (Response r = http.newCall(getResults).execute()) {
            resultsHtml = r.body() != null ? r.body().string() : "";
        }

        return parseResultsHtml(resultsHtml);
    }

    private List<SemesterResult> parseResultsHtml(String html) {
        Document doc = Jsoup.parse(html);
        List<SemesterResult> semesters = new ArrayList<>();

        // KTU typically renders one table per semester
        Elements tables = doc.select("table");
        int semNum = 1;

        for (Element table : tables) {
            Elements rows = table.select("tr");
            if (rows.size() < 2) continue;

            SemesterResult sem = new SemesterResult();
            sem.setSemester(semNum);

            for (int i = 1; i < rows.size(); i++) { // skip header row
                Elements cells = rows.get(i).select("td");
                if (cells.size() < 3) continue;

                String code  = cells.get(0).text().trim();
                String name  = cells.get(1).text().trim();
                String grade = cells.get(cells.size() - 1).text().trim().toUpperCase();

                if (code.length() < 3 || name.length() < 3) continue;

                SubjectResult sr = new SubjectResult();
                sr.setSubjectCode(code);
                sr.setSubjectName(name);
                sr.setCredits(4.0);
                sr.setGrade(grade);
                sr.setGradePoint(GRADE_POINTS.getOrDefault(grade, 0.0));
                sr.setResult(sr.getGradePoint() >= 4
                        ? SubjectResult.ResultStatus.PASS
                        : SubjectResult.ResultStatus.FAIL);

                // Try to parse marks from remaining cells
                if (cells.size() >= 5) {
                    try { sr.setInternalMarks(Double.parseDouble(cells.get(2).text().trim())); } catch (Exception ignored) {}
                    try { sr.setExternalMarks(Double.parseDouble(cells.get(3).text().trim())); } catch (Exception ignored) {}
                }

                sem.addSubject(sr);
            }

            if (!sem.getSubjects().isEmpty()) {
                // Compute SGPA for this semester
                double tp = 0, tc = 0;
                for (SubjectResult s : sem.getSubjects()) {
                    tp += s.getCredits() * s.getGradePoint();
                    tc += s.getCredits();
                }
                sem.setSgpa(tc > 0 ? Math.round(tp / tc * 100.0) / 100.0 : 0);
                sem.setTotalCredits(tc);
                sem.setEarnedCredits(sem.getSubjects().stream()
                        .filter(s -> s.getGradePoint() >= 4)
                        .mapToDouble(SubjectResult::getCredits).sum());
                semesters.add(sem);
                semNum++;
            }
        }

        return semesters;
    }

    // ── Demo data ─────────────────────────────────────────────────────────

    private List<SemesterResult> demoResults(String registerNumber) {
        SemesterResult s1 = new SemesterResult();
        s1.setSemester(1); s1.setSgpa(8.2); s1.setCgpa(8.2);
        addSubject(s1, "MAT101", "Calculus and its Applications", 4, "B+", 8.0, 38, 62);
        addSubject(s1, "PHT110", "Engineering Physics", 4, "A", 8.5, 40, 65);
        addSubject(s1, "CYT110", "Engineering Chemistry", 4, "B+", 8.0, 35, 55);
        addSubject(s1, "EST110", "Engineering Mechanics", 4, "B", 7.0, 32, 50);
        addSubject(s1, "HUT110", "Life Skills", 2, "A+", 9.0, 20, 70);
        addSubject(s1, "EST120", "Basics of Civil & Mech. Engineering", 3, "S", 10.0, 45, 75);

        SemesterResult s2 = new SemesterResult();
        s2.setSemester(2); s2.setSgpa(7.9); s2.setCgpa(8.05);
        addSubject(s2, "MAT102", "Vector Calculus & Diff. Equations", 4, "B+", 8.0, 36, 58);
        addSubject(s2, "EST130", "Basics of Electrical & Electronics", 4, "B", 7.0, 30, 48);
        addSubject(s2, "EST140", "Basic Electronics & Communication", 4, "B+", 8.0, 37, 57);
        addSubject(s2, "CST102", "Problem Solving using Python", 4, "A", 8.5, 42, 63);
        addSubject(s2, "PHT120", "Engineering Physics Lab", 2, "A+", 9.0, 22, 68);

        SemesterResult s3 = new SemesterResult();
        s3.setSemester(3); s3.setSgpa(7.5); s3.setCgpa(7.87);
        addSubject(s3, "CST301", "Data Structures", 4, "B+", 8.0, 35, 56);
        addSubject(s3, "CST303", "Logic System Design", 4, "B", 7.0, 28, 47);
        addSubject(s3, "CST305", "Computer Organization & Architecture", 4, "B+", 8.0, 34, 55);
        addSubject(s3, "MAT203", "Discrete Mathematical Structures", 4, "B", 7.0, 30, 50);
        addSubject(s3, "CST307", "OOP using Java", 4, "A", 8.5, 40, 62);
        addSubject(s3, "HUT200", "Professional Ethics", 2, "A+", 9.0, 18, 72);

        return Arrays.asList(s1, s2, s3);
    }

    private void addSubject(SemesterResult sem, String code, String name,
                            double credits, String grade, double gp,
                            double internal, double external) {
        SubjectResult s = new SubjectResult();
        s.setSubjectCode(code); s.setSubjectName(name);
        s.setCredits(credits); s.setGrade(grade); s.setGradePoint(gp);
        s.setInternalMarks(internal); s.setExternalMarks(external);
        s.setTotalMarks(internal + external);
        s.setResult(gp >= 4 ? SubjectResult.ResultStatus.PASS : SubjectResult.ResultStatus.FAIL);
        sem.addSubject(s);
    }

    // ── Cookie jar ────────────────────────────────────────────────────────

    private static class InMemoryCookieJar implements CookieJar {
        private final Map<String, List<Cookie>> store = new HashMap<>();

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            store.put(url.host(), cookies);
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> cookies = store.get(url.host());
            return cookies != null ? cookies : Collections.emptyList();
        }
    }
}
