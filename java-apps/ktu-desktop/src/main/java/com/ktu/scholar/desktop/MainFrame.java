package com.ktu.scholar.desktop;

import com.ktu.scholar.core.db.DatabaseHelper;
import com.ktu.scholar.core.db.ProfileRepository;
import com.ktu.scholar.desktop.panels.*;

import javax.swing.*;
import java.awt.*;

/**
 * Main application window.
 *
 * Layout: left sidebar with icon-button navigation + right content panel.
 * Each nav item swaps the content via CardLayout.
 */
public class MainFrame extends JFrame {

    private static final Color BG          = new Color(0x0A1628);
    private static final Color SIDEBAR_BG  = new Color(0x0D1E35);
    private static final Color ACTIVE_BG   = new Color(0x1E3A5F);
    private static final Color ACCENT      = new Color(0xF59E0B);
    private static final Color FG          = new Color(0xF0F4FF);
    private static final Color MUTED       = new Color(0x8BA3C7);
    private static final Color DANGER      = new Color(0xEF4444);

    private final DatabaseHelper db;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel   = new JPanel(cardLayout);

    private static final String[] TABS = {"Dashboard", "Results", "Attendance", "Tasks", "Materials", "GPA Calc", "Focus", "Settings"};
    private static final String[] ICONS = {"⌂", "★", "✓", "☑", "☰", "∑", "◉", "⚙"};

    public MainFrame(DatabaseHelper db) {
        this.db = db;
        setTitle("KTU Scholar");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 780);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setBackground(BG);
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        // ── Sidebar ───────────────────────────────────────────────────
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));

        // Logo / title
        JLabel logo = new JLabel("KTU Scholar");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        logo.setForeground(ACCENT);
        logo.setBorder(BorderFactory.createEmptyBorder(8, 20, 24, 20));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(logo);

        // Nav buttons
        ButtonGroup bg = new ButtonGroup();
        JToggleButton[] navButtons = new JToggleButton[TABS.length];

        for (int i = 0; i < TABS.length; i++) {
            JToggleButton btn = createNavButton(ICONS[i], TABS[i]);
            bg.add(btn);
            navButtons[i] = btn;
            sidebar.add(btn);
            sidebar.add(Box.createRigidArea(new Dimension(0, 4)));
            final String tab = TABS[i];
            btn.addActionListener(e -> cardLayout.show(contentPanel, tab));
        }

        // Push Logout button to bottom
        sidebar.add(Box.createVerticalGlue());

        // Logout button
        JButton logoutBtn = new JButton("🚪  Log Out");
        logoutBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        logoutBtn.setForeground(DANGER);
        logoutBtn.setBackground(SIDEBAR_BG);
        logoutBtn.setBorderPainted(false);
        logoutBtn.setFocusPainted(false);
        logoutBtn.setContentAreaFilled(false);
        logoutBtn.setOpaque(true);
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.setHorizontalAlignment(SwingConstants.LEFT);
        logoutBtn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        logoutBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        logoutBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutBtn.addActionListener(e -> handleLogout());
        sidebar.add(logoutBtn);

        // ── Content panels ────────────────────────────────────────────
        contentPanel.setBackground(BG);
        contentPanel.add(new DashboardPanel(db),   "Dashboard");
        contentPanel.add(new ResultsPanel(db),     "Results");
        contentPanel.add(new AttendancePanel(db),  "Attendance");
        contentPanel.add(new TodoPanel(db),        "Tasks");
        contentPanel.add(new MaterialsPanel(),     "Materials");
        contentPanel.add(new GradeCalcPanel(),     "GPA Calc");
        contentPanel.add(new FocusPanel(db),       "Focus");
        contentPanel.add(new SettingsPanel(db, this::handleLogout), "Settings");

        // Select first tab
        navButtons[0].setSelected(true);
        cardLayout.show(contentPanel, "Dashboard");

        add(sidebar, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);
    }

    public void handleLogout() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to log out? This will clear local profile and cached data.",
                "Confirm Logout", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                new ProfileRepository(db.getConnection()).deleteProfile();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Logout warning: " + e.getMessage());
            }

            dispose();

            // Re-open setup / onboarding wizard
            SwingUtilities.invokeLater(() -> {
                OnboardingDialog onboarding = new OnboardingDialog(null, db);
                onboarding.setVisible(true);
                try {
                    if (new ProfileRepository(db.getConnection()).getProfile() != null) {
                        new MainFrame(db).setVisible(true);
                    } else {
                        System.exit(0);
                    }
                } catch (Exception ignored) {
                    System.exit(0);
                }
            });
        }
    }

    private JToggleButton createNavButton(String icon, String label) {
        JToggleButton btn = new JToggleButton(icon + "  " + label) {
            @Override
            protected void paintComponent(Graphics g) {
                if (isSelected()) {
                    g.setColor(ACTIVE_BG);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(ACCENT);
                    g2.fillRect(0, 0, 4, getHeight());
                }
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        btn.setForeground(MUTED);
        btn.setBackground(SIDEBAR_BG);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);

        btn.addChangeListener(e -> {
            if (btn.isSelected()) btn.setForeground(FG);
            else btn.setForeground(MUTED);
        });

        return btn;
    }
}
