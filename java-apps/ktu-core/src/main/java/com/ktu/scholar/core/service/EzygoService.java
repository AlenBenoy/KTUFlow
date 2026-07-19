package com.ktu.scholar.core.service;

import com.google.gson.*;
import com.ktu.scholar.core.model.SubjectAttendance;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Integrates with EzyGo (https://edu.ezygo.app) attendance portal.
 *
 * EzyGo is a single-page Angular app backed by a REST API.
 * Login returns a JWT; subsequent calls pass it as Bearer token.
 *
 * Falls back to demo data when the portal is unreachable.
 */
public class EzygoService {

    private static final Logger log = LoggerFactory.getLogger(EzygoService.class);
    private static final String BASE = "https://edu.ezygo.app";

    private final OkHttpClient http;
    private final Gson gson = new Gson();

    public EzygoService() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    /**
     * Fetches attendance from EzyGo; falls back to demo data on any error.
     */
    public List<SubjectAttendance> fetchAttendance(String registerNumber, String ezygoPassword) {
        try {
            return scrapeAttendance(registerNumber, ezygoPassword);
        } catch (Exception e) {
            log.warn("EzyGo fetch failed: {}. Returning demo data.", e.getMessage());
            return demoAttendance();
        }
    }

    // ── Scraping ──────────────────────────────────────────────────────────

    private List<SubjectAttendance> scrapeAttendance(String regNum, String password)
            throws IOException {

        // Step 1: Login
        String loginBody = gson.toJson(Map.of("username", regNum, "password", password));
        Request loginReq = new Request.Builder()
                .url(BASE + "/api/auth/login")
                .post(RequestBody.create(loginBody, MediaType.parse("application/json")))
                .build();

        String token;
        try (Response r = http.newCall(loginReq).execute()) {
            String body = r.body() != null ? r.body().string() : "{}";
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            token = json.has("token") ? json.get("token").getAsString()
                    : json.has("access_token") ? json.get("access_token").getAsString()
                    : null;
        }

        if (token == null || token.isEmpty()) throw new IOException("EzyGo: login failed — no token");

        // Step 2: Fetch attendance
        Request attReq = new Request.Builder()
                .url(BASE + "/api/student/attendance")
                .get()
                .addHeader("Authorization", "Bearer " + token)
                .build();

        String attBody;
        try (Response r = http.newCall(attReq).execute()) {
            attBody = r.body() != null ? r.body().string() : "[]";
        }

        // Try both array-root and object-with-subjects
        JsonElement root = JsonParser.parseString(attBody);
        JsonArray arr;
        if (root.isJsonArray()) {
            arr = root.getAsJsonArray();
        } else {
            JsonObject obj = root.getAsJsonObject();
            arr = obj.has("subjects") ? obj.get("subjects").getAsJsonArray()
                    : obj.has("data") ? obj.get("data").getAsJsonArray()
                    : new JsonArray();
        }

        List<SubjectAttendance> list = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject s = el.getAsJsonObject();
            SubjectAttendance sa = new SubjectAttendance();
            sa.setSubjectCode(getString(s, "subjectCode", "code", "subject_code", ""));
            sa.setSubjectName(getString(s, "subjectName", "name", "subject_name", "Unknown"));
            sa.setTotalClasses(getInt(s, "totalClasses", "total", "total_classes", 0));
            sa.setAttendedClasses(getInt(s, "attendedClasses", "attended", "attended_classes", 0));
            list.add(AttendanceAnalysisService.analyse(sa));
        }
        return list;
    }

    // ── Demo data ─────────────────────────────────────────────────────────

    private List<SubjectAttendance> demoAttendance() {
        return Arrays.asList(
            make("CST301", "Data Structures",                     52, 42),
            make("CST303", "Logic System Design",                 48, 32),
            make("CST305", "Computer Organization & Architecture", 50, 44),
            make("MAT203", "Discrete Mathematical Structures",    52, 37),
            make("CST307", "OOP using Java",                      50, 40),
            make("HUT200", "Professional Ethics",                  30, 16)
        );
    }

    private SubjectAttendance make(String code, String name, int total, int attended) {
        SubjectAttendance sa = new SubjectAttendance();
        sa.setSubjectCode(code); sa.setSubjectName(name);
        sa.setTotalClasses(total); sa.setAttendedClasses(attended);
        return AttendanceAnalysisService.analyse(sa);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────

    private String getString(JsonObject obj, String... keys) {
        for (int i = 0; i < keys.length - 1; i++) {
            if (obj.has(keys[i])) return obj.get(keys[i]).getAsString();
        }
        return keys[keys.length - 1]; // last element is default
    }

    private int getInt(JsonObject obj, String... keys) {
        for (int i = 0; i < keys.length - 1; i++) {
            if (obj.has(keys[i])) try { return obj.get(keys[i]).getAsInt(); } catch (Exception ignored) {}
        }
        try { return Integer.parseInt(keys[keys.length - 1]); } catch (Exception ignored) { return 0; }
    }
}
