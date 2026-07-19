package com.ktu.scholar.desktop;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.ktu.scholar.core.db.DatabaseHelper;
import com.ktu.scholar.core.db.ProfileRepository;
import com.ktu.scholar.core.model.Profile;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.sql.SQLException;

/**
 * Application entry point.
 * Initialises FlatLaf dark theme, opens the SQLite database,
 * and shows either the onboarding wizard or the main window.
 */
public class Main {

    public static void main(String[] args) {
        // ── Install FlatLaf dark theme ──────────────────────────────────
        FlatDarkLaf.setup();

        // Override accent colour to KTU amber/gold
        UIManager.put("Component.accentColor",         new Color(0xF59E0B));
        UIManager.put("Button.arc",                    12);
        UIManager.put("Component.arc",                 12);
        UIManager.put("ProgressBar.arc",               6);
        UIManager.put("TextComponent.arc",             8);
        UIManager.put("TabbedPane.selectedBackground", new Color(0x1E3A5F));

        // ── Bootstrap app data directory ────────────────────────────────
        String appDir = System.getProperty("user.home") + File.separator + ".ktu-scholar";
        new File(appDir).mkdirs();

        // ── Open database ───────────────────────────────────────────────
        DatabaseHelper db;
        try {
            db = DatabaseHelper.getInstance(appDir);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null,
                    "Failed to open database:\n" + e.getMessage(),
                    "KTU Scholar — Database Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }

        // ── Launch UI on the EDT ────────────────────────────────────────
        final DatabaseHelper finalDb = db;
        SwingUtilities.invokeLater(() -> {
            try {
                Profile profile = new ProfileRepository(finalDb.getConnection()).getProfile();
                if (profile == null) {
                    // First-run: show onboarding wizard
                    OnboardingDialog onboarding = new OnboardingDialog(null, finalDb);
                    onboarding.setVisible(true);
                    profile = new ProfileRepository(finalDb.getConnection()).getProfile();
                    if (profile == null) {
                        // User closed wizard without completing it
                        finalDb.close();
                        System.exit(0);
                    }
                }
                new MainFrame(finalDb).setVisible(true);
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(null,
                        "Startup error:\n" + e.getMessage(),
                        "KTU Scholar",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
