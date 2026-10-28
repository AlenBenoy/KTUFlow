package com.ktu.scholar.desktop.panels;

import javax.swing.*;
import java.awt.*;
import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Study materials browser — shows curated links to ktunotes.in and ktuspecial.in,
 * filterable by semester and type (Notes / PYQ).
 *
 * Each card has:
 *   - "Open →"      opens a subject-specific page (or a site search for that subject
 *                   when no exact page URL is set).
 *   - "Download ↓"  only shown when a direct file URL is set; saves the file into
 *                   the user's Downloads folder.
 */
public class MaterialsPanel extends BasePanel {

    private static final String KTUNOTES_HOME  = "https://www.ktunotes.in/ktu-2024-scheme-notes/";
    private static final String KTUSPECIAL_HOME = "https://ktuspecial.in/ktunotes/ktu2024schemenotes";

    /**
     * @param url     exact page for this material, or null to auto-generate a site search
     * @param fileUrl direct link to a downloadable file (PDF/ZIP...), or null if none
     */
    private record Material(String title, String subjectCode, int semester,
                            String type, String url, String fileUrl, String source) {

        /**
         * The page this card opens: the exact URL if set, otherwise Google's
         * "I'm Feeling Lucky" (btnI=1) for a site-specific search, which jumps
         * straight to the top result instead of showing the results page.
         *
         * If Google ever shows a "Redirect Notice" instead, swap the return line for:
         *   "https://duckduckgo.com/?q=" + URLEncoder.encode("\\" + q, StandardCharsets.UTF_8)
         */
        String targetUrl() {
            if (url != null && !url.isBlank()) return url;
            String site = "KTU Special".equals(source) ? "ktuspecial.in" : "ktunotes.in";
            String q = "site:" + site + " " + subjectCode + " " + title + " " + type;
            return "https://www.google.com/search?btnI=1&q="
                    + URLEncoder.encode(q, StandardCharsets.UTF_8);
        }
    }

    // Fill in `url` (exact page) and/or `fileUrl` (direct file link) as you find them.
    // Leave both null to use the automatic subject search.
    private static final Material[] MATERIALS = {
        new Material("Calculus and its Applications Notes", "MAT101", 1, "Notes", null, null, "KTU Notes"),
        new Material("Engineering Physics Notes", "PHT110", 1, "Notes", null, null, "KTU Notes"),
        new Material("Engineering Physics PYQs", "PHT110", 1, "PYQ", null, null, "KTU Special"),
        new Material("Problem Solving using Python Notes", "CST102", 2, "Notes", null, null, "KTU Notes"),
        new Material("Basics of Electrical & Electronics Engg Notes", "EST130", 2, "Notes", null, null, "KTU Special"),
        new Material("Data Structures Notes", "CST301", 3, "Notes", null, null, "KTU Notes"),
        new Material("Data Structures PYQs", "CST301", 3, "PYQ", null, null, "KTU Special"),
        new Material("Discrete Mathematical Structures Notes", "MAT203", 3, "Notes", null, null, "KTU Notes"),
        new Material("OOP using Java Notes", "CST307", 3, "Notes", null, null, "KTU Special"),
        new Material("Logic System Design Notes", "CST303", 3, "Notes", null, null, "KTU Notes"),
        new Material("Operating Systems Notes", "CST402", 4, "Notes", null, null, "KTU Special"),
        new Material("Operating Systems PYQs", "CST402", 4, "PYQ", null, null, "KTU Special"),
        new Material("DBMS Notes", "CST404", 4, "Notes", null, null, "KTU Notes"),
        new Material("Computer Networks Notes", "CST305", 5, "Notes", null, null, "KTU Notes"),
        new Material("Compiler Design Notes", "CST502", 5, "Notes", null, null, "KTU Special"),
        new Material("Machine Learning Notes", "CST654", 6, "Notes", null, null, "KTU Special"),
        new Material("Artificial Intelligence Notes", "CST702", 7, "Notes", null, null, "KTU Notes"),
    };

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final Pattern CONTENT_DISPOSITION_NAME =
            Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    private JPanel resultsPanel;
    private JComboBox<String> semFilter;
    private JComboBox<String> typeFilter;

    public MaterialsPanel() {
        super("Study Materials");
        buildContent();
        applyFilter();
    }

    private void buildContent() {
        JPanel body = getBodyPanel();
        body.setLayout(new BorderLayout(0, 16));

        // ── Filter bar ────────────────────────────────────────────────
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        filterBar.setOpaque(false);

        semFilter = new JComboBox<>(new String[]{"All Semesters", "1", "2", "3", "4", "5", "6", "7", "8"});
        semFilter.setBackground(new Color(0x1E3A5F));
        semFilter.setForeground(FG);
        semFilter.setFont(BODY_FONT);
        semFilter.addActionListener(e -> applyFilter());

        typeFilter = new JComboBox<>(new String[]{"All Types", "Notes", "PYQ"});
        typeFilter.setBackground(new Color(0x1E3A5F));
        typeFilter.setForeground(FG);
        typeFilter.setFont(BODY_FONT);
        typeFilter.addActionListener(e -> applyFilter());

        JLabel semLbl  = new JLabel("Semester:");
        semLbl.setForeground(MUTED); semLbl.setFont(BODY_FONT);
        JLabel typeLbl = new JLabel("Type:");
        typeLbl.setForeground(MUTED); typeLbl.setFont(BODY_FONT);

        filterBar.add(semLbl);
        filterBar.add(semFilter);
        filterBar.add(Box.createHorizontalStrut(8));
        filterBar.add(typeLbl);
        filterBar.add(typeFilter);

        // ── Source banners ────────────────────────────────────────────
        JPanel banners = new JPanel(new GridLayout(1, 2, 16, 0));
        banners.setOpaque(false);
        banners.add(sourceBanner("ktunotes.in", "KTU Notes — Module-wise study materials", KTUNOTES_HOME));
        banners.add(sourceBanner("ktuspecial.in", "KTU Special — Notes & Previous Year Questions", KTUSPECIAL_HOME));

        // ── Results grid ──────────────────────────────────────────────
        resultsPanel = new JPanel(new GridLayout(0, 2, 12, 12));
        resultsPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(resultsPanel);
        styleScrollPane(scroll);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(filterBar, BorderLayout.NORTH);
        top.add(banners,   BorderLayout.CENTER);

        body.add(top,    BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
    }

    private void applyFilter() {
        String sem  = (String) semFilter.getSelectedItem();
        String type = (String) typeFilter.getSelectedItem();

        resultsPanel.removeAll();
        for (Material m : MATERIALS) {
            boolean semMatch  = "All Semesters".equals(sem) || String.valueOf(m.semester()).equals(sem);
            boolean typeMatch = "All Types".equals(type) || m.type().equals(type);
            if (semMatch && typeMatch) resultsPanel.add(materialCard(m));
        }
        if (resultsPanel.getComponentCount() == 0) {
            resultsPanel.setLayout(new BorderLayout());
            resultsPanel.add(emptyState("No materials found for this filter."), BorderLayout.CENTER);
        } else {
            resultsPanel.setLayout(new GridLayout(0, 2, 12, 12));
        }
        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private JPanel materialCard(Material m) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        JLabel title = new JLabel("<html><body style='width:200px'>" + m.title() + "</body></html>");
        title.setFont(BODY_FONT);
        title.setForeground(FG);

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        meta.setOpaque(false);

        JLabel semChip  = chip("S" + m.semester(), BLUE);
        JLabel typeChip = chip(m.type(), "PYQ".equals(m.type()) ? WARNING : SUCCESS);
        JLabel codeChip = chip(m.subjectCode(), MUTED);
        JLabel srcChip  = chip(m.source(), MUTED);

        meta.add(semChip); meta.add(typeChip); meta.add(codeChip); meta.add(srcChip);

        // Buttons: Open (always) + Download (only when a direct file URL exists)
        JPanel buttons = new JPanel(new GridLayout(1, 0, 8, 0));
        buttons.setOpaque(false);

        JButton openBtn = styledButton("Open →");
        openBtn.addActionListener(e -> openUrl(m.targetUrl()));
        buttons.add(openBtn);

        if (m.fileUrl() != null && !m.fileUrl().isBlank()) {
            JButton dlBtn = styledButton("Download ↓");
            dlBtn.addActionListener(e -> downloadFile(m.fileUrl(), m.targetUrl(), dlBtn));
            buttons.add(dlBtn);
        }

        card.add(title,   BorderLayout.NORTH);
        card.add(meta,    BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    private JPanel sourceBanner(String name, String description, String url) {
        JPanel banner = new JPanel(new BorderLayout(8, 0));
        banner.setBackground(new Color(0x1E3A5F));
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT, 1, true),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(HEADER_FONT);
        nameLbl.setForeground(ACCENT);

        JLabel descLbl = new JLabel(description);
        descLbl.setFont(SMALL_FONT);
        descLbl.setForeground(MUTED);

        JButton visit = styledButton("Visit Site");
        visit.setFont(SMALL_FONT);
        visit.addActionListener(e -> openUrl(url));

        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.add(nameLbl, BorderLayout.NORTH);
        text.add(descLbl, BorderLayout.CENTER);

        banner.add(text,  BorderLayout.CENTER);
        banner.add(visit, BorderLayout.EAST);
        return banner;
    }

    private JButton styledButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(ACCENT);
        b.setForeground(new Color(0x0A1628));
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JLabel chip(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(SMALL_FONT);
        l.setForeground(color);
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1, true),
                BorderFactory.createEmptyBorder(1, 6, 1, 6)));
        return l;
    }

    // ── Browser ──────────────────────────────────────────────────────

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                // Windows fallback when Desktop.browse() isn't available
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Could not open the website:\n" + url + "\n\n" + e.getMessage());
        }
    }

    // ── Download to the user's Downloads folder ──────────────────────

    /**
     * Downloads {@code fileUrl} into ~/Downloads on a background thread.
     * If the link turns out to be a web page (or the server refuses),
     * the user is offered to open {@code fallbackPageUrl} in the browser instead.
     */
    private void downloadFile(String fileUrl, String fallbackPageUrl, JButton btn) {
        String originalText = btn.getText();
        btn.setEnabled(false);
        btn.setText("Downloading…");

        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                HttpRequest req = HttpRequest.newBuilder(URI.create(fileUrl))
                        .header("User-Agent", "Mozilla/5.0 KTUScholarDesktop")
                        .timeout(Duration.ofMinutes(2))
                        .GET()
                        .build();
                HttpResponse<InputStream> res =
                        HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());

                try (InputStream in = res.body()) {
                    if (res.statusCode() != 200) {
                        throw new IOException("Server returned HTTP " + res.statusCode());
                    }
                    String contentType = res.headers().firstValue("Content-Type")
                            .orElse("").toLowerCase();
                    if (contentType.startsWith("text/html")) {
                        throw new IOException("This link is a web page, not a downloadable file.");
                    }

                    Path dir = Path.of(System.getProperty("user.home"), "Downloads");
                    Files.createDirectories(dir);
                    Path target = uniquePath(dir, fileNameFrom(res));
                    Files.copy(in, target);
                    return target;
                }
            }

            @Override
            protected void done() {
                btn.setEnabled(true);
                btn.setText(originalText);
                try {
                    Path saved = get();
                    int choice = JOptionPane.showOptionDialog(MaterialsPanel.this,
                            "Saved to:\n" + saved, "Download complete",
                            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                            null, new String[]{"Open folder", "OK"}, "OK");
                    if (choice == 0) {
                        Desktop.getDesktop().open(saved.getParent().toFile());
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    int choice = JOptionPane.showOptionDialog(MaterialsPanel.this,
                            "Download failed: " + cause.getMessage()
                                    + "\n\nOpen the page in your browser instead?",
                            "Download failed", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
                            null, new String[]{"Open in browser", "Cancel"}, "Open in browser");
                    if (choice == 0) openUrl(fallbackPageUrl);
                }
            }
        }.execute();
    }

    /** Picks a file name from Content-Disposition, else from the URL path. */
    private static String fileNameFrom(HttpResponse<?> res) {
        String name = res.headers().firstValue("Content-Disposition")
                .map(h -> {
                    Matcher m = CONTENT_DISPOSITION_NAME.matcher(h);
                    return m.find() ? URLDecoder.decode(m.group(1).trim(), StandardCharsets.UTF_8) : null;
                })
                .orElse(null);

        if (name == null || name.isBlank()) {
            String path = res.uri().getPath();
            name = URLDecoder.decode(path.substring(path.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return name.isEmpty() ? "download" : name;
    }

    /** Returns a path that doesn't overwrite an existing file: "name (1).pdf", "name (2).pdf"... */
    private static Path uniquePath(Path dir, String fileName) {
        Path p = dir.resolve(fileName);
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext  = dot > 0 ? fileName.substring(dot) : "";
        for (int i = 1; Files.exists(p); i++) {
            p = dir.resolve(base + " (" + i + ")" + ext);
        }
        return p;
    }
}