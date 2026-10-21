package com.ktu.scholar.core.service;
 
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
 
/**
 * Scrapes the KTU portal (https://app.ktu.edu.in) for student results.
 *
 * Fixes applied (see inline comments for details):
 *  1. Jsoup was told the page's *base URI* was just the site root
 *     ("https://app.ktu.edu.in") when resolving relative links on pages that
 *     actually live under "/eu/res/...". That silently produced wrong absolute
 *     URLs for every grade-card link (dropping the "/eu/res/" path segment),
 *     so the portal usually returned nothing readable. We now parse each page
 *     with its own real URL as the base.
 *  2. The login page has more than one login form on it (student/institution/
 *     staff), and the old code searched the *whole document* for a field named
 *     "j_username" etc. It could pick up the wrong form's fields entirely. We
 *     now scope the search to the specific <form> that contains a password
 *     input, capture every hidden field in that form (not just a guessed CSRF
 *     name) and resubmit them, and post to whatever URL that form's own
 *     "action" attribute points to instead of assuming it's the same page.
 *  3. "Logged in vs still on login page" was decided by checking for the
 *     literal text action="login.htm", which breaks the moment the real form
 *     posts anywhere else. We now primarily check for the presence/absence of
 *     a password field on the returned page, which is a much more reliable
 *     signal regardless of the exact login endpoint.
 *  4. If KTU is showing a CAPTCHA, the previous code would just report
 *     "Invalid KTU credentials", which is misleading. We now detect a CAPTCHA
 *     challenge and say so explicitly.
 *  5. The subject table parser only ever extracted Code/Name/Credits/Grade.
 *     The UI has always had columns for Internal, External and Total marks,
 *     but they never got populated (always showing "—") because the column
 *     mapper never looked for those headers. Fixed to detect and parse them.
 */
public class KtuPortalService {
 
    private static final Logger log = LoggerFactory.getLogger(KtuPortalService.class);
 
    private static final String BASE    = "https://app.ktu.edu.in";
    private static final String LOGIN   = BASE + "/login.htm";
    private static final String LISTING = BASE + "/eu/res/semesterGradeCardListing.htm";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
 
    private static final Pattern[] SEMESTER_PATTERNS = new Pattern[]{
            Pattern.compile("\\bS(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bSEM(?:ESTER)?\\s*(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(\\d{1,2})(?:ST|ND|RD|TH)?\\s*SEM(?:ESTER)?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bSEMESTER\\s*(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE)
    };
 
    private static final Map<String, Integer> ROMAN_SEMESTERS = new HashMap<>();
    static {
        ROMAN_SEMESTERS.put("I", 1);    ROMAN_SEMESTERS.put("II", 2);
        ROMAN_SEMESTERS.put("III", 3);  ROMAN_SEMESTERS.put("IV", 4);
        ROMAN_SEMESTERS.put("V", 5);    ROMAN_SEMESTERS.put("VI", 6);
        ROMAN_SEMESTERS.put("VII", 7);  ROMAN_SEMESTERS.put("VIII", 8);
    }
 
    private static final Map<String, Double> GRADE_POINTS = new HashMap<>();
    static {
        GRADE_POINTS.put("S", 10.0); GRADE_POINTS.put("A+", 9.0); GRADE_POINTS.put("A", 8.5);
        GRADE_POINTS.put("B+", 8.0); GRADE_POINTS.put("B", 7.5);  GRADE_POINTS.put("C+", 7.0);
        GRADE_POINTS.put("C", 6.5);  GRADE_POINTS.put("D", 6.0);  GRADE_POINTS.put("P", 5.5);
        GRADE_POINTS.put("F", 0.0);  GRADE_POINTS.put("FE", 0.0); GRADE_POINTS.put("I", 0.0);
    }
 
    private final OkHttpClient http;
 
    public KtuPortalService() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .cookieJar(new InMemoryCookieJar())
                .build();
    }
 
    /**
     * Fetches results from the KTU portal.
     */
    public List<SemesterResult> fetchResults(String registerNumber, String password) throws IOException {
        List<SemesterResult> results = scrapeResults(registerNumber, password);
        if (results.isEmpty()) {
            throw new IOException("KTU returned no readable result rows");
        }
        return results;
    }
 
    private List<SemesterResult> scrapeResults(String regNum, String password) throws IOException {
        login(regNum, password);
        String listingHtml = get(LISTING);
 
        if (isLoginPage(listingHtml)) {
            throw new IOException("KTU session was not established; portal bounced back to the login page");
        }
 
        // Fix #1: resolve relative links against the LISTING page's own URL, not
        // the bare site root, so "/eu/res/" isn't silently dropped from the path.
        List<GradeCardLink> cards = parseGradeCardListing(listingHtml, LISTING);
        if (cards.isEmpty()) {
            log.warn("No grade-card links found on listing page. First 500 chars:\n{}",
                    listingHtml.substring(0, Math.min(500, listingHtml.length())));
            return Collections.emptyList();
        }
 
        List<SemesterResult> semesters = new ArrayList<>();
        int fallbackSem = 1;
 
        for (GradeCardLink card : cards) {
            String cardHtml = get(card.url);
            SemesterResult sem = parseGradeCard(cardHtml);
            if (sem == null || sem.getSubjects().isEmpty()) continue;
 
            Integer semNum = extractSemesterNumber(card.examName);
            sem.setSemester(semNum != null ? semNum : fallbackSem);
            fallbackSem = sem.getSemester() + 1;
            semesters.add(sem);
        }
 
        // Sort by semester number ascending
        semesters.sort(Comparator.comparingInt(SemesterResult::getSemester));
        return semesters;
    }
 
    private void login(String regNum, String password) throws IOException {
        String loginHtml = get(LOGIN);
        // Fix #2/#3: parse with the real login page URL as the base URI so that
        // abs:href / abs:action resolve correctly to full URLs.
        Document loginDoc = Jsoup.parse(loginHtml, LOGIN);
 
        // The login page can contain more than one login form (student /
        // institution / staff). Scope everything to the form that actually has
        // a password field instead of searching the whole document.
        Element loginForm = null;
        for (Element form : loginDoc.select("form")) {
            if (form.selectFirst("input[type=password]") != null) {
                loginForm = form;
                break;
            }
        }
        Element searchScope = loginForm != null ? loginForm : loginDoc;
 
        // CAPTCHA check - fail fast with a clear, honest message instead of a
        // misleading "invalid credentials" error later on.
        if (searchScope.selectFirst("img[src*=captcha], input[name*=captcha], input[id*=captcha]") != null) {
            throw new IOException("KTU is showing a CAPTCHA challenge, which this app can't solve automatically. "
                    + "Please log in once through your browser, then try syncing again.");
        }
 
        Element csrfElem = searchScope.selectFirst(
                "input[name=CSRF_TOKEN], input[name=_csrf], input[name=csrf_token], input[name*=csrf i], input[name*=token i]");
        String csrf = csrfElem != null ? csrfElem.attr("value") : "";
        String csrfParam = csrfElem != null ? csrfElem.attr("name") : "";
 
        Element userElem = searchScope.selectFirst(
                "input[name=j_username], input[name=username], input[name=userId], input[name=loginId], input[name=regno], input[type=text], input[type=email]");
        Element passElem = searchScope.selectFirst("input[type=password]");
 
        String userParam = userElem != null && !userElem.attr("name").isEmpty() ? userElem.attr("name") : "j_username";
        String passParam = passElem != null && !passElem.attr("name").isEmpty() ? passElem.attr("name") : "j_password";
 
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add(userParam, regNum)
                .add(passParam, password);
 
        if (!csrf.isEmpty() && !csrfParam.isEmpty()) {
            formBuilder.add(csrfParam, csrf);
        }
 
        // Fix #2: forward every other hidden field the form carries (view state,
        // extra tokens, timestamps, etc.) so the server sees a complete, valid
        // submission instead of just the two fields we knew to look for.
        if (loginForm != null) {
            for (Element hidden : loginForm.select("input[type=hidden]")) {
                String name = hidden.attr("name");
                if (name.isEmpty()) continue;
                if (name.equalsIgnoreCase(userParam) || name.equalsIgnoreCase(passParam) || name.equalsIgnoreCase(csrfParam)) {
                    continue; // already added above
                }
                formBuilder.add(name, hidden.attr("value"));
            }
        }
 
        // Fix #2: post to the form's own action URL (resolved to an absolute
        // URL) rather than assuming it's always the login page itself.
        String postUrl = LOGIN;
        if (loginForm != null) {
            String absAction = loginForm.attr("abs:action");
            if (!absAction.isEmpty()) {
                postUrl = absAction;
            }
        }
 
        Request post = new Request.Builder()
                .url(postUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", LOGIN)
                .post(formBuilder.build())
                .build();
 
        String afterLogin;
        try (Response r = http.newCall(post).execute()) {
            afterLogin = r.body() != null ? r.body().string() : "";
        }
 
        if (isLoginPage(afterLogin)) {
            throw new IOException("Invalid KTU credentials");
        }
    }
 
    /**
     * Fix #3: the most reliable "are we still on the login form" signal is
     * whether the page still has a password field. The old text-based checks
     * are kept as secondary hints since they're cheap and still useful for
     * pages Jsoup might mis-parse.
     */
    private boolean isLoginPage(String html) {
        if (html == null || html.isEmpty()) return true;
 
        try {
            Document doc = Jsoup.parse(html);
            if (doc.selectFirst("input[type=password]") != null) {
                return true;
            }
        } catch (Exception ignored) {
            // fall through to the text-based heuristics below
        }
 
        String lower = html.toLowerCase();
        return lower.contains("login-username") || lower.contains("name=\"loginform\"");
    }
 
    private static class GradeCardLink {
        final String examName;
        final String url;
        GradeCardLink(String examName, String url) { this.examName = examName; this.url = url; }
    }
 
    private List<GradeCardLink> parseGradeCardListing(String html, String pageUrl) {
        // Fix #1: base URI must be the actual page URL, not the bare site root,
        // otherwise abs:href resolves relative links to the wrong path.
        Document doc = Jsoup.parse(html, pageUrl);
        List<GradeCardLink> links = new ArrayList<>();
        Set<String> seen = new HashSet<>();
 
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("abs:href");
            String lowerHref = href.toLowerCase();
 
            boolean isResultLink = lowerHref.contains("gradecard") ||
                    lowerHref.contains("grade_card") ||
                    lowerHref.contains("viewsemestergradecard") ||
                    lowerHref.contains("viewgradecard") ||
                    lowerHref.contains("/eu/res/");
 
            if (!isResultLink) continue;
            if (!seen.add(href)) continue;
 
            String examName = a.text().trim();
            Element row = a.closest("tr");
            if (row != null && row.text().trim().length() > examName.length()) {
                examName = row.text().trim();
            }
 
            links.add(new GradeCardLink(examName, href));
        }
 
        return links;
    }
 
    private Integer extractSemesterNumber(String examName) {
        if (examName == null || examName.isEmpty()) return null;
 
        for (Pattern p : SEMESTER_PATTERNS) {
            Matcher m = p.matcher(examName);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {}
            }
        }
 
        // Try Roman numerals (e.g. S III or Semester IV)
        Matcher mRoman = Pattern.compile("\\bS\\s*([I|V|X]+)\\b", Pattern.CASE_INSENSITIVE).matcher(examName);
        if (mRoman.find()) {
            String roman = mRoman.group(1).toUpperCase();
            if (ROMAN_SEMESTERS.containsKey(roman)) {
                return ROMAN_SEMESTERS.get(roman);
            }
        }
 
        // Fallback search for any single digit between 1 and 8
        Matcher mDigit = Pattern.compile("\\b([1-8])\\b").matcher(examName);
        if (mDigit.find()) {
            return Integer.parseInt(mDigit.group(1));
        }
 
        return null;
    }
 
    private SemesterResult parseGradeCard(String html) {
        Document doc = Jsoup.parse(html);
 
        for (Element table : doc.select("table")) {
            Elements rows = table.select("tr");
            if (rows.size() < 2) continue;
 
            // Search for header row in table instead of hardcoding row index 0
            int headerRowIdx = -1;
            int[] cols = {-1, -1, -1, -1, -1, -1, -1};
 
            for (int r = 0; r < Math.min(rows.size(), 5); r++) {
                int[] c = mapColumns(rows.get(r));
                if (c[0] >= 0 && c[3] >= 0) { // Code and Grade columns found
                    cols = c;
                    headerRowIdx = r;
                    break;
                }
            }
 
            if (headerRowIdx < 0) continue;
 
            SemesterResult sem = new SemesterResult();
            for (int i = headerRowIdx + 1; i < rows.size(); i++) {
                Elements cells = rows.get(i).select("td");
                if (cells.size() <= cols[0] || cells.size() <= cols[3]) continue;
 
                String code  = cells.get(cols[0]).text().trim();
                String name  = cols[1] >= 0 && cells.size() > cols[1] ? cells.get(cols[1]).text().trim() : "";
                String grade = cells.get(cols[3]).text().trim().toUpperCase();
 
                if (code.length() < 2 || !GRADE_POINTS.containsKey(grade)) continue;
 
                SubjectResult sr = new SubjectResult();
                sr.setSubjectCode(code);
                sr.setSubjectName(name);
                sr.setGrade(grade);
                sr.setGradePoint(GRADE_POINTS.get(grade));
 
                double credits = 0;
                if (cols[2] >= 0 && cells.size() > cols[2]) {
                    try { credits = Double.parseDouble(cells.get(cols[2]).text().trim()); }
                    catch (NumberFormatException ignored) {}
                }
                sr.setCredits(credits);
 
                // Fix #5: parse Internal/External/Total marks when the table has
                // those columns; the ResultsPanel UI has always expected them.
                sr.setInternalMarks(parseMarksCell(cells, cols[4]));
                sr.setExternalMarks(parseMarksCell(cells, cols[5]));
                sr.setTotalMarks(parseMarksCell(cells, cols[6]));
 
                sr.setResult(sr.getGradePoint() >= 4
                        ? SubjectResult.ResultStatus.PASS
                        : SubjectResult.ResultStatus.FAIL);
 
                sem.addSubject(sr);
            }
 
            if (!sem.getSubjects().isEmpty()) {
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
                return sem;
            }
        }
 
        log.warn("No subject table found on grade-card page. First 500 chars:\n{}",
                html.substring(0, Math.min(500, html.length())));
        return null;
    }
 
    /** Safely reads a numeric marks cell; returns 0 if the column is absent or non-numeric (e.g. "-" for absent). */
    private double parseMarksCell(Elements cells, int colIndex) {
        if (colIndex < 0 || cells.size() <= colIndex) return 0;
        try {
            return Double.parseDouble(cells.get(colIndex).text().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
 
    private int[] mapColumns(Element headerRow) {
        int[] cols = {-1, -1, -1, -1, -1, -1, -1}; // code, name, credits, grade, internal, external, total
        Elements headers = headerRow.select("th");
        if (headers.isEmpty()) headers = headerRow.select("td");
 
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i).text().trim().toLowerCase();
            if ((h.contains("code") || h.contains("course") || h.contains("subject")) && !h.contains("name")) cols[0] = i;
            else if (h.contains("name") || h.contains("title"))                        cols[1] = i;
            else if (h.contains("credit") || h.contains("cts"))                        cols[2] = i;
            else if (h.contains("grade") && !h.contains("point"))                      cols[3] = i;
            else if (h.contains("internal"))                                           cols[4] = i;
            else if (h.contains("external") || h.contains("university"))               cols[5] = i;
            else if (h.contains("total"))                                              cols[6] = i;
        }
        return cols;
    }
 
    private String get(String url) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build();
        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful()) {
                throw new IOException("KTU portal returned HTTP " + r.code() + " for " + url);
            }
            return r.body() != null ? r.body().string() : "";
        }
    }
 
    /**
     * Fixed CookieJar: preserves session cookies across requests and redirects.
     */
    private static class InMemoryCookieJar implements CookieJar {
        private final Map<String, Map<String, Cookie>> store = new ConcurrentHashMap<>();
 
        @Override
        public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            if (cookies == null || cookies.isEmpty()) return;
            Map<String, Cookie> domainCookies = store.computeIfAbsent(url.host(), k -> new HashMap<>());
            for (Cookie c : cookies) {
                domainCookies.put(c.name(), c);
            }
        }
 
        @Override
        public synchronized List<Cookie> loadForRequest(HttpUrl url) {
            Map<String, Cookie> domainCookies = store.get(url.host());
            if (domainCookies == null || domainCookies.isEmpty()) return Collections.emptyList();
            return new ArrayList<>(domainCookies.values());
        }
    }
}