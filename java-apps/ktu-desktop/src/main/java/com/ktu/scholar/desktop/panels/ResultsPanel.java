package com.ktu.scholar.desktop.panels;

import com.ktu.scholar.core.db.*;
import com.ktu.scholar.core.model.*;
import com.ktu.scholar.core.service.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays semester-wise KTU results with a semester selection dropdown.
 * Shows CGPA / SGPA as hero metrics at the top.
 */
public class ResultsPanel extends BasePanel {

    private final DatabaseHelper db;
    private JLabel cgpaHeroTitle;
    private JLabel cgpaHero;
    private JLabel percentageLabel;
    private JLabel classLabel;
    private JPanel semestersPanel;
    private JComboBox<String> semesterSelector;

    private List<SemesterResult> cachedSemesters = new ArrayList<>();

    public ResultsPanel(DatabaseHelper db) {
        super("Academic Results");
        this.db = db;
        buildContent();
        loadFromCache();
    }

    private void buildContent() {
        JPanel body = getBodyPanel();
        body.setLayout(new BorderLayout(0, 20));

        // ── Top controls (Semester Dropdown + Sync) ────────────────────
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        topBar.setOpaque(false);

        JLabel selectLbl = new JLabel("Select Semester:");
        selectLbl.setFont(BODY_FONT);
        selectLbl.setForeground(MUTED);

        semesterSelector = new JComboBox<>(new String[]{"All Semesters"});
        semesterSelector.setFont(BODY_FONT);
        semesterSelector.setBackground(new Color(0x1E3A5F));
        semesterSelector.setForeground(FG);
        semesterSelector.setPreferredSize(new Dimension(160, 36));
        semesterSelector.addActionListener(e -> applyFilter());

        topBar.add(selectLbl);
        topBar.add(semesterSelector);

        JButton syncBtn = createPrimaryButton("Sync from KTU Portal");
        syncBtn.setPreferredSize(new Dimension(180, 36));
        syncBtn.addActionListener(e -> syncResults());

        addToHeader(topBar);
        addToHeader(syncBtn);

        // ── Hero metrics row ──────────────────────────────────────────
        JPanel heroes = new JPanel(new GridLayout(1, 3, 16, 0));
        heroes.setOpaque(false);

        cgpaHeroTitle = new JLabel("CGPA", SwingConstants.CENTER);

        cgpaHero = new JLabel("–.––", SwingConstants.CENTER);
        cgpaHero.setFont(new Font("Segoe UI", Font.BOLD, 48));
        cgpaHero.setForeground(ACCENT);

        percentageLabel = new JLabel("--%", SwingConstants.CENTER);
        percentageLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
        percentageLabel.setForeground(SUCCESS);

        classLabel = new JLabel("—", SwingConstants.CENTER);
        classLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        classLabel.setForeground(FG);

        heroes.add(createCard("CGPA / SGPA", cgpaHero));
        heroes.add(createCard("Percentage", percentageLabel));
        heroes.add(createCard("Classification", classLabel));

        // ── Semester accordion panel ──────────────────────────────────
        semestersPanel = new JPanel();
        semestersPanel.setLayout(new BoxLayout(semestersPanel, BoxLayout.Y_AXIS));
        semestersPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(semestersPanel);
        styleScrollPane(scroll);

        body.add(heroes, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
    }

    private void loadFromCache() {
        SwingWorker<List<SemesterResult>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SemesterResult> doInBackground() throws Exception {
                var cached = new ResultsCacheRepository(db.getConnection()).get();
                return cached != null ? cached.semesters : null;
            }

            @Override
            protected void done() {
                try {
                    List<SemesterResult> sems = get();
                    if (sems != null && !sems.isEmpty()) {
                        updateData(sems);
                    } else {
                        showEmptyState();
                    }
                } catch (Exception ignored) { showEmptyState(); }
            }
        };
        worker.execute();
    }

    private void syncResults() {
        setLoading(true);
        SwingWorker<List<SemesterResult>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SemesterResult> doInBackground() throws Exception {
                Profile p = new ProfileRepository(db.getConnection()).getProfile();
                if (p == null || !p.hasKtuCredentials())
                    throw new Exception("KTU credentials not configured. Go to Settings.");
                var results = new KtuPortalService().fetchResults(
                        p.getRegisterNumber(), p.getKtuPassword());
                new ResultsCacheRepository(db.getConnection()).save(p.getRegisterNumber(), results);
                return results;
            }

            @Override
            protected void done() {
                setLoading(false);
                try { updateData(get()); }
                catch (Exception e) {
                    JOptionPane.showMessageDialog(ResultsPanel.this,
                            e.getMessage(), "Sync Failed", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void updateData(List<SemesterResult> sems) {
        this.cachedSemesters = sems != null ? sems : new ArrayList<>();

        // Update dropdown options
        semesterSelector.removeAllItems();
        semesterSelector.addItem("All Semesters");
        for (SemesterResult sem : cachedSemesters) {
            semesterSelector.addItem("Semester " + sem.getSemester());
        }

        applyFilter();
    }

    private void applyFilter() {
        if (cachedSemesters.isEmpty()) {
            showEmptyState();
            return;
        }

        String selected = (String) semesterSelector.getSelectedItem();
        if (selected == null || selected.equals("All Semesters")) {
            renderAllSemesters();
        } else {
            try {
                int semNum = Integer.parseInt(selected.replace("Semester ", "").trim());
                renderSingleSemester(semNum);
            } catch (Exception e) {
                renderAllSemesters();
            }
        }
    }

    private void renderAllSemesters() {
        double totalPoints = 0, totalCredits = 0;
        for (SemesterResult sem : cachedSemesters) {
            for (SubjectResult s : sem.getSubjects()) {
                totalPoints  += s.getCredits() * s.getGradePoint();
                totalCredits += s.getCredits();
            }
        }
        double cgpa = totalCredits > 0 ? Math.round(totalPoints / totalCredits * 100) / 100.0 : 0;
        double pct  = Math.max(0, Math.round((cgpa - 0.5) * 10 * 100) / 100.0);
        String cls  = GradeCalculatorService.classify(cgpa);

        cgpaHero.setText(String.format("%.2f", cgpa));
        percentageLabel.setText(String.format("%.1f%%", pct));
        classLabel.setText(cls);

        semestersPanel.removeAll();
        for (SemesterResult sem : cachedSemesters) {
            semestersPanel.add(createSemesterAccordion(sem, false));
            semestersPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        }
        semestersPanel.revalidate();
        semestersPanel.repaint();
    }

    private void renderSingleSemester(int semNum) {
        SemesterResult target = null;
        for (SemesterResult sem : cachedSemesters) {
            if (sem.getSemester() == semNum) {
                target = sem;
                break;
            }
        }

        if (target == null) {
            renderAllSemesters();
            return;
        }

        double sgpa = target.getSgpa();
        double pct  = Math.max(0, Math.round((sgpa - 0.5) * 10 * 100) / 100.0);
        String cls  = GradeCalculatorService.classify(sgpa);

        cgpaHero.setText(String.format("%.2f", sgpa));
        percentageLabel.setText(String.format("%.1f%%", pct));
        classLabel.setText(cls);

        semestersPanel.removeAll();
        semestersPanel.add(createSemesterAccordion(target, true));
        semestersPanel.revalidate();
        semestersPanel.repaint();
    }

    private JPanel createSemesterAccordion(SemesterResult sem, boolean forceExpanded) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(CARD_BG);
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CARD_BG);
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel semLabel = new JLabel("Semester " + sem.getSemester());
        semLabel.setFont(HEADER_FONT);
        semLabel.setForeground(FG);

        JLabel sgpaLabel = new JLabel(String.format("SGPA: %.2f", sem.getSgpa()));
        sgpaLabel.setFont(BODY_FONT);
        sgpaLabel.setForeground(ACCENT);

        JLabel toggle = new JLabel(forceExpanded ? "▲" : "▼");
        toggle.setForeground(MUTED);

        header.add(semLabel, BorderLayout.WEST);
        header.add(sgpaLabel, BorderLayout.CENTER);
        header.add(toggle, BorderLayout.EAST);

        JTable table = buildSubjectTable(sem.getSubjects());
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setVisible(forceExpanded);
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        header.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                boolean vis = !tableScroll.isVisible();
                tableScroll.setVisible(vis);
                toggle.setText(vis ? "▲" : "▼");
                wrapper.revalidate();
                wrapper.repaint();
            }
        });

        wrapper.add(header);
        wrapper.add(tableScroll);
        return wrapper;
    }

    private JTable buildSubjectTable(List<SubjectResult> subjects) {
        String[] cols = {"Code", "Subject", "Credits", "Internal", "External", "Total", "Grade", "Result"};
        Object[][] data = new Object[subjects.size()][cols.length];
        for (int i = 0; i < subjects.size(); i++) {
            SubjectResult s = subjects.get(i);
            data[i] = new Object[]{
                s.getSubjectCode(), s.getSubjectName(), s.getCredits(),
                s.getInternalMarks() > 0 ? (int)s.getInternalMarks() : "—",
                s.getExternalMarks() > 0 ? (int)s.getExternalMarks() : "—",
                s.getTotalMarks()    > 0 ? (int)s.getTotalMarks()    : "—",
                s.getGrade(), s.getResult().name()
            };
        }
        JTable table = new JTable(data, cols);
        table.setBackground(CARD_BG);
        table.setForeground(FG);
        table.setFont(BODY_FONT);
        table.setRowHeight(30);
        table.getTableHeader().setBackground(new Color(0x1E3A5F));
        table.getTableHeader().setForeground(MUTED);
        table.setSelectionBackground(BORDER);
        table.setGridColor(BORDER);
        return table;
    }

    private void showEmptyState() {
        semestersPanel.removeAll();
        JLabel empty = emptyState("No results cached. Click 'Sync from KTU Portal' to fetch your results.");
        semestersPanel.add(empty);
        semestersPanel.revalidate();
        semestersPanel.repaint();
    }
}
