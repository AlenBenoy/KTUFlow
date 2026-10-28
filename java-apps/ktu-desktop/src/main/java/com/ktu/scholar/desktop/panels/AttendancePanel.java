package com.ktu.scholar.desktop.panels;

import com.ktu.scholar.core.db.*;
import com.ktu.scholar.core.model.*;
import com.ktu.scholar.core.service.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Attendance panel with per-subject progress bars, colour-coded status chips,
 * and recommendations.
 */
public class AttendancePanel extends BasePanel {

    private final DatabaseHelper db;
    private JLabel overallLabel;
    private JProgressBar overallBar;
    private JPanel subjectsPanel;
    private JTextField ezyGoIdField;
    private JTextField emailField;
    private JLabel profileSaveStatus;
    private final Runnable profileChangeListener =
            () -> SwingUtilities.invokeLater(this::refreshProfileMeta);
    private boolean profileMetaLoaded;
    private boolean loadingProfileMeta;
    private boolean ezyGoIdDirty;
    private boolean emailDirty;
    private boolean savingProfileMeta;
    private boolean listeningForProfileChanges;
    private long profileMetaGeneration;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public AttendancePanel(DatabaseHelper db) {
        super("Attendance");
        this.db = db;
        buildContent();
        loadFromCache();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!listeningForProfileChanges) {
            ProfileRepository.addProfileChangeListener(profileChangeListener);
            listeningForProfileChanges = true;
        }
    }

    @Override
    public void removeNotify() {
        if (listeningForProfileChanges) {
            ProfileRepository.removeProfileChangeListener(profileChangeListener);
            listeningForProfileChanges = false;
        }
        super.removeNotify();
    }

    private void buildContent() {
        JPanel body = getBodyPanel();
        body.setLayout(new BorderLayout(0, 20));

        ezyGoIdField = createProfileField("EzyGo ID");
        emailField = createProfileField("Email");
        attachFieldSaveHandlers();

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

        // Sync button + profile metadata inputs in the top-right corner
        JButton syncBtn = createPrimaryButton("Sync from EzyGo");
        syncBtn.addActionListener(e -> syncAttendance());

        JPanel fieldsPanel = new JPanel();
        fieldsPanel.setOpaque(false);
        fieldsPanel.setLayout(new BoxLayout(fieldsPanel, BoxLayout.Y_AXIS));

        JPanel syncRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        syncRow.setOpaque(false);
        syncRow.add(syncBtn);

        JPanel metaRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        metaRow.setOpaque(false);
        metaRow.add(createLabeledField("EzyGo ID", ezyGoIdField));
        metaRow.add(createLabeledField("Email", emailField));

        fieldsPanel.add(syncRow);
        fieldsPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        fieldsPanel.add(metaRow);
        profileSaveStatus = new JLabel(" ");
        profileSaveStatus.setFont(SMALL_FONT);
        profileSaveStatus.setForeground(DANGER);
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        statusRow.setOpaque(false);
        statusRow.add(profileSaveStatus);
        fieldsPanel.add(statusRow);
        fieldsPanel.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                metaRow.setLayout(fieldsPanel.getWidth() < 500
                        ? new GridLayout(2, 1, 0, 6)
                        : new FlowLayout(FlowLayout.RIGHT, 8, 0));
                metaRow.revalidate();
            }
        });

        addToHeader(fieldsPanel);
        loadProfileMeta();
    }

    private JTextField createProfileField(String label) {
        JTextField field = new JTextField();
        field.setColumns(16);
        field.setPreferredSize(new Dimension(170, 30));
        field.setBackground(new Color(0x1E3A5F));
        field.setForeground(FG);
        field.setCaretColor(FG);
        field.setFont(SMALL_FONT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        field.setToolTipText(label);
        return field;
    }

    private JPanel createLabeledField(String labelText, JTextField field) {
        JPanel wrap = new JPanel(new BorderLayout(6, 0));
        wrap.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(SMALL_FONT);
        label.setForeground(MUTED);

        wrap.add(label, BorderLayout.WEST);
        wrap.add(field, BorderLayout.CENTER);
        wrap.setPreferredSize(new Dimension(230, 34));
        return wrap;
    }

    private void attachFieldSaveHandlers() {
        DocumentListener idListener = createDirtyListener(true);
        DocumentListener emailListener = createDirtyListener(false);
        ezyGoIdField.getDocument().addDocumentListener(idListener);
        emailField.getDocument().addDocumentListener(emailListener);
        FieldSaveListener listener = new FieldSaveListener();
        ezyGoIdField.addFocusListener(listener);
        emailField.addFocusListener(listener);
        ezyGoIdField.addActionListener(e -> saveProfileMeta());
        emailField.addActionListener(e -> saveProfileMeta());
    }

    private void loadProfileMeta() {
        long generation = ++profileMetaGeneration;
        SwingWorker<Profile, Void> worker = new SwingWorker<>() {
            @Override protected Profile doInBackground() throws Exception {
                return new ProfileRepository(db.getConnection()).getProfile();
            }

            @Override protected void done() {
                if (generation != profileMetaGeneration) return;
                try {
                    Profile p = get();
                    if (p != null) displayProfileMeta(p);
                    profileMetaLoaded = true;
                } catch (Exception e) {
                    profileSaveStatus.setText("Could not load profile.");
                    profileSaveStatus.setToolTipText(e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private DocumentListener createDirtyListener(boolean ezyGoId) {
        return new DocumentListener() {
            private void changed() {
                if (!loadingProfileMeta) {
                    if (ezyGoId) ezyGoIdDirty = true;
                    else emailDirty = true;
                }
            }

            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        };
    }

    private void displayProfileMeta(Profile p) {
        loadingProfileMeta = true;
        if (!ezyGoIdDirty) ezyGoIdField.setText(p.getEzygoId() == null ? "" : p.getEzygoId());
        if (!emailDirty) emailField.setText(p.getEmail() == null ? "" : p.getEmail());
        loadingProfileMeta = false;
    }

    private void refreshProfileMeta() {
        long generation = ++profileMetaGeneration;
        SwingWorker<Profile, Void> worker = new SwingWorker<>() {
            @Override protected Profile doInBackground() throws Exception {
                return new ProfileRepository(db.getConnection()).getProfile();
            }

            @Override protected void done() {
                if (generation != profileMetaGeneration) return;
                try {
                    Profile p = get();
                    if (p != null) displayProfileMeta(p);
                    profileMetaLoaded = true;
                } catch (Exception e) {
                    profileSaveStatus.setText("Could not refresh profile.");
                    profileSaveStatus.setToolTipText(e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void saveProfileMeta() {
        if (!profileMetaLoaded || savingProfileMeta || (!ezyGoIdDirty && !emailDirty)) return;

        String ezyGoId = ezyGoIdField.getText().trim();
        String email = emailField.getText().trim();
        boolean saveEzyGoId = ezyGoIdDirty;
        boolean saveEmail = emailDirty;
        if (!isValidEmail(email)) {
            profileSaveStatus.setText("Enter a valid email address.");
            profileSaveStatus.setToolTipText("Email must be a valid address, or left blank.");
            emailField.requestFocusInWindow();
            return;
        }

        savingProfileMeta = true;
        profileSaveStatus.setText("Saving...");
        profileSaveStatus.setToolTipText(null);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                boolean updated = new ProfileRepository(db.getConnection())
                        .updateContactInfo(ezyGoId, saveEzyGoId, email, saveEmail);
                if (!updated) throw new IllegalStateException("No saved profile is available to update.");
                return null;
            }

            @Override protected void done() {
                savingProfileMeta = false;
                try {
                    get();
                    ezyGoIdDirty = !ezyGoId.equals(ezyGoIdField.getText().trim());
                    emailDirty = !email.equals(emailField.getText().trim());
                    profileSaveStatus.setText("Saved");
                    profileSaveStatus.setToolTipText(null);
                    if (ezyGoIdDirty || emailDirty) {
                        profileSaveStatus.setText("Saving latest edits...");
                        SwingUtilities.invokeLater(AttendancePanel.this::saveProfileMeta);
                    }
                } catch (Exception e) {
                    profileSaveStatus.setText("Could not save profile.");
                    profileSaveStatus.setToolTipText(e.getCause() == null
                            ? e.getMessage() : e.getCause().getMessage());
                }
            }
        };
        worker.execute();
    }

    private static boolean isValidEmail(String email) {
        return email == null || email.isBlank() || EMAIL_PATTERN.matcher(email.trim()).matches();
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
        if (subjects == null || subjects.isEmpty()) {
            showEmptyState();
            return;
        }

        double total = subjects.stream()
                .filter(java.util.Objects::nonNull)
                .mapToInt(SubjectAttendance::getTotalClasses)
                .sum();
        double attended = subjects.stream()
                .filter(java.util.Objects::nonNull)
                .mapToInt(SubjectAttendance::getAttendedClasses)
                .sum();
        double overall = total > 0 ? attended / total * 100 : 0;

        Color overallColor = overall >= 75 ? SUCCESS : overall >= 60 ? WARNING : DANGER;
        overallLabel.setText(String.format("%.1f%%", overall));
        overallLabel.setForeground(overallColor);
        overallBar.setValue((int) overall);
        overallBar.setForeground(overallColor);

        subjectsPanel.removeAll();
        for (SubjectAttendance s : subjects) {
            if (s == null) continue;
            subjectsPanel.add(createSubjectRow(s));
            subjectsPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        }
        subjectsPanel.revalidate();
        subjectsPanel.repaint();
    }

    private JPanel createSubjectRow(SubjectAttendance s) {
        SubjectAttendance.AttendanceStatus status = s.getStatus() != null ? s.getStatus() : SubjectAttendance.AttendanceStatus.DANGER;
        Color cardColor = statusColor(status);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardColor, 1, true),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // Top row: name + status chip + percentage
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);

        JLabel name = new JLabel((s.getSubjectCode() == null ? "" : s.getSubjectCode()) + "  " + (s.getSubjectName() == null ? "Unknown Subject" : s.getSubjectName()));
        name.setFont(BODY_FONT);
        name.setForeground(FG);

        JLabel chip = new JLabel(status.name());
        chip.setFont(SMALL_FONT);
        chip.setForeground(cardColor);
        chip.setBorder(BorderFactory.createLineBorder(cardColor, 1, true));
        chip.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        JLabel pct = new JLabel(String.format("%.1f%%  (%d/%d)",
                s.getPercentage(), s.getAttendedClasses(), s.getTotalClasses()));
        pct.setFont(BODY_FONT);
        pct.setForeground(cardColor);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(chip);
        right.add(pct);

        top.add(name,  BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);

        // Progress bar
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue((int) s.getPercentage());
        bar.setForeground(cardColor);
        bar.setBackground(BORDER);
        bar.setStringPainted(false);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));

        // Recommendation
        String recommendation = s.getRecommendation() == null ? "No recommendation available." : s.getRecommendation();
        JLabel rec = new JLabel("<html><body style='width:500px;color:#8BA3C7'>"
                + recommendation + "</body></html>");
        rec.setFont(SMALL_FONT);
        rec.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        card.add(top);
        card.add(Box.createRigidArea(new Dimension(0, 6)));
        card.add(bar);
        card.add(rec);

        // Condonation steps (collapsed by default)
        java.util.List<String> condonationSteps = s.getCondonationSteps();
        if (status == SubjectAttendance.AttendanceStatus.CONDONATION && condonationSteps != null && !condonationSteps.isEmpty()) {
            JPanel steps = new JPanel();
            steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
            steps.setBackground(new Color(0x1A1A2E));
            steps.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
            steps.setVisible(false);

            for (String step : condonationSteps) {
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

    private class FieldSaveListener extends java.awt.event.FocusAdapter {
        @Override
        public void focusLost(java.awt.event.FocusEvent e) {
            if ((e.getSource() == ezyGoIdField && ezyGoIdDirty)
                    || (e.getSource() == emailField && emailDirty)) {
                saveProfileMeta();
            } else {
                refreshProfileMeta();
            }
        }
    }

    private void showEmptyState() {
        subjectsPanel.removeAll();
        subjectsPanel.add(emptyState("No attendance data. Click 'Sync from EzyGo'."));
        subjectsPanel.revalidate();
        subjectsPanel.repaint();
    }
}
