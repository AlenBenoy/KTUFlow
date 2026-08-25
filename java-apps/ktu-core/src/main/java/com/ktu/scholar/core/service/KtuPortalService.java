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
 * When the portal is unreachable or its response cannot be parsed, the
 * service throws. Student records must never be replaced with invented data.
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
     * Returns only rows parsed from the KTU portal. Student records are
     * never replaced with invented data.
     */
    public List<SemesterResult> fetchResults(String registerNumber, String password) throws IOException {
        List<SemesterResult> results = scrapeResults(registerNumber, password);
        if (results.isEmpty()) {
            throw new IOException("KTU returned no readable result rows");
        }
        return results;
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
                double credits = 0;
                for (int j = 2; j < cells.size() - 1; j++) {
                    try {
                        double candidate = Double.parseDouble(cells.get(j).text().trim());
                        if (candidate > 0 && candidate <= 10) {
                            credits = candidate;
                            break;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (credits == 0) continue;
                sr.setCredits(credits);
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
