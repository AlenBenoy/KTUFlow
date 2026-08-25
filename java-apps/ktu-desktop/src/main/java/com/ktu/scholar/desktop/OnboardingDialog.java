package com.ktu.scholar.desktop;

import com.ktu.scholar.core.db.DatabaseHelper;
import com.ktu.scholar.core.db.ProfileRepository;
import com.ktu.scholar.core.model.Profile;

import javax.swing.*;
import java.awt.*;

/**
 * First-run wizard — collects student details in 3 steps:
 *  Step 1: Basic info (name, register number, DOB, branch, semester, college)
 *  Step 2: KTU portal credentials (optional)
 *  Step 3: EzyGo credentials (optional)
 */
public class OnboardingDialog extends JDialog {

    private static final Color BG      = new Color(0x0A1628);
    private static final Color CARD_BG = new Color(0x12213A);
    private static final Color BORDER  = new Color(0x1E3A5F);
    private static final Color ACCENT  = new Color(0xF59E0B);
    private static final Color FG      = new Color(0xF0F4FF);
    private static final Color MUTED   = new Color(0x8BA3C7);

    private final DatabaseHelper db;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel  = new JPanel(cards);
    private int currentStep = 0;

    // Step 1 fields
    private JTextField nameField, regNumField, dobField, branchField, collegeField;
    private JSpinner semesterSpinner;

    // Step 2 fields
    private JPasswordField ktuPassField;

    // Step 3 fields
    private JPasswordField ezygoPassField;

    private JButton nextBtn;
    private JButton backBtn;
    private JLabel stepIndicator;

    public OnboardingDialog(Frame parent, DatabaseHelper db) {
        super(parent, "Welcome to KTU Scholar — Setup", true);
        this.db = db;
        setSize(560, 520);
        setResizable(false);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(BG);
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        // ── Header ────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x0D1E35));
        header.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));

        JLabel title = new JLabel("KTU Scholar");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(ACCENT);

        stepIndicator = new JLabel("Step 1 of 3");
        stepIndicator.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        stepIndicator.setForeground(MUTED);

        header.add(title,         BorderLayout.WEST);
        header.add(stepIndicator, BorderLayout.EAST);

        // ── Cards ─────────────────────────────────────────────────────
        cardPanel.setBackground(BG);
        cardPanel.add(buildStep1(), "1");
        cardPanel.add(buildStep2(), "2");
        cardPanel.add(buildStep3(), "3");

        // ── Footer ────────────────────────────────────────────────────
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(0x0D1E35));
        footer.setBorder(BorderFactory.createEmptyBorder(16, 28, 16, 28));

        backBtn = new JButton("← Back");
        backBtn.setBackground(BORDER);
        backBtn.setForeground(MUTED);
        backBtn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        backBtn.setBorderPainted(false);
        backBtn.setFocusPainted(false);
        backBtn.setEnabled(false);
        backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backBtn.addActionListener(e -> navigate(-1));

        nextBtn = new JButton("Next →");
        nextBtn.setBackground(ACCENT);
        nextBtn.setForeground(new Color(0x0A1628));
        nextBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nextBtn.setBorderPainted(false);
        nextBtn.setFocusPainted(false);
        nextBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nextBtn.addActionListener(e -> navigate(1));

        footer.add(backBtn, BorderLayout.WEST);
        footer.add(nextBtn, BorderLayout.EAST);

        add(header,    BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);
        add(footer,    BorderLayout.SOUTH);
    }

    private JPanel buildStep1() {
        JPanel p = step("1.  Your Details",
                "Let us know who you are. This information is only stored locally.");

        nameField      = addField(p, "Full Name *", false);
        regNumField    = addField(p, "KTU Register Number *", false);
        dobField       = addField(p, "Date of Birth (yyyy-MM-dd)", false);
        branchField    = addField(p, "Branch (e.g. CSE, ECE, ME)", false);
        semesterSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
        styleSpinner(semesterSpinner);
        addLabeledComp(p, "Current Semester", semesterSpinner);
        collegeField = addField(p, "College Name", false);
        return p;
    }

    private JPanel buildStep2() {
        JPanel p = step("2.  KTU Portal Credentials  (Optional)",
                "Used to fetch your results. Stored encrypted on your device only. " +
                "Leave blank until you are ready to sync real KTU data.");
        ktuPassField = new JPasswordField();
        styleField(ktuPassField);
        addLabeledComp(p, "KTU Portal Password", ktuPassField);

        JLabel note = new JLabel("<html><body style='width:380px;color:#8BA3C7'>" +
                "Your register number from Step 1 is used as the username. " +
                "If you skip this, sample results will be shown instead." +
                "</body></html>");
        note.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        p.add(note);
        return p;
    }

    private JPanel buildStep3() {
        JPanel p = step("3.  EzyGo Credentials  (Optional)",
                "Used to fetch attendance from EzyGo. Leave blank until you are ready to sync real attendance.");
        ezygoPassField = new JPasswordField();
        styleField(ezygoPassField);
        addLabeledComp(p, "EzyGo Password", ezygoPassField);

        JLabel note = new JLabel("<html><body style='width:380px;color:#8BA3C7'>" +
                "Your register number is used as the EzyGo username too. " +
                "All credentials are encrypted with AES-256-GCM before being saved." +
                "</body></html>");
        note.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        p.add(note);
        return p;
    }

    private void navigate(int direction) {
        if (direction > 0 && currentStep == 0) {
            if (validateStep1()) currentStep++;
            else return;
        } else if (direction > 0 && currentStep == 2) {
            finish();
            return;
        } else {
            currentStep += direction;
        }

        cards.show(cardPanel, String.valueOf(currentStep + 1));
        stepIndicator.setText("Step " + (currentStep + 1) + " of 3");
        backBtn.setEnabled(currentStep > 0);
        nextBtn.setText(currentStep == 2 ? "Finish ✓" : "Next →");
    }

    private boolean validateStep1() {
        if (nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Full Name is required.");
            return false;
        }
        if (regNumField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Register Number is required.");
            return false;
        }
        return true;
    }

    private void finish() {
        Profile p = new Profile();
        p.setName(regNumField.getText().trim());
        p.setName(nameField.getText().trim());
        p.setRegisterNumber(regNumField.getText().trim());
        p.setDateOfBirth(dobField.getText().trim());
        p.setBranch(branchField.getText().trim());
        p.setSemester((int) semesterSpinner.getValue());
        p.setCollege(collegeField.getText().trim());

        char[] ktu = ktuPassField.getPassword();
        if (ktu.length > 0) p.setKtuPassword(new String(ktu));
        char[] ezygo = ezygoPassField.getPassword();
        if (ezygo.length > 0) p.setEzygoPassword(new String(ezygo));

        try {
            new ProfileRepository(db.getConnection()).saveProfile(p);
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to save profile: " + e.getMessage());
        }
    }

    // ── Builder helpers ───────────────────────────────────────────────────

    private JPanel step(String title, String subtitle) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(28, 32, 16, 32));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLbl.setForeground(FG);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLbl = new JLabel("<html><body style='width:400px'>" + subtitle + "</body></html>");
        subtitleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitleLbl.setForeground(MUTED);
        subtitleLbl.setBorder(BorderFactory.createEmptyBorder(4, 0, 20, 0));
        subtitleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(titleLbl);
        p.add(subtitleLbl);
        return p;
    }

    private JTextField addField(JPanel parent, String label, boolean password) {
        JTextField f = new JTextField();
        styleField(f);
        addLabeledComp(parent, label, f);
        return f;
    }

    private void addLabeledComp(JPanel parent, String label, JComponent comp) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        comp.setAlignmentX(Component.LEFT_ALIGNMENT);
        comp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        parent.add(lbl);
        parent.add(Box.createRigidArea(new Dimension(0, 4)));
        parent.add(comp);
        parent.add(Box.createRigidArea(new Dimension(0, 12)));
    }

    private void styleField(JTextField f) {
        f.setBackground(new Color(0x1E3A5F));
        f.setForeground(FG);
        f.setCaretColor(FG);
        f.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
    }

    private void styleSpinner(JSpinner sp) {
        sp.setBackground(new Color(0x1E3A5F));
        sp.setForeground(FG);
        sp.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        ((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setBackground(new Color(0x1E3A5F));
        ((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setForeground(FG);
    }
}
