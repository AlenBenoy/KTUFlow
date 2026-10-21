package com.ktu.scholar.desktop.panels;

import com.ktu.scholar.core.db.DatabaseHelper;
import com.ktu.scholar.core.db.FocusSettingsRepository;
import com.ktu.scholar.core.model.FocusSettings;
import com.ktu.scholar.core.service.FocusBlockerService;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Focus Mode panel — manages focus sessions, app blocking, and website blocking.
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

    private DefaultListModel<String> blockedAppsModel;
    private JList<String> blockedAppsList;
    private JTextField addAppField;

    private DefaultListModel<String> blockedWebsitesModel;
    private JList<String> blockedWebsitesList;
    private JTextField addWebsiteField;

    public FocusPanel(DatabaseHelper db) {
        super("Focus Mode");
        this.db = db;
        buildContent();
        loadSettingsFromDb();
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
                BorderFactory.createEmptyBorder(20, 20, 20, 20)));

        // Session & Cooldown Duration spinners
        durationSpinner = new JSpinner(new SpinnerNumberModel(60, 10, 300, 5));
        styleSpinner(durationSpinner);
        addRow(card, "Session Duration (minutes)", durationSpinner);
        card.add(Box.createRigidArea(new Dimension(0, 12)));

        cooldownSpinner = new JSpinner(new SpinnerNumberModel(30, 5, 120, 5));
        styleSpinner(cooldownSpinner);
        addRow(card, "Cooldown After Session (minutes)", cooldownSpinner);
        card.add(Box.createRigidArea(new Dimension(0, 16)));

        // Tabbed Pane for Blocked Apps & Blocked Websites
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(BODY_FONT);
        tabs.setBackground(CARD_BG);
        tabs.setForeground(FG);

        tabs.addTab("Blocked Apps", buildBlockedAppsTab());
        tabs.addTab("Blocked Websites", buildBlockedWebsitesTab());
        tabs.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(tabs);
        card.add(Box.createVerticalGlue());
        return card;
    }

    private JPanel buildBlockedAppsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 8, 8, 8));

        blockedAppsModel = new DefaultListModel<>();
        blockedAppsModel.addElement("discord.exe");
        blockedAppsModel.addElement("spotify.exe");
        blockedAppsModel.addElement("steam.exe");

        blockedAppsList = new JList<>(blockedAppsModel);
        styleList(blockedAppsList);

        JScrollPane scroll = new JScrollPane(blockedAppsList);
        scroll.setPreferredSize(new Dimension(0, 110));
        styleScrollPane(scroll);

        addAppField = createInputField("e.g. discord.exe, spotify");
        JButton addBtn = createPrimaryButton("Add");
        addBtn.setPreferredSize(new Dimension(60, 32));
        addBtn.addActionListener(e -> {
            String app = addAppField.getText().trim();
            if (!app.isEmpty()) {
                blockedAppsModel.addElement(app);
                addAppField.setText("");
                saveSettingsToDb();
            }
        });

        JButton removeBtn = new JButton("Remove");
        styleDangerButton(removeBtn);
        removeBtn.addActionListener(e -> {
            int idx = blockedAppsList.getSelectedIndex();
            if (idx >= 0) {
                blockedAppsModel.remove(idx);
                saveSettingsToDb();
            }
        });

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        inputRow.add(addAppField, BorderLayout.CENTER);
        inputRow.add(addBtn, BorderLayout.EAST);

        JPanel removeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        removeRow.setOpaque(false);
        removeRow.add(removeBtn);

        panel.add(scroll);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(inputRow);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
        panel.add(removeRow);
        return panel;
    }

    private JPanel buildBlockedWebsitesTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 8, 8, 8));

        blockedWebsitesModel = new DefaultListModel<>();
        blockedWebsitesModel.addElement("instagram.com");
        blockedWebsitesModel.addElement("youtube.com");
        blockedWebsitesModel.addElement("twitter.com");

        blockedWebsitesList = new JList<>(blockedWebsitesModel);
        styleList(blockedWebsitesList);

        JScrollPane scroll = new JScrollPane(blockedWebsitesList);
        scroll.setPreferredSize(new Dimension(0, 110));
        styleScrollPane(scroll);

        addWebsiteField = createInputField("e.g. instagram.com, youtube.com");
        JButton addBtn = createPrimaryButton("Add");
        addBtn.setPreferredSize(new Dimension(60, 32));
        addBtn.addActionListener(e -> {
            String site = addWebsiteField.getText().trim();
            if (!site.isEmpty()) {
                blockedWebsitesModel.addElement(site);
                addWebsiteField.setText("");
                saveSettingsToDb();
            }
        });

        JButton removeBtn = new JButton("Remove");
        styleDangerButton(removeBtn);
        removeBtn.addActionListener(e -> {
            int idx = blockedWebsitesList.getSelectedIndex();
            if (idx >= 0) {
                blockedWebsitesModel.remove(idx);
                saveSettingsToDb();
            }
        });

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        inputRow.add(addWebsiteField, BorderLayout.CENTER);
        inputRow.add(addBtn, BorderLayout.EAST);

        JPanel removeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        removeRow.setOpaque(false);
        removeRow.add(removeBtn);

        panel.add(scroll);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(inputRow);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
        panel.add(removeRow);
        return panel;
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
        saveSettingsToDb();

        sessionDurationSeconds = (int) durationSpinner.getValue() * 60;
        cooldownDurationSeconds = (int) cooldownSpinner.getValue() * 60;
        elapsedSeconds = 0;

        statusLabel.setText("Focus session active — Blocking enabled");
        statusLabel.setForeground(SUCCESS);
        startStopBtn.setText("End Session");
        startStopBtn.setBackground(DANGER);

        // Activate background App & Website Blocker
        List<String> apps = getListElements(blockedAppsModel);
        List<String> sites = getListElements(blockedWebsitesModel);
        FocusBlockerService.getInstance().startBlocking(apps, sites);

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

        // Stop background blocking
        FocusBlockerService.getInstance().stopBlocking();

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

    private void loadSettingsFromDb() {
        SwingWorker<FocusSettings, Void> worker = new SwingWorker<>() {
            @Override
            protected FocusSettings doInBackground() throws Exception {
                return new FocusSettingsRepository(db.getConnection()).getSettings();
            }

            @Override
            protected void done() {
                try {
                    FocusSettings s = get();
                    if (s != null) {
                        durationSpinner.setValue(Math.max(10, s.getSessionDurationMinutes()));
                        cooldownSpinner.setValue(Math.max(5, s.getCooldownMinutes()));

                        if (s.getBlockedApps() != null && !s.getBlockedApps().isEmpty()) {
                            blockedAppsModel.clear();
                            for (String app : s.getBlockedApps()) blockedAppsModel.addElement(app);
                        }

                        if (s.getBlockedWebsites() != null && !s.getBlockedWebsites().isEmpty()) {
                            blockedWebsitesModel.clear();
                            for (String site : s.getBlockedWebsites()) blockedWebsitesModel.addElement(site);
                        }
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void saveSettingsToDb() {
        FocusSettings s = new FocusSettings();
        s.setSessionDurationMinutes((int) durationSpinner.getValue());
        s.setCooldownMinutes((int) cooldownSpinner.getValue());
        s.setBlockedApps(getListElements(blockedAppsModel));
        s.setBlockedWebsites(getListElements(blockedWebsitesModel));

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                new FocusSettingsRepository(db.getConnection()).saveSettings(s);
                return null;
            }
        };
        worker.execute();
    }

    private List<String> getListElements(DefaultListModel<String> model) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            list.add(model.getElementAt(i));
        }
        return list;
    }

    private String formatTime(int totalSeconds) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private JTextField createInputField(String placeholder) {
        JTextField f = new JTextField();
        f.setBackground(new Color(0x1E3A5F));
        f.setForeground(FG);
        f.setCaretColor(FG);
        f.setFont(BODY_FONT);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return f;
    }

    private void styleList(JList<String> list) {
        list.setBackground(new Color(0x1E3A5F));
        list.setForeground(FG);
        list.setFont(BODY_FONT);
        list.setFixedCellHeight(28);
    }

    private void styleDangerButton(JButton btn) {
        btn.setBackground(DANGER);
        btn.setForeground(Color.WHITE);
        btn.setFont(BODY_FONT);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(80, 28));
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
