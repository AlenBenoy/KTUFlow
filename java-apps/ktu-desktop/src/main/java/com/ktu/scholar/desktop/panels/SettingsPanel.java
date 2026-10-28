package com.ktu.scholar.desktop.panels;

import com.ktu.scholar.core.db.*;
import com.ktu.scholar.core.model.Profile;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Profile / settings editor. */
public class SettingsPanel extends BasePanel {

    private final DatabaseHelper db;
    private final Runnable onLogout;
    private final Runnable profileChangeListener;

    private JTextField regNumField, ezygoIdField, nameField, emailField, dobField,
                       branchField, collegeField;
    private JSpinner semesterSpinner;
    private JPasswordField ktuPassField, ezygoPassField;
    private boolean profileLoaded;
    private boolean updatingProfileFields;
    private boolean ezygoIdDirty;
    private boolean emailDirty;
    private boolean listeningForProfileChanges;
    private long profileLoadGeneration;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public SettingsPanel(DatabaseHelper db) {
        this(db, null);
    }

    public SettingsPanel(DatabaseHelper db, Runnable onLogout) {
        super("Settings");
        this.db = db;
        this.onLogout = onLogout;
        this.profileChangeListener =
                () -> SwingUtilities.invokeLater(this::refreshSharedProfileFields);
        buildContent();
        loadProfile();
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
        body.setLayout(new BorderLayout(0, 0));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 0, 6, 12);

        regNumField     = field(); ezygoIdField    = field();
        nameField       = field(); emailField      = field();
        dobField        = field(); branchField     = field();
        collegeField    = field();
        addSharedFieldListeners();
        semesterSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
        ktuPassField    = new JPasswordField(); styleField(ktuPassField);
        ezygoPassField  = new JPasswordField(); styleField(ezygoPassField);

        String[][] rows = {
            {"KTU Register Number", null},
            {"EzyGo ID", null},
            {"Full Name", null},
            {"Email", null},
            {"Date of Birth (yyyy-MM-dd)", null},
            {"Branch", null},
            {"College", null},
            {"Current Semester", null},
            {"KTU Portal Password", null},
            {"EzyGo Password", null},
        };
        JComponent[] components = {
            regNumField, ezygoIdField, nameField, emailField, dobField,
            branchField, collegeField, semesterSpinner,
            ktuPassField, ezygoPassField
        };

        for (int i = 0; i < rows.length; i++) {
            gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0.3;
            JLabel lbl = new JLabel(rows[i][0]);
            lbl.setForeground(MUTED); lbl.setFont(BODY_FONT);
            form.add(lbl, gbc);

            gbc.gridx = 1; gbc.weightx = 0.7;
            form.add(components[i], gbc);
        }

        // Hint about password security
        gbc.gridx = 0; gbc.gridy = rows.length; gbc.gridwidth = 2; gbc.weightx = 1;
        JLabel hint = new JLabel("Passwords are stored encrypted on your local device only.");
        hint.setForeground(MUTED); hint.setFont(SMALL_FONT);
        hint.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        form.add(hint, gbc);

        JScrollPane scroll = new JScrollPane(form);
        styleScrollPane(scroll);

        JButton saveBtn = createPrimaryButton("Save Profile");
        saveBtn.setPreferredSize(new Dimension(160, 44));
        saveBtn.addActionListener(e -> saveProfile());

        JButton logoutBtn = new JButton("Log Out");
        logoutBtn.setBackground(DANGER);
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        logoutBtn.setBorderPainted(false);
        logoutBtn.setFocusPainted(false);
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.setPreferredSize(new Dimension(120, 44));
        logoutBtn.addActionListener(e -> {
            if (onLogout != null) {
                onLogout.run();
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnPanel.setOpaque(false);
        if (onLogout != null) {
            btnPanel.add(logoutBtn);
        }
        btnPanel.add(saveBtn);

        body.add(scroll,   BorderLayout.CENTER);
        body.add(btnPanel, BorderLayout.SOUTH);
    }

    private void loadProfile() {
        long generation = ++profileLoadGeneration;
        SwingWorker<Profile, Void> worker = new SwingWorker<>() {
            @Override protected Profile doInBackground() throws Exception {
                return new ProfileRepository(db.getConnection()).getProfile();
            }
            @Override protected void done() {
                if (generation != profileLoadGeneration) return;
                try {
                    Profile p = get();
                    if (p != null) populate(p);
                    else profileLoaded = true;
                } catch (Exception e) {
                    profileLoaded = false;
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                            "Could not load profile: " + e.getMessage(),
                            "Profile Load Failed", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void populate(Profile p) {
        updatingProfileFields = true;
        regNumField.setText(p.getRegisterNumber());
        ezygoIdField.setText(p.getEzygoId());
        nameField.setText(p.getName());
        emailField.setText(p.getEmail());
        dobField.setText(p.getDateOfBirth());
        branchField.setText(p.getBranch());
        collegeField.setText(p.getCollege());
        int semester = p.getSemester();
        if (semester < 1) semester = 1;
        else if (semester > 8) semester = 8;
        semesterSpinner.setValue(semester);
        ezygoIdDirty = false;
        emailDirty = false;
        updatingProfileFields = false;
        profileLoaded = true;
    }

    private void saveProfile() {
        if (!profileLoaded) {
            JOptionPane.showMessageDialog(this, "Profile is still loading. Please try again.",
                    "Profile Not Ready", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Profile p = new Profile();
        p.setRegisterNumber(regNumField.getText().trim());
        p.setEzygoId(ezygoIdField.getText().trim());
        p.setName(nameField.getText().trim());
        p.setEmail(emailField.getText().trim());
        p.setDateOfBirth(dobField.getText().trim());
        p.setBranch(branchField.getText().trim());
        p.setCollege(collegeField.getText().trim());
        p.setSemester((int) semesterSpinner.getValue());

        if (!isValidEmail(p.getEmail())) {
            JOptionPane.showMessageDialog(this, "Enter a valid email address.",
                    "Invalid Email", JOptionPane.WARNING_MESSAGE);
            emailField.requestFocusInWindow();
            return;
        }

        String ktu = readPassword(ktuPassField);
        if (ktu != null) p.setKtuPassword(ktu);
        String ezygo = readPassword(ezygoPassField);
        if (ezygo != null) p.setEzygoPassword(ezygo);
        boolean saveEzygoId = ezygoIdDirty;
        boolean saveEmail = emailDirty;

        if (p.getRegisterNumber().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Register number is required.");
            return;
        }

        SwingWorker<Profile, Void> worker = new SwingWorker<>() {
            @Override protected Profile doInBackground() throws Exception {
                var connection = db.getConnection();
                synchronized (connection) {
                    ProfileRepository repository = new ProfileRepository(connection);
                    Profile current = repository.getProfile();
                    if (current != null) {
                        if (!saveEzygoId) p.setEzygoId(current.getEzygoId());
                        if (!saveEmail) p.setEmail(current.getEmail());
                    }
                    return repository.saveProfile(p);
                }
            }
            @Override protected void done() {
                try {
                    get();
                    ktuPassField.setText("");
                    ezygoPassField.setText("");
                    if (ezygoIdField.getText().trim().equals(p.getEzygoId())) {
                        ezygoIdDirty = false;
                    }
                    if (emailField.getText().trim().equals(p.getEmail())) {
                        emailDirty = false;
                    }
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                        "Profile saved successfully!", "Saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                            "Could not save profile: " + e.getMessage(),
                            "Save Failed", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void addSharedFieldListeners() {
        ezygoIdField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { ezygoIdDirty = !updatingProfileFields; }
            @Override public void removeUpdate(DocumentEvent e) { ezygoIdDirty = !updatingProfileFields; }
            @Override public void changedUpdate(DocumentEvent e) { ezygoIdDirty = !updatingProfileFields; }
        });
        emailField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { emailDirty = !updatingProfileFields; }
            @Override public void removeUpdate(DocumentEvent e) { emailDirty = !updatingProfileFields; }
            @Override public void changedUpdate(DocumentEvent e) { emailDirty = !updatingProfileFields; }
        });
    }

    private void refreshSharedProfileFields() {
        long generation = ++profileLoadGeneration;
        SwingWorker<Profile, Void> worker = new SwingWorker<>() {
            @Override protected Profile doInBackground() throws Exception {
                return new ProfileRepository(db.getConnection()).getProfile();
            }

            @Override protected void done() {
                if (generation != profileLoadGeneration) return;
                try {
                    Profile p = get();
                    if (p == null) {
                        profileLoaded = true;
                        return;
                    }
                    if (!profileLoaded) {
                        populate(p);
                        return;
                    }
                    updatingProfileFields = true;
                    if (!ezygoIdDirty) {
                        ezygoIdField.setText(p.getEzygoId());
                    }
                    if (!emailDirty) {
                        emailField.setText(p.getEmail());
                    }
                    profileLoaded = true;
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                            "Could not refresh profile fields: " + e.getMessage(),
                            "Profile Refresh Failed", JOptionPane.WARNING_MESSAGE);
                } finally {
                    updatingProfileFields = false;
                }
            }
        };
        worker.execute();
    }

    private static boolean isValidEmail(String email) {
        return email == null || email.isBlank() || EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private static String readPassword(JPasswordField field) {
        char[] value = field.getPassword();
        try {
            return value.length == 0 ? null : new String(value);
        } finally {
            Arrays.fill(value, '\0');
        }
    }

    private JTextField field() {
        JTextField f = new JTextField();
        styleField(f);
        return f;
    }

    private void styleField(JTextField f) {
        f.setBackground(new Color(0x1E3A5F));
        f.setForeground(FG);
        f.setCaretColor(FG);
        f.setFont(BODY_FONT);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
    }
}
