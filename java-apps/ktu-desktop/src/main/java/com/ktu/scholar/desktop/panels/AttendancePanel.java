package com.ktu.scholar.desktop.panels;

import com.ktu.scholar.core.db.*;
import com.ktu.scholar.core.model.*;
import com.ktu.scholar.core.service.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Attendance panel with per-subject progress bars, colour-coded status chips,
 * and recommendations.
 */
public class AttendancePanel extends BasePanel {

    private final DatabaseHelper db;
    private JLabel overallLabel;
    private JProgressBar overallBar;
    private JPanel subjectsPanel;

    public AttendancePanel(DatabaseHelper db) {
        super("Attendance");
        this.db = db;
        buildContent();
        loadFromCache();
    }

    private void buildContent() {
        JPanel body = getBodyPanel();
        body.setLayout(new BorderLayout(0, 20));

        // ── Overall summary ───────────────────────────────────────────
        JPanel summaryCard = new JPanel(new BorderLayout(12, 8));
        summaryCard.setBackground(CARD_BG);
        summaryCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)));

        JLabel summaryTitle = new JLabel("Overall Attendance");
        summaryTitle.setFont(SMALL_FONT);
        summaryTitle.setForeground(MUTED);

        overallLabel = new JLabel("-- %");
        overallLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
        overallLabel.setForeground(SUCCESS);

        overallBar = new JProgressBar(0, 100);
        overallBar.setValue(0);
        overallBar.setStringPainted(false);
        overallBar.setBackground(BORDER);
        overallBar.setForeground(SUCCESS);
        overallBar.setPreferredSize(new Dimension(0, 10));

        summaryCard.add(summaryTitle, BorderLayout.NORTH);
        summaryCard.add(overallLabel, BorderLayout.CENTER);
        summaryCard.add(overallBar,   BorderLayout.SOUTH);

        // ── Subject list ──────────────────────────────────────────────
        subjectsPanel = new JPanel();
        subjectsPanel.setLayout(new BoxLayout(subjectsPanel, BoxLayout.Y_AXIS));
        subjectsPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(subjectsPanel);
        styleScrollPane(scroll);

        body.add(summaryCard, BorderLayout.NORTH);
        body.add(scroll,      BorderLayout.CENTER);

        // Sync button
        JButton syncBtn = createPrimaryButton("Sync from EzyGo");
        syncBtn.addActionListener(e -> syncAttendance());
        addToHeader(syncBtn);
    }

    private void loadFromCache() {
        SwingWorker<List<SubjectAttendance>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SubjectAttendance> doInBackground() throws Exception {
                var cached = new AttendanceCacheRepository(db.getConnection()).get();
                return cached != null ? cached.subjects : null;
            }

            @Override
            protected void done() {
                try {
                    var list = get();
                    if (list != null && !list.isEmpty()) renderSubjects(list);
                    else showEmptyState();
                } catch (Exception ignored) { showEmptyState(); }
            }
        };
        worker.execute();
    }

    private void syncAttendance() {
        setLoading(true);
        SwingWorker<List<SubjectAttendance>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SubjectAttendance> doInBackground() throws Exception {
                Profile p = new ProfileRepository(db.getConnection()).getProfile();
                if (p == null || !p.hasEzygoCredentials())
                    throw new Exception("EzyGo credentials not configured. Go to Settings.");
                var att = new EzygoService().fetchAttendance(p.getRegisterNumber(), p.getEzygoPassword());
                new AttendanceCacheRepository(db.getConnection()).save(p.getRegisterNumber(), att);
                return att;
            }

            @Override
            protected void done() {
                setLoading(false);
                try { renderSubjects(get()); }
                catch (Exception e) {
                    JOptionPane.showMessageDialog(AttendancePanel.this,
                            e.getMessage(), "Sync Failed", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void renderSubjects(List<SubjectAttendance> subjects) {
        double total = subjects.stream().mapToInt(SubjectAttendance::getTotalClasses).sum();
        double attended = subjects.stream().mapToInt(SubjectAttendance::getAttendedClasses).sum();
        double overall = total > 0 ? attended / total * 100 : 0;

        Color overallColor = overall >= 75 ? SUCCESS : overall >= 60 ? WARNING : DANGER;
        overallLabel.setText(String.format("%.1f%%", overall));
        overallLabel.setForeground(overallColor);
        overallBar.setValue((int) overall);
        overallBar.setForeground(overallColor);

        subjectsPanel.removeAll();
        for (SubjectAttendance s : subjects) {
            subjectsPanel.add(createSubjectRow(s));
            subjectsPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        }
        subjectsPanel.revalidate();
        subjectsPanel.repaint();
    }

    private JPanel createSubjectRow(SubjectAttendance s) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(statusColor(s.getStatus()), 1, true),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // Top row: name + status chip + percentage
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);

        JLabel name = new JLabel(s.getSubjectCode() + "  " + s.getSubjectName());
        name.setFont(BODY_FONT);
        name.setForeground(FG);

        JLabel chip = new JLabel(s.getStatus().name());
        chip.setFont(SMALL_FONT);
        chip.setForeground(statusColor(s.getStatus()));
        chip.setBorder(BorderFactory.createLineBorder(statusColor(s.getStatus()), 1, true));
        chip.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        JLabel pct = new JLabel(String.format("%.1f%%  (%d/%d)",
                s.getPercentage(), s.getAttendedClasses(), s.getTotalClasses()));
        pct.setFont(BODY_FONT);
        pct.setForeground(statusColor(s.getStatus()));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(chip);
        right.add(pct);

        top.add(name,  BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);

        // Progress bar
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue((int) s.getPercentage());
        bar.setForeground(statusColor(s.getStatus()));
        bar.setBackground(BORDER);
        bar.setStringPainted(false);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));

        // Recommendation
        JLabel rec = new JLabel("<html><body style='width:500px;color:#8BA3C7'>"
                + s.getRecommendation() + "</body></html>");
        rec.setFont(SMALL_FONT);
        rec.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        card.add(top);
        card.add(Box.createRigidArea(new Dimension(0, 6)));
        card.add(bar);
        card.add(rec);

        // Condonation steps (collapsed by default)
        if (s.getStatus() == SubjectAttendance.AttendanceStatus.CONDONATION
                && !s.getCondonationSteps().isEmpty()) {
            JPanel steps = new JPanel();
            steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
            steps.setBackground(new Color(0x1A1A2E));
            steps.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
            steps.setVisible(false);

            for (String step : s.getCondonationSteps()) {
                JLabel l = new JLabel("<html>" + step + "</html>");
                l.setFont(SMALL_FONT);
                l.setForeground(WARNING);
                l.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
                steps.add(l);
            }

            JButton toggle = new JButton("Show Condonation Steps ▼");
            toggle.setBackground(new Color(0x1E3A5F));
            toggle.setForeground(WARNING);
            toggle.setFont(SMALL_FONT);
            toggle.setBorderPainted(false);
            toggle.setFocusPainted(false);
            toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
            toggle.addActionListener(e -> {
                boolean vis = !steps.isVisible();
                steps.setVisible(vis);
                toggle.setText(vis ? "Hide Condonation Steps ▲" : "Show Condonation Steps ▼");
            });

            card.add(toggle);
            card.add(steps);
        }

        return card;
    }

    private Color statusColor(SubjectAttendance.AttendanceStatus status) {
        return switch (status) {
            case SAFE         -> SUCCESS;
            case WARNING      -> WARNING;
            case DANGER       -> DANGER;
            case CONDONATION  -> new Color(0xFF6B35);
        };
    }

    private void showEmptyState() {
        subjectsPanel.removeAll();
        subjectsPanel.add(emptyState("No attendance data. Click 'Sync from EzyGo'."));
        subjectsPanel.revalidate();
        subjectsPanel.repaint();
    }
}
