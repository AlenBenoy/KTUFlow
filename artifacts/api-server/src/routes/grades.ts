import { Router } from "express";

const router = Router();

// KTU 2024 scheme grade points
const KTU_2024_GRADES: Record<string, { point: number; percentageRange: string; description: string }> = {
  S:  { point: 10, percentageRange: "90–100", description: "Outstanding" },
  "A+": { point: 9,  percentageRange: "85–89",  description: "Excellent" },
  A:  { point: 8.5, percentageRange: "80–84",  description: "Very Good" },
  "B+": { point: 8,  percentageRange: "75–79",  description: "Good" },
  B:  { point: 7,  percentageRange: "70–74",  description: "Above Average" },
  "C+": { point: 6,  percentageRange: "65–69",  description: "Average" },
  C:  { point: 5,  percentageRange: "60–64",  description: "Satisfactory" },
  D:  { point: 4,  percentageRange: "50–59",  description: "Pass" },
  F:  { point: 0,  percentageRange: "0–49",   description: "Fail" },
  FE: { point: 0,  percentageRange: "—",       description: "Fail (Eligibility)" },
  I:  { point: 0,  percentageRange: "—",       description: "Incomplete" },
};

function classify(sgpa: number): string {
  if (sgpa >= 9.0) return "First Class with Distinction";
  if (sgpa >= 7.5) return "First Class";
  if (sgpa >= 6.5) return "Second Class";
  if (sgpa >= 5.5) return "Pass";
  return "Fail";
}

// CGPA to percentage: (CGPA - 0.5) × 10  — KTU 2024 formula
function cgpaToPercentage(cgpa: number): number {
  return Math.max(0, (cgpa - 0.5) * 10);
}

// POST /api/grades/calculate
router.post("/grades/calculate", (req, res) => {
  try {
    const { subjects } = req.body as {
      subjects: Array<{ subjectCode?: string; subjectName?: string; credits: number; grade: string }>;
    };

    if (!subjects || subjects.length === 0) {
      res.status(400).json({ error: "validation_error", message: "subjects array is required" });
      return;
    }

    let totalCreditPoints = 0;
    let totalCredits = 0;
    let earnedCredits = 0;

    const breakdown = subjects.map((s) => {
      const gradeInfo = KTU_2024_GRADES[s.grade.toUpperCase()] ?? KTU_2024_GRADES["F"]!;
      const gradePoints = s.credits * gradeInfo.point;
      totalCreditPoints += gradePoints;
      totalCredits += s.credits;
      if (gradeInfo.point >= 4) earnedCredits += s.credits;
      return {
        subjectCode: s.subjectCode,
        subjectName: s.subjectName,
        credits: s.credits,
        grade: s.grade.toUpperCase(),
        gradePoint: gradeInfo.point,
        gradePoints,
      };
    });

    const sgpa = totalCredits > 0 ? totalCreditPoints / totalCredits : 0;
    const percentage = cgpaToPercentage(sgpa);
    const classification = classify(sgpa);

    res.json({
      sgpa: Math.round(sgpa * 100) / 100,
      totalCredits,
      earnedCredits,
      percentage: Math.round(percentage * 100) / 100,
      classification,
      subjectBreakdown: breakdown,
    });
  } catch (err) {
    res.status(500).json({ error: "internal_error", message: "Failed to calculate GPA" });
  }
});

// GET /api/grades/scheme
router.get("/grades/scheme", (_req, res) => {
  const grades = Object.entries(KTU_2024_GRADES).map(([grade, info]) => ({
    grade,
    gradePoint: info.point,
    percentageRange: info.percentageRange,
    description: info.description,
  }));

  res.json({
    scheme: "KTU 2024 Grading Scheme",
    grades,
    formula: "SGPA = Σ(Credits × Grade Points) / Σ(Credits)",
    note: "Percentage = (CGPA − 0.5) × 10 as per KTU 2024 scheme",
  });
});

export default router;
