package com.ktu.scholar.desktop.panels;

import com.ktu.scholar.core.db.*;
import com.ktu.scholar.core.model.*;
import com.ktu.scholar.core.service.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Home dashboard — shows CGPA summary, attendance overview,
 * upcoming tasks, and latest KTU announcements.
 */
public class DashboardPanel extends BasePanel {

    private final DatabaseHelper db;
    private JLabel cgpaLabel;
    private JLabel attendanceLabel;
    private JPanel todoListPanel;
    private JPanel announcementsPanel;

    public DashboardPanel(DatabaseHelper db) {
        super("Dashboard");
        this.db = db;
        buildContent();
        refresh();
    }

    private void buildContent() {
        JPanel body = getBodyPanel();
        body.setLayout(new BorderLayout(16, 16));

        // ── Top summary cards ─────────────────────────────────────────
        JPanel cards = new JPanel(new GridLayout(1, 2, 16, 0));
        cards.setOpaque(false);
        cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        cgpaLabel = new JLabel("–.––", SwingConstants.CENTER);
        cgpaLabel.setFont(new Font("Segoe UI", Font.BOLD, 40));
        cgpaLabel.setForeground(ACCENT);
        JPanel cgpaCard = createCard("CGPA", cgpaLabel);

        attendanceLabel = new JLabel("--%", SwingConstants.CENTER);
        attendanceLabel.setFont(new Font("Segoe UI", Font.BOLD, 40));
        attendanceLabel.setForeground(new Color(0x34D399));
        JPanel attCard = createCard("Overall Attendance", attendanceLabel);

        cards.add(cgpaCard);
        cards.add(attCard);

        // ── Bottom: tasks + announcements ─────────────────────────────
        JPanel bottom = new JPanel(new GridLayout(1, 2, 16, 0));
        bottom.setOpaque(false);

        todoListPanel = new JPanel();
        todoListPanel.setLayout(new BoxLayout(todoListPanel, BoxLayout.Y_AXIS));
        todoListPanel.setOpaque(false);
        JScrollPane todoScroll = new JScrollPane(todoListPanel);
        styleScrollPane(todoScroll);
        bottom.add(createSection("Upcoming Tasks", todoScroll));

        announcementsPanel = new JPanel();
        announcementsPanel.setLayout(new BoxLayout(announcementsPanel, BoxLayout.Y_AXIS));
        announcementsPanel.setOpaque(false);
        JScrollPane annScroll = new JScrollPane(announcementsPanel);
        styleScrollPane(annScroll);
        bottom.add(createSection("Recent Announcements", annScroll));

        body.add(cards,  BorderLayout.NORTH);
        body.add(bottom, BorderLayout.CENTER);

        // Refresh button
        JButton refreshBtn = createPrimaryButton("Sync Now");
        refreshBtn.addActionListener(e -> syncAndRefresh());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setOpaque(false);
        btnPanel.add(refreshBtn);
        addToHeader(btnPanel);
    }

    public void refresh() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                refreshCgpa();
                refreshAttendance();
                refreshTodos();
                refreshAnnouncements();
                return null;
            }
        };
        worker.execute();
    }

    private void refreshCgpa() {
        try {
            var rCache = new ResultsCacheRepository(db.getConnection()).get();
            if (rCache != null) {
                var data = rCache;
                double cgpa = data.latestCgpa;
                SwingUtilities.invokeLater(() -> cgpaLabel.setText(String.format("%.2f", cgpa)));
            }
        } catch (Exception ignored) {}
    }

    private void refreshAttendance() {
        try {
            var aCache = new AttendanceCacheRepository(db.getConnection()).get();
            if (aCache != null) {
                double overall = aCache.overallPercentage;
                Color color = overall >= 75 ? new Color(0x34D399) : overall >= 60 ? new Color(0xFBBF24) : new Color(0xEF4444);
                SwingUtilities.invokeLater(() -> {
                    attendanceLabel.setText(String.format("%.1f%%", overall));
                    attendanceLabel.setForeground(color);
                });
            }
        } catch (Exception ignored) {}
    }

    private void refreshTodos() {
        try {
            List<Todo> todos = new TodoRepository(db.getConnection()).getPending();
            SwingUtilities.invokeLater(() -> {
                todoListPanel.removeAll();
                if (todos.isEmpty()) {
                    todoListPanel.add(emptyState("All caught up!"));
                } else {
                    todos.stream().limit(5).forEach(t -> todoListPanel.add(createTodoRow(t)));
                }
                todoListPanel.revalidate();
                todoListPanel.repaint();
            });
        } catch (Exception ignored) {}
    }

    private void refreshAnnouncements() {
        try {
            List<Announcement> anns = new AnnouncementRepository(db.getConnection()).getAll();
            SwingUtilities.invokeLater(() -> {
                announcementsPanel.removeAll();
                if (anns.isEmpty()) {
                    announcementsPanel.add(emptyState("No announcements. Click Sync."));
                } else {
                    anns.stream().limit(5).forEach(a -> announcementsPanel.add(createAnnouncementRow(a)));
                }
                announcementsPanel.revalidate();
                announcementsPanel.repaint();
            });
        } catch (Exception ignored) {}
    }

    private void syncAndRefresh() {
        setLoading(true);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    Profile profile = new ProfileRepository(db.getConnection()).getProfile();
                    if (profile != null) {
                        if (profile.hasKtuCredentials()) {
                            var results = new KtuPortalService().fetchResults(
                                    profile.getRegisterNumber(), profile.getKtuPassword());
                            new ResultsCacheRepository(db.getConnection()).save(profile.getRegisterNumber(), results);
                        }
                        if (profile.hasEzygoCredentials()) {
                            var attendance = new EzygoService().fetchAttendance(
                                    profile.getRegisterNumber(), profile.getEzygoPassword());
                            new AttendanceCacheRepository(db.getConnection()).save(profile.getRegisterNumber(), attendance);
                        }
                    }
                    var anns = new AnnouncementService().fetchAnnouncements();
                    new AnnouncementRepository(db.getConnection()).replaceAll(anns);
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(DashboardPanel.this,
                            "Sync failed: " + e.getMessage(), "Sync Error",
                            JOptionPane.WARNING_MESSAGE));
                }
                return null;
            }

            @Override
            protected void done() {
                setLoading(false);
                refresh();
            }
        };
        worker.execute();
    }

    // ── Row builders ──────────────────────────────────────────────────

    private JPanel createTodoRow(Todo t) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        Color priorityColor = switch (t.getPriority()) {
            case HIGH -> new Color(0xEF4444);
            case MEDIUM -> new Color(0xFBBF24);
            case LOW -> new Color(0x34D399);
        };
        JLabel dot = new JLabel("●");
        dot.setForeground(priorityColor);

        JLabel title = new JLabel(t.getTitle());
        title.setForeground(FG);
        title.setFont(BODY_FONT);

        JLabel due = new JLabel(t.getDueDate() != null ? t.getDueDate() : "");
        due.setForeground(MUTED);
        due.setFont(SMALL_FONT);

        row.add(dot, BorderLayout.WEST);
        row.add(title, BorderLayout.CENTER);
        row.add(due, BorderLayout.EAST);
        return row;
    }

    private JPanel createAnnouncementRow(Announcement a) {
        JPanel row = new JPanel(new BorderLayout(8, 2));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        JLabel title = new JLabel("<html><body style='width:260px'>" + a.getTitle() + "</body></html>");
        title.setForeground(FG);
        title.setFont(BODY_FONT);

        JLabel date = new JLabel(a.getDate());
        date.setForeground(MUTED);
        date.setFont(SMALL_FONT);

        row.add(title, BorderLayout.CENTER);
        row.add(date, BorderLayout.EAST);
        return row;
    }
}
