package com.ktu.scholar.core.model;

public class SubjectResult {

    public enum ResultStatus { PASS, FAIL, ABSENT, WITHHELD }

    private String subjectCode;
    private String subjectName;
    private double credits;
    private String grade;
    private double gradePoint;
    private double internalMarks;
    private double externalMarks;
    private double totalMarks;
    private ResultStatus result;

    public SubjectResult() {}

    // ── Getters & Setters ─────────────────────────────────────────────────

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public double getCredits() { return credits; }
    public void setCredits(double credits) { this.credits = credits; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public double getGradePoint() { return gradePoint; }
    public void setGradePoint(double gradePoint) { this.gradePoint = gradePoint; }

    public double getInternalMarks() { return internalMarks; }
    public void setInternalMarks(double internalMarks) { this.internalMarks = internalMarks; }

    public double getExternalMarks() { return externalMarks; }
    public void setExternalMarks(double externalMarks) { this.externalMarks = externalMarks; }

    public double getTotalMarks() { return totalMarks; }
    public void setTotalMarks(double totalMarks) { this.totalMarks = totalMarks; }

    public ResultStatus getResult() { return result; }
    public void setResult(ResultStatus result) { this.result = result; }
}
