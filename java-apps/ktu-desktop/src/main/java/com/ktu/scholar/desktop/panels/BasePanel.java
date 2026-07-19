package com.ktu.scholar.desktop.panels;

import javax.swing.*;
import java.awt.*;

/**
 * Base class for all content panels.
 * Provides common styling, header, body layout, and utility helpers.
 */
public abstract class BasePanel extends JPanel {

    // ── Design tokens ─────────────────────────────────────────────────────
    protected static final Color BG       = new Color(0x0A1628);
    protected static final Color CARD_BG  = new Color(0x12213A);
    protected static final Color BORDER   = new Color(0x1E3A5F);
    protected static final Color ACCENT   = new Color(0xF59E0B);
    protected static final Color FG       = new Color(0xF0F4FF);
    protected static final Color MUTED    = new Color(0x8BA3C7);
    protected static final Color SUCCESS  = new Color(0x34D399);
    protected static final Color WARNING  = new Color(0xFBBF24);
    protected static final Color DANGER   = new Color(0xEF4444);
    protected static final Color BLUE     = new Color(0x3B82F6);

    protected static final Font TITLE_FONT  = new Font("Segoe UI", Font.BOLD,  22);
    protected static final Font HEADER_FONT = new Font("Segoe UI", Font.BOLD,  16);
    protected static final Font BODY_FONT   = new Font("Segoe UI", Font.PLAIN, 14);
    protected static final Font SMALL_FONT  = new Font("Segoe UI", Font.PLAIN, 12);

    private final JPanel headerPanel;
    private final JPanel bodyPanel;
    private JLabel titleLabel;
    private JLabel loadingLabel;

    public BasePanel(String title) {
        setLayout(new BorderLayout());
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        // Header
        headerPanel = new JPanel(new BorderLayout(12, 0));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        titleLabel = new JLabel(title);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(FG);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        loadingLabel = new JLabel("Syncing...");
        loadingLabel.setFont(SMALL_FONT);
        loadingLabel.setForeground(ACCENT);
        loadingLabel.setVisible(false);
        headerPanel.add(loadingLabel, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Body
        bodyPanel = new JPanel();
        bodyPanel.setOpaque(false);
        add(bodyPanel, BorderLayout.CENTER);
    }

    protected JPanel getBodyPanel() { return bodyPanel; }

    protected void addToHeader(JComponent comp) {
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(comp);
        headerPanel.add(right, BorderLayout.EAST);
    }

    protected void setLoading(boolean loading) {
        SwingUtilities.invokeLater(() -> loadingLabel.setVisible(loading));
    }

    // ── Card helpers ──────────────────────────────────────────────────────

    protected JPanel createCard(String label, JComponent content) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)));

        JLabel lbl = new JLabel(label.toUpperCase());
        lbl.setFont(SMALL_FONT);
        lbl.setForeground(MUTED);

        card.add(lbl,     BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    protected JPanel createSection(String title, JComponent content) {
        JPanel section = new JPanel(new BorderLayout(0, 8));
        section.setOpaque(false);

        JLabel lbl = new JLabel(title);
        lbl.setFont(HEADER_FONT);
        lbl.setForeground(FG);
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        section.add(lbl,     BorderLayout.NORTH);
        section.add(content, BorderLayout.CENTER);
        return section;
    }

    protected JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(ACCENT);
        btn.setForeground(new Color(0x0A1628));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    protected JLabel emptyState(String message) {
        JLabel lbl = new JLabel(message, SwingConstants.CENTER);
        lbl.setForeground(MUTED);
        lbl.setFont(BODY_FONT);
        lbl.setBorder(BorderFactory.createEmptyBorder(32, 0, 32, 0));
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    protected void styleScrollPane(JScrollPane sp) {
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createLineBorder(BORDER, 1, true));
        sp.getVerticalScrollBar().setUnitIncrement(12);
    }

    protected JPanel gradeChip(String grade) {
        JPanel chip = new JPanel();
        chip.setOpaque(true);
        chip.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        chip.setBackground(gradeColor(grade));

        JLabel lbl = new JLabel(grade);
        lbl.setForeground(Color.BLACK);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chip.add(lbl);
        return chip;
    }

    private Color gradeColor(String grade) {
        return switch (grade.toUpperCase()) {
            case "S"  -> ACCENT;
            case "A+", "A"  -> new Color(0x10B981);
            case "B+", "B"  -> SUCCESS;
            case "C+", "C"  -> WARNING;
            case "D"  -> new Color(0xFB923C);
            default   -> DANGER;
        };
    }
}
