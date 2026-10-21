package com.ktu.scholar.desktop.panels;

import com.ktu.scholar.core.db.*;
import com.ktu.scholar.core.model.Profile;

import javax.swing.*;
import java.awt.*;

/** Profile / settings editor. */
public class SettingsPanel extends BasePanel {

    private final DatabaseHelper db;
    private final Runnable onLogout;

    private JTextField regNumField, nameField, emailField, dobField,
                       branchField, collegeField;
    private JSpinner semesterSpinner;
    private JPasswordField ktuPassField, ezygoPassField;

    public SettingsPanel(DatabaseHelper db) {
        this(db, null);
    }

    public SettingsPanel(DatabaseHelper db, Runnable onLogout) {
        super("Settings");
        this.db = db;
        this.onLogout = onLogout;
        buildContent();
        loadProfile();
    }

    private void buildContent() {
        JPanel body = getBodyPanel();
        body.setLayout(new BorderLayout(0, 0));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 0, 6, 12);

        regNumField     = field(); nameField       = field();
        emailField      = field(); dobField        = field();
        branchField     = field(); collegeField    = field();
        semesterSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
        ktuPassField    = new JPasswordField(); styleField(ktuPassField);
        ezygoPassField  = new JPasswordField(); styleField(ezygoPassField);

        String[][] rows = {
            {"KTU Register Number", null},
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
            regNumField, nameField, emailField, dobField,
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
        SwingWorker<Profile, Void> worker = new SwingWorker<>() {
            @Override protected Profile doInBackground() throws Exception {
                return new ProfileRepository(db.getConnection()).getProfile();
            }
            @Override protected void done() {
                try {
                    Profile p = get();
                    if (p != null) populate(p);
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void populate(Profile p) {
        regNumField.setText(p.getRegisterNumber());
        nameField.setText(p.getName());
        emailField.setText(p.getEmail());
        dobField.setText(p.getDateOfBirth());
        branchField.setText(p.getBranch());
        collegeField.setText(p.getCollege());
        semesterSpinner.setValue(Math.max(1, p.getSemester()));
    }

    private void saveProfile() {
        Profile p = new Profile();
        p.setRegisterNumber(regNumField.getText().trim());
        p.setName(nameField.getText().trim());
        p.setEmail(emailField.getText().trim());
        p.setDateOfBirth(dobField.getText().trim());
        p.setBranch(branchField.getText().trim());
        p.setCollege(collegeField.getText().trim());
        p.setSemester((int) semesterSpinner.getValue());

        char[] ktu = ktuPassField.getPassword();
        if (ktu.length > 0) p.setKtuPassword(new String(ktu));
        char[] ezygo = ezygoPassField.getPassword();
        if (ezygo.length > 0) p.setEzygoPassword(new String(ezygo));

        if (p.getRegisterNumber().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Register number is required.");
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                new ProfileRepository(db.getConnection()).saveProfile(p);
                return null;
            }
            @Override protected void done() {
                ktuPassField.setText("");
                ezygoPassField.setText("");
                JOptionPane.showMessageDialog(SettingsPanel.this,
                    "Profile saved successfully!", "Saved", JOptionPane.INFORMATION_MESSAGE);
            }
        };
        worker.execute();
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
