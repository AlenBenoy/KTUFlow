package com.ktu.scholar.core.model;

public class SubjectAttendance {

    public enum AttendanceStatus { SAFE, WARNING, DANGER, CONDONATION }

    private String subjectCode;
    private String subjectName;
    private int totalClasses;
    private int attendedClasses;
    private double percentage;
    private AttendanceStatus status;
    private int classesNeeded;    // extra classes to attend to reach 75%
    private int classesCanSkip;   // classes that can be skipped and still stay ≥ 75%
    private String recommendation;
    private java.util.List<String> condonationSteps = new java.util.ArrayList<>();

    public SubjectAttendance() {}

    // ── Getters & Setters ─────────────────────────────────────────────────

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String c) { this.subjectCode = c; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String n) { this.subjectName = n; }

    public int getTotalClasses() { return totalClasses; }
    public void setTotalClasses(int t) { this.totalClasses = t; }

    public int getAttendedClasses() { return attendedClasses; }
    public void setAttendedClasses(int a) { this.attendedClasses = a; }

    public double getPercentage() { return percentage; }
    public void setPercentage(double p) { this.percentage = p; }

    public AttendanceStatus getStatus() { return status; }
    public void setStatus(AttendanceStatus s) { this.status = s; }

    public int getClassesNeeded() { return classesNeeded; }
    public void setClassesNeeded(int n) { this.classesNeeded = n; }

    public int getClassesCanSkip() { return classesCanSkip; }
    public void setClassesCanSkip(int n) { this.classesCanSkip = n; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String r) { this.recommendation = r; }

    public java.util.List<String> getCondonationSteps() { return condonationSteps; }
    public void setCondonationSteps(java.util.List<String> s) { this.condonationSteps = s; }
}
