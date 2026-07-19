package com.ktu.scholar.desktop.panels;

import javax.swing.*;
import java.awt.*;
import java.awt.Desktop;
import java.net.URI;

/**
 * Study materials browser — shows curated links to ktunotes.in and ktuspecial.in,
 * filterable by semester and type (Notes / PYQ).
 */
public class MaterialsPanel extends BasePanel {

    private record Material(String title, String subjectCode, int semester,
                            String type, String url, String source) {}

    private static final Material[] MATERIALS = {
        new Material("Calculus and its Applications Notes", "MAT101", 1, "Notes", "https://www.ktunotes.in/ktu-2024-scheme-notes/", "KTU Notes"),
        new Material("Engineering Physics Notes", "PHT110", 1, "Notes", "https://www.ktunotes.in/ktu-2024-scheme-notes/", "KTU Notes"),
        new Material("Engineering Physics PYQs", "PHT110", 1, "PYQ", "https://ktuspecial.in/ktunotes/ktu2024schemenotes", "KTU Special"),
        new Material("Problem Solving using Python Notes", "CST102", 2, "Notes", "https://www.ktunotes.in/ktu-2024-scheme-notes/", "KTU Notes"),
        new Material("Basics of Electrical & Electronics Engg Notes", "EST130", 2, "Notes", "https://ktuspecial.in/ktunotes/ktu2024schemenotes", "KTU Special"),
        new Material("Data Structures Notes", "CST301", 3, "Notes", "https://www.ktunotes.in/ktu-2024-scheme-notes/", "KTU Notes"),
        new Material("Data Structures PYQs", "CST301", 3, "PYQ", "https://ktuspecial.in/ktunotes/ktu2024schemenotes", "KTU Special"),
        new Material("Discrete Mathematical Structures Notes", "MAT203", 3, "Notes", "https://www.ktunotes.in/ktu-2024-scheme-notes/", "KTU Notes"),
        new Material("OOP using Java Notes", "CST307", 3, "Notes", "https://ktuspecial.in/ktunotes/ktu2024schemenotes", "KTU Special"),
        new Material("Logic System Design Notes", "CST303", 3, "Notes", "https://www.ktunotes.in/ktu-2024-scheme-notes/", "KTU Notes"),
        new Material("Operating Systems Notes", "CST402", 4, "Notes", "https://ktuspecial.in/ktunotes/ktu2024schemenotes", "KTU Special"),
        new Material("Operating Systems PYQs", "CST402", 4, "PYQ", "https://ktuspecial.in/ktunotes/ktu2024schemenotes", "KTU Special"),
        new Material("DBMS Notes", "CST404", 4, "Notes", "https://www.ktunotes.in/ktu-2024-scheme-notes/", "KTU Notes"),
        new Material("Computer Networks Notes", "CST305", 5, "Notes", "https://www.ktunotes.in/ktu-2024-scheme-notes/", "KTU Notes"),
        new Material("Compiler Design Notes", "CST502", 5, "Notes", "https://ktuspecial.in/ktunotes/ktu2024schemenotes", "KTU Special"),
        new Material("Machine Learning Notes", "CST654", 6, "Notes", "https://ktuspecial.in/ktunotes/ktu2024schemenotes", "KTU Special"),
        new Material("Artificial Intelligence Notes", "CST702", 7, "Notes", "https://www.ktunotes.in/ktu-2024-scheme-notes/", "KTU Notes"),
    };

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
        banners.add(sourceBanner("ktunotes.in", "KTU Notes — Module-wise study materials",
                "https://www.ktunotes.in/ktu-2024-scheme-notes/"));
        banners.add(sourceBanner("ktuspecial.in", "KTU Special — Notes & Previous Year Questions",
                "https://ktuspecial.in/ktunotes/ktu2024schemenotes"));

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

        JLabel semChip = chip("S" + m.semester(), BLUE);
        JLabel typeChip = chip(m.type(), "PYQ".equals(m.type()) ? WARNING : SUCCESS);
        JLabel srcChip = chip(m.source(), MUTED);

        meta.add(semChip); meta.add(typeChip); meta.add(srcChip);

        JButton openBtn = new JButton("Open →");
        openBtn.setBackground(ACCENT);
        openBtn.setForeground(new Color(0x0A1628));
        openBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        openBtn.setBorderPainted(false);
        openBtn.setFocusPainted(false);
        openBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        openBtn.addActionListener(e -> openUrl(m.url()));

        card.add(title,   BorderLayout.NORTH);
        card.add(meta,    BorderLayout.CENTER);
        card.add(openBtn, BorderLayout.SOUTH);
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

        JButton visit = new JButton("Visit Site");
        visit.setBackground(ACCENT);
        visit.setForeground(new Color(0x0A1628));
        visit.setFont(SMALL_FONT);
        visit.setBorderPainted(false);
        visit.setFocusPainted(false);
        visit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        visit.addActionListener(e -> openUrl(url));

        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.add(nameLbl, BorderLayout.NORTH);
        text.add(descLbl, BorderLayout.CENTER);

        banner.add(text,  BorderLayout.CENTER);
        banner.add(visit, BorderLayout.EAST);
        return banner;
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

    private void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not open browser: " + e.getMessage());
        }
    }
}
