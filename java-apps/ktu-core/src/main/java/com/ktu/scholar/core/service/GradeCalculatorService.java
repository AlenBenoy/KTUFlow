package com.ktu.scholar.core.service;

import com.ktu.scholar.core.model.SubjectResult;
import com.ktu.scholar.core.model.SemesterResult;

import java.util.*;

/**
 * Implements KTU 2024 grading scheme.
 *
 * Grade point table:
 *  S   → 10   (90–100)
 *  A+  →  9   (85–89)
 *  A   →  8.5 (80–84)
 *  B+  →  8   (75–79)
 *  B   →  7   (70–74)
 *  C+  →  6   (65–69)
 *  C   →  5   (60–64)
 *  D   →  4   (50–59)
 *  F   →  0   (0–49)
 *
 * SGPA  = Σ(Credits × GradePoints) / Σ(Credits)
 * Percentage = (CGPA − 0.5) × 10
 */
public class GradeCalculatorService {

    private static final Map<String, Double> GRADE_POINTS = new LinkedHashMap<>();

    static {
        GRADE_POINTS.put("S",  10.0);
        GRADE_POINTS.put("A+",  9.0);
        GRADE_POINTS.put("A",   8.5);
        GRADE_POINTS.put("B+",  8.0);
        GRADE_POINTS.put("B",   7.0);
        GRADE_POINTS.put("C+",  6.0);
        GRADE_POINTS.put("C",   5.0);
        GRADE_POINTS.put("D",   4.0);
        GRADE_POINTS.put("F",   0.0);
        GRADE_POINTS.put("FE",  0.0);
        GRADE_POINTS.put("I",   0.0);
        GRADE_POINTS.put("W",   0.0);
    }

    /** Returns the grade point for a given grade string (case-insensitive). */
    public static double gradePoint(String grade) {
        return GRADE_POINTS.getOrDefault(grade.toUpperCase().trim(), 0.0);
    }

    /** Computes SGPA for a list of subjects. */
    public static double calculateSgpa(List<SubjectResult> subjects) {
        double totalPoints = 0;
        double totalCredits = 0;
        for (SubjectResult s : subjects) {
            double gp = gradePoint(s.getGrade());
            totalPoints  += s.getCredits() * gp;
            totalCredits += s.getCredits();
        }
        return totalCredits > 0 ? round2(totalPoints / totalCredits) : 0;
    }

    /** Computes CGPA from a list of semester results. */
    public static double calculateCgpa(List<SemesterResult> semesters) {
        double totalPoints  = 0;
        double totalCredits = 0;
        for (SemesterResult sem : semesters) {
            for (SubjectResult s : sem.getSubjects()) {
                totalPoints  += s.getCredits() * gradePoint(s.getGrade());
                totalCredits += s.getCredits();
            }
        }
        return totalCredits > 0 ? round2(totalPoints / totalCredits) : 0;
    }

    /** Converts CGPA to percentage using KTU 2024 formula: (CGPA − 0.5) × 10 */
    public static double cgpaToPercentage(double cgpa) {
        return Math.max(0, round2((cgpa - 0.5) * 10));
    }

    public static String classify(double cgpa) {
        if (cgpa >= 9.0) return "First Class with Distinction";
        if (cgpa >= 7.5) return "First Class";
        if (cgpa >= 6.5) return "Second Class";
        if (cgpa >= 5.5) return "Pass";
        return "Fail";
    }

    /** Returns all grades in display order with metadata. */
    public static List<Map<String, Object>> getGradingScheme() {
        List<Map<String, Object>> scheme = new ArrayList<>();
        String[][] data = {
            {"S",  "10.0", "90–100", "Outstanding"},
            {"A+",  "9.0", "85–89",  "Excellent"},
            {"A",   "8.5", "80–84",  "Very Good"},
            {"B+",  "8.0", "75–79",  "Good"},
            {"B",   "7.0", "70–74",  "Above Average"},
            {"C+",  "6.0", "65–69",  "Average"},
            {"C",   "5.0", "60–64",  "Satisfactory"},
            {"D",   "4.0", "50–59",  "Pass"},
            {"F",   "0.0", "0–49",   "Fail"},
        };
        for (String[] row : data) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("grade", row[0]);
            entry.put("gradePoint", Double.parseDouble(row[1]));
            entry.put("percentageRange", row[2]);
            entry.put("description", row[3]);
            scheme.add(entry);
        }
        return scheme;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
