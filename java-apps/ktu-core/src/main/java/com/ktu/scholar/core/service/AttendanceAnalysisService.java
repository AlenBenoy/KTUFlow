package com.ktu.scholar.core.service;

import com.ktu.scholar.core.model.SubjectAttendance;
import com.ktu.scholar.core.model.SubjectAttendance.AttendanceStatus;

import java.util.Arrays;
import java.util.List;

/**
 * Analyses attendance data against KTU's 75% minimum requirement.
 * Condonation applies when attendance is 60–74% with a valid reason.
 */
public class AttendanceAnalysisService {

    private static final double MINIMUM_ATTENDANCE   = 75.0;
    private static final double CONDONATION_MIN      = 60.0;

    /**
     * Enriches a SubjectAttendance object with:
     *  - percentage
     *  - status (SAFE / WARNING / DANGER / CONDONATION)
     *  - classesNeeded / classesCanSkip
     *  - recommendation text
     *  - condonation steps (when applicable)
     */
    public static SubjectAttendance analyse(SubjectAttendance s) {
        if (s.getTotalClasses() == 0) {
            s.setPercentage(0);
            s.setStatus(AttendanceStatus.DANGER);
            s.setRecommendation("No class data available.");
            return s;
        }

        double pct = (double) s.getAttendedClasses() / s.getTotalClasses() * 100.0;
        s.setPercentage(Math.round(pct * 10.0) / 10.0);

        // Status classification
        AttendanceStatus status;
        if (pct >= 85) {
            status = AttendanceStatus.SAFE;
        } else if (pct >= MINIMUM_ATTENDANCE) {
            status = AttendanceStatus.WARNING;  // safe but getting close
        } else if (pct >= CONDONATION_MIN) {
            status = AttendanceStatus.DANGER;   // below 75%, not yet in condonation range
        } else {
            status = AttendanceStatus.CONDONATION;
        }
        s.setStatus(status);

        // Classes needed to reach 75%
        int needed = classesNeededFor75(s.getAttendedClasses(), s.getTotalClasses());
        s.setClassesNeeded(Math.max(0, needed));

        // Classes that can be skipped while staying ≥ 75%
        int canSkip = classesCanSkip(s.getAttendedClasses(), s.getTotalClasses());
        s.setClassesCanSkip(Math.max(0, canSkip));

        // Recommendation text
        s.setRecommendation(buildRecommendation(s));

        // Condonation steps
        if (status == AttendanceStatus.CONDONATION) {
            s.setCondonationSteps(condonationSteps());
        }

        return s;
    }

    // ── Calculations ──────────────────────────────────────────────────────

    /**
     * How many consecutive classes must be attended to reach ≥75%:
     * 0.75*(total+x) ≤ attended+x  →  x ≥ (0.75*total − attended) / 0.25
     */
    static int classesNeededFor75(int attended, int total) {
        double needed = (0.75 * total - attended) / 0.25;
        return (int) Math.ceil(needed);
    }

    /**
     * How many classes can be skipped while staying ≥75%:
     * attended/(total+x) ≥ 0.75  →  x ≤ attended/0.75 − total
     */
    static int classesCanSkip(int attended, int total) {
        double canSkip = (double) attended / 0.75 - total;
        return (int) Math.floor(canSkip);
    }

    // ── Text builders ─────────────────────────────────────────────────────

    private static String buildRecommendation(SubjectAttendance s) {
        return switch (s.getStatus()) {
            case SAFE -> {
                if (s.getClassesCanSkip() > 0)
                    yield String.format("Safe — you can miss up to %d more class(es) and stay above 75%%.",
                            s.getClassesCanSkip());
                else
                    yield "You are exactly at 75%. Do not miss any more classes.";
            }
            case WARNING ->
                String.format("Attendance is %.1f%% — just above 75%%. Attend regularly. "
                    + "You can only skip %d more class(es).", s.getPercentage(), s.getClassesCanSkip());
            case DANGER ->
                String.format("Below 75%%! Current: %.1f%%. You must attend the next %d class(es) "
                    + "consecutively to reach the minimum.", s.getPercentage(), s.getClassesNeeded());
            case CONDONATION ->
                String.format("Critically low at %.1f%%. You are eligible to apply for condonation "
                    + "(requires 60–74%% and a valid documented reason). See steps below.",
                    s.getPercentage());
        };
    }

    private static List<String> condonationSteps() {
        return Arrays.asList(
            "1. Obtain supporting documents (medical certificate / valid reason letter).",
            "2. Submit a written application to the College Principal.",
            "3. The Principal forwards the application with a recommendation to KTU.",
            "4. Condonation is applicable ONLY if attendance is between 60% and 74%.",
            "5. Maximum condonation granted: up to 10% of total working days per semester.",
            "6. Pay the condonation fee at the University Cash Counter after approval.",
            "7. Submit the application BEFORE the examination registration deadline.",
            "8. Collect the condonation certificate and submit a copy to the college exam cell."
        );
    }
}
