package com.ktu.scholar.core.model;

import java.util.List;
import java.util.ArrayList;

public class SemesterResult {
    private int semester;
    private double sgpa;
    private double cgpa;
    private double totalCredits;
    private double earnedCredits;
    private List<SubjectResult> subjects = new ArrayList<>();

    public SemesterResult() {}

    public int getSemester() { return semester; }
    public void setSemester(int semester) { this.semester = semester; }

    public double getSgpa() { return sgpa; }
    public void setSgpa(double sgpa) { this.sgpa = sgpa; }

    public double getCgpa() { return cgpa; }
    public void setCgpa(double cgpa) { this.cgpa = cgpa; }

    public double getTotalCredits() { return totalCredits; }
    public void setTotalCredits(double totalCredits) { this.totalCredits = totalCredits; }

    public double getEarnedCredits() { return earnedCredits; }
    public void setEarnedCredits(double earnedCredits) { this.earnedCredits = earnedCredits; }

    public List<SubjectResult> getSubjects() { return subjects; }
    public void setSubjects(List<SubjectResult> subjects) { this.subjects = subjects; }
    public void addSubject(SubjectResult s) { this.subjects.add(s); }
}
