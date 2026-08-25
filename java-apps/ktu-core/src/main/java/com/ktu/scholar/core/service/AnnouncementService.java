package com.ktu.scholar.core.service;

import com.ktu.scholar.core.model.Announcement;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Scrapes KTU announcements from https://app.ktu.edu.in.
 * Falls back to hardcoded demo announcements when the portal is unreachable.
 */
public class AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementService.class);
    private static final String BASE = "https://app.ktu.edu.in";
    private static final String NOTIF_URL = BASE + "/eu/notification/viewNotifications.htm";

    private final OkHttpClient http;

    public AnnouncementService() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    /** Returns up to 30 latest announcements. Falls back to demo on failure. */
    public List<Announcement> fetchAnnouncements() {
        try {
            return scrapeAnnouncements();
        } catch (Exception e) {
            log.warn("Announcement scrape failed: {}", e.getMessage());
            throw new RuntimeException("KTU announcements could not be fetched", e);
        }
    }

    // ── Scraping ──────────────────────────────────────────────────────────

    private List<Announcement> scrapeAnnouncements() throws IOException {
        Request req = new Request.Builder()
                .url(NOTIF_URL)
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120")
                .get()
                .build();

        String html;
        try (Response r = http.newCall(req).execute()) {
            html = r.body() != null ? r.body().string() : "";
        }

        Document doc = Jsoup.parse(html);
        List<Announcement> list = new ArrayList<>();

        // Try table rows first
        for (Element row : doc.select("table tr")) {
            Elements cells = row.select("td");
            if (cells.size() < 2) continue;

            String title = cells.get(cells.size() > 2 ? 1 : 0).text().trim();
            String date  = cells.get(0).text().trim();
            Element linkEl = row.selectFirst("a");
            String href  = linkEl != null ? linkEl.attr("href") : "";
            String link  = href.startsWith("http") ? href : BASE + href;

            if (title.length() > 5) {
                Announcement a = new Announcement();
                a.setId(UUID.randomUUID().toString());
                a.setTitle(title);
                a.setContent(title);
                a.setDate(date.isEmpty() ? LocalDate.now().toString() : date);
                a.setLink(link);
                a.setCategory(categorize(title));
                a.setNew(true);
                list.add(a);
            }
        }

        if (list.isEmpty()) {
            // Fallback: any link that smells like a notice
            for (Element a : doc.select("a")) {
                String text = a.text().trim();
                String href = a.attr("href");
                if (text.length() > 10 && (href.contains("notice") || href.contains("notif"))) {
                    Announcement ann = new Announcement();
                    ann.setId(UUID.randomUUID().toString());
                    ann.setTitle(text);
                    ann.setContent(text);
                    ann.setDate(LocalDate.now().toString());
                    ann.setLink(href.startsWith("http") ? href : BASE + href);
                    ann.setCategory(categorize(text));
                    ann.setNew(true);
                    list.add(ann);
                }
            }
        }

        return list.stream().limit(30).toList();
    }

    // ── Demo data ─────────────────────────────────────────────────────────

    private List<Announcement> demoAnnouncements() {
        String today = LocalDate.now().toString();
        return Arrays.asList(
            ann("S7 Regular Exam Registration Open",
                "Semester 7 exam registration is now open. Last date: 30 July.",
                Announcement.Category.EXAM, today, BASE),
            ann("B.Tech S5 Supplementary Results Published",
                "Results for S5 supplementary examinations are now available.",
                Announcement.Category.RESULT, LocalDate.now().minusDays(3).toString(), BASE),
            ann("Revised Academic Calendar 2026–27 Released",
                "The revised academic calendar for 2026-27 is now available.",
                Announcement.Category.ACADEMIC, LocalDate.now().minusDays(7).toString(), BASE),
            ann("Anti-Ragging Compliance Certificate Deadline",
                "Colleges must submit anti-ragging certificates by month-end.",
                Announcement.Category.GENERAL, LocalDate.now().minusDays(10).toString(), BASE),
            ann("S3 Regular Examination Hall Tickets Available",
                "Hall tickets for S3 regular examinations available for download.",
                Announcement.Category.EXAM, LocalDate.now().minusDays(12).toString(), BASE),
            ann("Online FDP on Advanced Computing Technologies",
                "Registrations open for the online Faculty Development Programme.",
                Announcement.Category.ACADEMIC, LocalDate.now().minusDays(15).toString(), BASE)
        );
    }

    private Announcement ann(String title, String content, Announcement.Category cat,
                             String date, String link) {
        Announcement a = new Announcement();
        a.setId(UUID.randomUUID().toString());
        a.setTitle(title); a.setContent(content);
        a.setCategory(cat); a.setDate(date); a.setLink(link);
        a.setNew(true);
        return a;
    }

    private Announcement.Category categorize(String title) {
        String t = title.toLowerCase();
        if (t.contains("exam") || t.contains("hall ticket") || t.contains("timetable"))
            return Announcement.Category.EXAM;
        if (t.contains("result") || t.contains("grade") || t.contains("publish"))
            return Announcement.Category.RESULT;
        if (t.contains("registration") || t.contains("semester") || t.contains("academic"))
            return Announcement.Category.ACADEMIC;
        return Announcement.Category.GENERAL;
    }
}
