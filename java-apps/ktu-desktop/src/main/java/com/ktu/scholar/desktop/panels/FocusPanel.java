package com.ktu.scholar.desktop.panels;

import com.ktu.scholar.core.db.DatabaseHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Focus Mode panel — starts a timed study session, shows elapsed time,
 * and manages a cooldown period when a session ends.
 *
 * Note: On desktop (Windows), apps cannot be blocked via Java without
 * elevated OS-level permissions. This panel provides a session tracker
 * and a list of reminders. True app blocking can be added by spawning
 * a Windows Hosts-file editor or using parental-control APIs at the
 * OS level (documented in README).
 */
public class FocusPanel extends BasePanel {

    private final DatabaseHelper db;

    private Timer sessionTimer;
    private Timer cooldownTimer;
    private int elapsedSeconds = 0;
    private int cooldownSeconds = 0;
    private int sessionDurationSeconds;
    private int cooldownDurationSeconds;
    private boolean inCooldown = false;

    private JLabel timerLabel;
    private JLabel statusLabel;
    private JButton startStopBtn;
    private JProgressBar sessionBar;
    private JSpinner durationSpinner;
    private JSpinner cooldownSpinner;
    private JList<String> blockedList;
    private DefaultListModel<String> blockedModel;
    private JTextField addBlockedField;

    public FocusPanel(DatabaseHelper db) {
        super("Focus Mode");
        this.db = db;
        buildContent();
    }

    private void buildContent() {
        JPanel body = getBodyPanel();
        body.setLayout(new GridLayout(1, 2, 20, 0));

        body.add(buildTimerCard());
        body.add(buildSettingsCard());
    }

    private JPanel buildTimerCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(32, 32, 32, 32)));

        statusLabel = new JLabel("Ready to focus", SwingConstants.CENTER);
        statusLabel.setFont(HEADER_FONT);
        statusLabel.setForeground(MUTED);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerLabel = new JLabel("00:00:00", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Segoe Mono", Font.BOLD, 56));
        timerLabel.setForeground(ACCENT);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        sessionBar = new JProgressBar(0, 100);
        sessionBar.setValue(0);
        sessionBar.setForeground(ACCENT);
        sessionBar.setBackground(BORDER);
        sessionBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        sessionBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        startStopBtn = createPrimaryButton("Start Focus Session");
        startStopBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        startStopBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        startStopBtn.setMaximumSize(new Dimension(260, 48));
        startStopBtn.addActionListener(e -> toggleSession());

        card.add(Box.createVerticalGlue());
        card.add(statusLabel);
        card.add(Box.createRigidArea(new Dimension(0, 20)));
        card.add(timerLabel);
        card.add(Box.createRigidArea(new Dimension(0, 20)));
        card.add(sessionBar);
        card.add(Box.createRigidArea(new Dimension(0, 32)));
        card.add(startStopBtn);
        card.add(Box.createVerticalGlue());
        return card;
    }

    private JPanel buildSettingsCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(24, 24, 24, 24)));

        // Session duration
        durationSpinner = new JSpinner(new SpinnerNumberModel(60, 10, 300, 5));
        styleSpinner(durationSpinner);
        addRow(card, "Session Duration (minutes)", durationSpinner);
        card.add(Box.createRigidArea(new Dimension(0, 16)));

        // Cooldown duration
        cooldownSpinner = new JSpinner(new SpinnerNumberModel(30, 5, 120, 5));
        styleSpinner(cooldownSpinner);
        addRow(card, "Cooldown After Session (minutes)", cooldownSpinner);
        card.add(Box.createRigidArea(new Dimension(0, 24)));

        // Blocked sites / apps list
        JLabel blockedTitle = new JLabel("Blocked Sites / Reminders");
        blockedTitle.setFont(HEADER_FONT);
        blockedTitle.setForeground(FG);
        blockedTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(blockedTitle);
        card.add(Box.createRigidArea(new Dimension(0, 8)));

        blockedModel = new DefaultListModel<>();
        blockedModel.addElement("instagram.com");
        blockedModel.addElement("youtube.com");
        blockedModel.addElement("twitter.com");
        blockedList = new JList<>(blockedModel);
        blockedList.setBackground(new Color(0x1E3A5F));
        blockedList.setForeground(FG);
        blockedList.setFont(BODY_FONT);
        blockedList.setFixedCellHeight(30);
        JScrollPane listScroll = new JScrollPane(blockedList);
        listScroll.setPreferredSize(new Dimension(0, 140));
        listScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        listScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        styleScrollPane(listScroll);
        card.add(listScroll);
        card.add(Box.createRigidArea(new Dimension(0, 8)));

        // Add / remove
        JPanel addRow = new JPanel(new BorderLayout(8, 0));
        addRow.setOpaque(false);
        addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        addRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        addBlockedField = new JTextField();
        addBlockedField.setBackground(new Color(0x1E3A5F));
        addBlockedField.setForeground(FG);
        addBlockedField.setCaretColor(FG);
        addBlockedField.setFont(BODY_FONT);
        addBlockedField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JButton addBtn = createPrimaryButton("Add");
        addBtn.setPreferredSize(new Dimension(60, 36));
        addBtn.addActionListener(e -> {
            String site = addBlockedField.getText().trim();
            if (!site.isEmpty()) { blockedModel.addElement(site); addBlockedField.setText(""); }
        });

        JButton removeBtn = new JButton("Remove");
        removeBtn.setBackground(DANGER);
        removeBtn.setForeground(Color.WHITE);
        removeBtn.setFont(BODY_FONT);
        removeBtn.setBorderPainted(false);
        removeBtn.setFocusPainted(false);
        removeBtn.setPreferredSize(new Dimension(80, 36));
        removeBtn.addActionListener(e -> {
            int idx = blockedList.getSelectedIndex();
            if (idx >= 0) blockedModel.remove(idx);
        });

        addRow.add(addBlockedField, BorderLayout.CENTER);
        addRow.add(addBtn,          BorderLayout.EAST);
        card.add(addRow);
        card.add(Box.createRigidArea(new Dimension(0, 8)));

        JPanel removeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        removeRow.setOpaque(false);
        removeRow.add(removeBtn);
        removeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        removeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        card.add(removeRow);

        card.add(Box.createVerticalGlue());
        return card;
    }

    private void toggleSession() {
        if (inCooldown) {
            JOptionPane.showMessageDialog(this,
                "Cooldown in progress. Wait for the cooldown to finish before starting a new session.",
                "Cooldown Active", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (sessionTimer != null && sessionTimer.isRunning()) {
            stopSession();
        } else {
            startSession();
        }
    }

    private void startSession() {
        sessionDurationSeconds = (int) durationSpinner.getValue() * 60;
        cooldownDurationSeconds = (int) cooldownSpinner.getValue() * 60;
        elapsedSeconds = 0;

        statusLabel.setText("Focus session active");
        statusLabel.setForeground(SUCCESS);
        startStopBtn.setText("End Session");
        startStopBtn.setBackground(DANGER);

        sessionTimer = new Timer(1000, e -> {
            elapsedSeconds++;
            timerLabel.setText(formatTime(elapsedSeconds));
            int progress = (int) ((double) elapsedSeconds / sessionDurationSeconds * 100);
            sessionBar.setValue(Math.min(progress, 100));
            sessionBar.setForeground(progress < 80 ? ACCENT : SUCCESS);

            if (elapsedSeconds >= sessionDurationSeconds) {
                stopSession();
                startCooldown();
            }
        });
        sessionTimer.start();
    }

    private void stopSession() {
        if (sessionTimer != null) sessionTimer.stop();
        startStopBtn.setText("Start Focus Session");
        startStopBtn.setBackground(ACCENT);
        startStopBtn.setForeground(new Color(0x0A1628));
    }

    private void startCooldown() {
        inCooldown = true;
        cooldownSeconds = cooldownDurationSeconds;
        statusLabel.setText("Cooldown");
        statusLabel.setForeground(WARNING);
        startStopBtn.setText("Cooldown in progress...");
        startStopBtn.setBackground(BORDER);

        JOptionPane.showMessageDialog(this,
            "Great work! Session complete.\nCooldown of " + cooldownDurationSeconds/60 + " minutes started.",
            "Session Complete", JOptionPane.INFORMATION_MESSAGE);

        cooldownTimer = new Timer(1000, e -> {
            cooldownSeconds--;
            timerLabel.setText(formatTime(cooldownSeconds));
            sessionBar.setValue((int)((double)cooldownSeconds / cooldownDurationSeconds * 100));
            sessionBar.setForeground(WARNING);
            if (cooldownSeconds <= 0) {
                cooldownTimer.stop();
                inCooldown = false;
                statusLabel.setText("Ready to focus");
                statusLabel.setForeground(MUTED);
                timerLabel.setText("00:00:00");
                sessionBar.setValue(0);
                startStopBtn.setText("Start Focus Session");
                startStopBtn.setBackground(ACCENT);
                JOptionPane.showMessageDialog(FocusPanel.this,
                    "Cooldown complete! You can start another session.",
                    "Cooldown Complete", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        cooldownTimer.start();
    }

    private String formatTime(int totalSeconds) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private void addRow(JPanel parent, String labelText, JComponent comp) {
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(SMALL_FONT);
        lbl.setForeground(MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        comp.setAlignmentX(Component.LEFT_ALIGNMENT);
        comp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        parent.add(lbl);
        parent.add(Box.createRigidArea(new Dimension(0, 4)));
        parent.add(comp);
    }

    private void styleSpinner(JSpinner sp) {
        sp.setBackground(new Color(0x1E3A5F));
        sp.setForeground(FG);
        sp.setFont(BODY_FONT);
        ((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setBackground(new Color(0x1E3A5F));
        ((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setForeground(FG);
    }
}
