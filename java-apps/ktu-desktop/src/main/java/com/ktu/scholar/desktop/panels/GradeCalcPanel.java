package com.ktu.scholar.desktop.panels;

import com.ktu.scholar.core.model.SubjectResult;
import com.ktu.scholar.core.service.GradeCalculatorService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive GPA calculator using the KTU 2024 grading scheme.
 * Users add rows (subject name, credits, grade) and click Calculate.
 */
public class GradeCalcPanel extends BasePanel {

    private static final String[] GRADE_OPTIONS =
            {"S", "A+", "A", "B+", "B", "C+", "C", "D", "F"};

    private DefaultTableModel tableModel;
    private JLabel sgpaLabel;
    private JLabel percentageLabel;
    private JLabel classLabel;

    public GradeCalcPanel() {
        super("GPA Calculator  (KTU 2024 Scheme)");
        buildContent();
    }

    private void buildContent() {
        JPanel body = new JPanel();
        body.setLayout(new BorderLayout(0, 20));

        // ── Grade entry table ─────────────────────────────────────────
        String[] cols = {"Subject Name", "Credits", "Grade"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return true; }
        };
        // Seed with 6 empty rows
        for (int i = 0; i < 6; i++) tableModel.addRow(new Object[]{"", 4, "B+"});

        JTable table = new JTable(tableModel);
        table.setBackground(CARD_BG);
        table.setForeground(FG);
        table.setFont(BODY_FONT);
        table.setRowHeight(32);
        table.getTableHeader().setBackground(new Color(0x1E3A5F));
        table.getTableHeader().setForeground(MUTED);
        table.setGridColor(BORDER);
        table.setSelectionBackground(BORDER);

        // Grade column: dropdown
        JComboBox<String> gradeCombo = new JComboBox<>(GRADE_OPTIONS);
        gradeCombo.setBackground(new Color(0x1E3A5F));
        gradeCombo.setForeground(FG);
        table.getColumnModel().getColumn(2)
                .setCellEditor(new DefaultCellEditor(gradeCombo));

        JScrollPane tableScroll = new JScrollPane(table);
        styleScrollPane(tableScroll);

        // ── Bottom: buttons + results ─────────────────────────────────
        JPanel controls = new JPanel(new BorderLayout(16, 0));
        controls.setOpaque(false);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        buttons.setOpaque(false);

        JButton addRow = createPrimaryButton("+ Add Subject");
        addRow.addActionListener(e -> tableModel.addRow(new Object[]{"", 4, "B+"}));

        JButton calcBtn = new JButton("Calculate SGPA");
        calcBtn.setBackground(SUCCESS);
        calcBtn.setForeground(Color.BLACK);
        calcBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        calcBtn.setBorderPainted(false);
        calcBtn.setFocusPainted(false);
        calcBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        calcBtn.addActionListener(e -> calculate());

        JButton clearBtn = new JButton("Clear");
        clearBtn.setBackground(BORDER);
        clearBtn.setForeground(MUTED);
        clearBtn.setFont(BODY_FONT);
        clearBtn.setBorderPainted(false);
        clearBtn.setFocusPainted(false);
        clearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearBtn.addActionListener(e -> tableModel.setRowCount(0));

        buttons.add(addRow);
        buttons.add(calcBtn);
        buttons.add(clearBtn);

        // Result cards
        JPanel results = new JPanel(new GridLayout(1, 3, 16, 0));
        results.setOpaque(false);

        sgpaLabel      = resultLabel("–.––");
        percentageLabel = resultLabel("--%");
        classLabel      = resultLabel("—");

        results.add(createCard("SGPA",           sgpaLabel));
        results.add(createCard("Percentage",     percentageLabel));
        results.add(createCard("Classification", classLabel));

        controls.add(buttons, BorderLayout.WEST);
        controls.add(results, BorderLayout.CENTER);

        // ── Grading scheme reference ──────────────────────────────────
        JPanel schemePanel = buildSchemePanel();

        body.add(tableScroll, BorderLayout.CENTER);
        body.add(controls,    BorderLayout.SOUTH);

        // Add scheme as a collapsible section below
        JPanel outer = new JPanel(new BorderLayout(0, 16));
        outer.setOpaque(false);
        outer.add(body, BorderLayout.CENTER);

        JButton schemeToggle = new JButton("Show Grading Scheme Reference ▼");
        schemeToggle.setBackground(BORDER);
        schemeToggle.setForeground(MUTED);
        schemeToggle.setFont(SMALL_FONT);
        schemeToggle.setBorderPainted(false);
        schemeToggle.setFocusPainted(false);
        schemeToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        schemePanel.setVisible(false);
        schemeToggle.addActionListener(e -> {
            boolean vis = !schemePanel.isVisible();
            schemePanel.setVisible(vis);
            schemeToggle.setText(vis ? "Hide Grading Scheme Reference ▲" : "Show Grading Scheme Reference ▼");
        });

        outer.add(schemeToggle, BorderLayout.SOUTH);
        getBodyPanel().setLayout(new BorderLayout(0, 16));
        getBodyPanel().add(outer,       BorderLayout.CENTER);
        getBodyPanel().add(schemePanel, BorderLayout.SOUTH);
    }

    private void calculate() {
        List<SubjectResult> subjects = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String name    = String.valueOf(tableModel.getValueAt(i, 0)).trim();
            String credStr = String.valueOf(tableModel.getValueAt(i, 1)).trim();
            String grade   = String.valueOf(tableModel.getValueAt(i, 2)).trim();
            if (name.isEmpty() && credStr.isEmpty()) continue;
            try {
                SubjectResult s = new SubjectResult();
                s.setSubjectName(name);
                s.setCredits(Double.parseDouble(credStr));
                s.setGrade(grade);
                s.setGradePoint(GradeCalculatorService.gradePoint(grade));
                subjects.add(s);
            } catch (NumberFormatException ignored) {}
        }

        if (subjects.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add at least one subject with valid credits.");
            return;
        }

        double sgpa = GradeCalculatorService.calculateSgpa(subjects);
        double pct  = GradeCalculatorService.cgpaToPercentage(sgpa);
        String cls  = GradeCalculatorService.classify(sgpa);

        sgpaLabel.setText(String.format("%.2f", sgpa));
        percentageLabel.setText(String.format("%.1f%%", pct));
        classLabel.setText(cls);
    }

    private JPanel buildSchemePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        JLabel title = new JLabel("KTU 2024 Grading Scheme  |  Percentage = (CGPA − 0.5) × 10");
        title.setFont(BODY_FONT);
        title.setForeground(ACCENT);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        panel.add(title);

        JPanel grid = new JPanel(new GridLayout(0, 4, 12, 6));
        grid.setOpaque(false);
        for (var entry : GradeCalculatorService.getGradingScheme()) {
            grid.add(schemeCell(entry.get("grade") + " → " + entry.get("gradePoint"),
                                entry.get("percentageRange") + "  " + entry.get("description")));
        }
        panel.add(grid);
        return panel;
    }

    private JPanel schemeCell(String top, String bottom) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        JLabel t = new JLabel(String.valueOf(top));
        t.setFont(new Font("Segoe UI", Font.BOLD, 13));
        t.setForeground(ACCENT);
        JLabel b = new JLabel(String.valueOf(bottom));
        b.setFont(SMALL_FONT);
        b.setForeground(MUTED);
        p.add(t, BorderLayout.NORTH);
        p.add(b, BorderLayout.CENTER);
        return p;
    }

    private JLabel resultLabel(String text) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 32));
        lbl.setForeground(ACCENT);
        return lbl;
    }
}
