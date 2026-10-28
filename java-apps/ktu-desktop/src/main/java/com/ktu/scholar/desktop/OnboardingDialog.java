package com.ktu.scholar.desktop;

import com.ktu.scholar.core.db.DatabaseHelper;
import com.ktu.scholar.core.db.ProfileRepository;
import com.ktu.scholar.core.model.Profile;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * First-run wizard — collects student details in 3 steps:
 *  Step 1: Basic info (name, register number, EzyGo ID, email ID, DOB, branch, semester, college)
 *  Step 2: KTU portal credentials (optional)
 *  Step 3: EzyGo credentials (optional) + option to add a Desktop shortcut
 */
public class OnboardingDialog extends JDialog {

    private static final Color BG      = new Color(0x0A1628);
    private static final Color BORDER  = new Color(0x1E3A5F);
    private static final Color ACCENT  = new Color(0xF59E0B);
    private static final Color FG      = new Color(0xF0F4FF);
    private static final Color MUTED   = new Color(0x8BA3C7);

    private static final String SHORTCUT_NAME = "KTU Scholar";

    private final DatabaseHelper db;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel  = new JPanel(cards);
    private int currentStep = 0;

    // Step 1 fields
    private JTextField nameField, regNumField, ezygoIdField, emailField,
            dobField, branchField, collegeField;
    private JSpinner semesterSpinner;

    // Step 2 fields
    private JPasswordField ktuPassField;

    // Step 3 fields
    private JPasswordField ezygoPassField;
    private JCheckBox shortcutCheck;

    private JButton nextBtn;
    private JButton backBtn;
    private JLabel stepIndicator;

    public OnboardingDialog(Frame parent, DatabaseHelper db) {
        super(parent, "Welcome to KTU Scholar — Setup", true);
        this.db = db;
        setSize(560, 680);
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
        cardPanel.add(wrapInScroll(buildStep1()), "1");
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

    /** Step 1 has many fields, so it is wrapped in a scroll pane (dark themed). */
    private JScrollPane wrapInScroll(JPanel content) {
        JScrollPane sp = new JScrollPane(content,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(BG);
        sp.setBackground(BG);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private JPanel buildStep1() {
        JPanel p = step("1.  Your Details",
                "Let us know who you are. This information is only stored locally.");

        nameField      = addField(p, "Full Name *");
        regNumField    = addField(p, "KTU Register Number (KTU ID) *");
        ezygoIdField   = addField(p, "EzyGo ID");
        emailField     = addField(p, "Email ID");
        dobField       = addField(p, "Date of Birth (yyyy-MM-dd)");
        branchField    = addField(p, "Branch (e.g. CSE, ECE, ME)");
        semesterSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
        styleSpinner(semesterSpinner);
        addLabeledComp(p, "Current Semester", semesterSpinner);
        collegeField = addField(p, "College Name");
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
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
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
                "Your EzyGo ID from Step 1 is used as the EzyGo username. " +
                "All credentials are encrypted with AES-256-GCM before being saved." +
                "</body></html>");
        note.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(note);

        // Desktop shortcut option
        shortcutCheck = new JCheckBox("Add a KTU Scholar shortcut to my Desktop", true);
        shortcutCheck.setOpaque(false);
        shortcutCheck.setForeground(FG);
        shortcutCheck.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        shortcutCheck.setFocusPainted(false);
        shortcutCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(shortcutCheck);
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
        String email = emailField.getText().trim();
        if (!email.isEmpty() && !email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
            JOptionPane.showMessageDialog(this, "Please enter a valid Email ID (or leave it blank).");
            return false;
        }
        return true;
    }

    private void finish() {
        Profile p = new Profile();
        p.setName(nameField.getText().trim());
        p.setRegisterNumber(regNumField.getText().trim());
        p.setEzygoId(ezygoIdField.getText().trim());   // NEW – add to Profile
        p.setEmail(emailField.getText().trim());       // NEW – add to Profile
        p.setDateOfBirth(dobField.getText().trim());
        p.setBranch(branchField.getText().trim());
        p.setSemester((int) semesterSpinner.getValue());
        p.setCollege(collegeField.getText().trim());

        char[] ktu = ktuPassField.getPassword();
        if (ktu.length > 0) p.setKtuPassword(new String(ktu));
        Arrays.fill(ktu, '\0');
        char[] ezygo = ezygoPassField.getPassword();
        if (ezygo.length > 0) p.setEzygoPassword(new String(ezygo));
        Arrays.fill(ezygo, '\0');

        try {
            new ProfileRepository(db.getConnection()).saveProfile(p);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to save profile: " + e.getMessage());
            return;
        }

        if (shortcutCheck.isSelected()) {
            createShortcutAndGuide();
        }
        dispose();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Desktop shortcut (Windows)
    //  Windows does not let apps pin themselves to the taskbar, so we create
    //  a Desktop shortcut and tell the user to right-click -> Pin to taskbar.
    // ══════════════════════════════════════════════════════════════════════

    private void createShortcutAndGuide() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            Path lnk = createDesktopShortcut(SHORTCUT_NAME);
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this,
                    "A \"" + SHORTCUT_NAME + "\" shortcut was added to your Desktop.\n\n" +
                    "To pin it to the taskbar: right-click it and choose\n" +
                    "\"Pin to taskbar\" (Windows 11: \"Show more options\" first).",
                    "Shortcut created", JOptionPane.INFORMATION_MESSAGE);
            try {   // highlight the shortcut in Explorer
                new ProcessBuilder("explorer.exe", "/select,", lnk.toString()).start();
            } catch (Exception ignored) { }
        } catch (Exception e) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this,
                    "Your profile was saved, but the Desktop shortcut could not be created:\n" +
                    e.getMessage(),
                    "Shortcut not created", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static Path createDesktopShortcut(String shortcutName) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            throw new UnsupportedOperationException("Desktop shortcuts are only supported on Windows.");
        }

        // The jar this app is currently running from
        Path jar = Paths.get(OnboardingDialog.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isRegularFile(jar) || !jar.toString().toLowerCase().endsWith(".jar")) {
            throw new IllegalStateException(
                    "The app is not running from a .jar (probably started from the IDE or Gradle), "
                    + "so no shortcut was created.");
        }

        // javaw = launches without a console window
        Path javaw = Paths.get(System.getProperty("java.home"), "bin", "javaw.exe");

        // Temp PowerShell script; paths are passed as parameters to avoid quoting problems
        String ps =
                "param($Name,$Javaw,$Jar,$WorkDir)\r\n" +
                "$desktop = [Environment]::GetFolderPath('Desktop')\r\n" +
                "$lnk = Join-Path $desktop ($Name + '.lnk')\r\n" +
                "$s = (New-Object -ComObject WScript.Shell).CreateShortcut($lnk)\r\n" +
                "$s.TargetPath = $Javaw\r\n" +
                "$s.Arguments = '-jar \"' + $Jar + '\"'\r\n" +
                "$s.WorkingDirectory = $WorkDir\r\n" +
                "$s.IconLocation = $Javaw\r\n" +
                "$s.Description = $Name\r\n" +
                "$s.Save()\r\n" +
                "Write-Output $lnk\r\n";

        Path script = Files.createTempFile("ktu-shortcut", ".ps1");
        try {
            Files.write(script, ps.getBytes(StandardCharsets.UTF_8));

            Process proc = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.toString(),
                    shortcutName, javaw.toString(), jar.toString(), jar.getParent().toString())
                    .redirectErrorStream(true)
                    .start();

            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!proc.waitFor(15, TimeUnit.SECONDS) || proc.exitValue() != 0) {
                throw new IllegalStateException("PowerShell failed: " + output);
            }
            return Paths.get(output);
        } finally {
            Files.deleteIfExists(script);
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

    private JTextField addField(JPanel parent, String label) {
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
