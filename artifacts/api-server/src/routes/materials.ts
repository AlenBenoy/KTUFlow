import { Router } from "express";

const router = Router();

// Curated study material links from ktunotes.in and ktuspecial.in
// These map to the 2024 scheme note pages the user specified
const MATERIALS = [
  // Semester 1 & 2 common
  { id: "1", title: "Calculus and its Applications (MAT101)", subjectCode: "MAT101", subjectName: "Calculus and its Applications", semester: 1, type: "notes", url: "https://www.ktunotes.in/ktu-2024-scheme-notes/", source: "KTU Notes", description: "Module-wise notes for MAT101" },
  { id: "2", title: "Engineering Physics (PHT110) Notes", subjectCode: "PHT110", subjectName: "Engineering Physics", semester: 1, type: "notes", url: "https://www.ktunotes.in/ktu-2024-scheme-notes/", source: "KTU Notes", description: "Chapter-wise notes with solved problems" },
  { id: "3", title: "Engineering Physics Previous Year Questions", subjectCode: "PHT110", subjectName: "Engineering Physics", semester: 1, type: "pyq", url: "https://ktuspecial.in/ktunotes/ktu2024schemenotes", source: "KTU Special", description: "Previous year question papers with answers" },
  { id: "4", title: "Problem Solving using Python (CST102) Notes", subjectCode: "CST102", subjectName: "Problem Solving using Python", semester: 2, type: "notes", url: "https://www.ktunotes.in/ktu-2024-scheme-notes/", source: "KTU Notes", description: "Python programming notes for KTU 2024" },
  { id: "5", title: "Basics of Electrical & Electronics Engg", subjectCode: "EST130", subjectName: "Basics of Electrical & Electronics Engineering", semester: 2, type: "notes", url: "https://ktuspecial.in/ktunotes/ktu2024schemenotes", source: "KTU Special", description: "Module-wise notes" },
  // Semester 3
  { id: "6", title: "Data Structures (CST301) Notes", subjectCode: "CST301", subjectName: "Data Structures", semester: 3, type: "notes", url: "https://www.ktunotes.in/ktu-2024-scheme-notes/", source: "KTU Notes", description: "Complete notes with algorithms" },
  { id: "7", title: "Data Structures Previous Year Questions", subjectCode: "CST301", subjectName: "Data Structures", semester: 3, type: "pyq", url: "https://ktuspecial.in/ktunotes/ktu2024schemenotes", source: "KTU Special", description: "2019-2024 question papers" },
  { id: "8", title: "Discrete Mathematical Structures (MAT203)", subjectCode: "MAT203", subjectName: "Discrete Mathematical Structures", semester: 3, type: "notes", url: "https://www.ktunotes.in/ktu-2024-scheme-notes/", source: "KTU Notes", description: "Notes covering graph theory, logic, sets" },
  { id: "9", title: "OOP using Java (CST307) Notes", subjectCode: "CST307", subjectName: "Object Oriented Programming using Java", semester: 3, type: "notes", url: "https://ktuspecial.in/ktunotes/ktu2024schemenotes", source: "KTU Special", description: "Java OOP concepts and programs" },
  { id: "10", title: "Logic System Design (CST303) Notes", subjectCode: "CST303", subjectName: "Logic System Design", semester: 3, type: "notes", url: "https://www.ktunotes.in/ktu-2024-scheme-notes/", source: "KTU Notes", description: "Digital logic and circuit design" },
  // Semester 4
  { id: "11", title: "Graph Theory (MAT206) Notes", subjectCode: "MAT206", subjectName: "Graph Theory", semester: 4, type: "notes", url: "https://www.ktunotes.in/ktu-2024-scheme-notes/", source: "KTU Notes", description: "Graph theory notes for CS students" },
  { id: "12", title: "Operating Systems (CST402) Notes", subjectCode: "CST402", subjectName: "Operating Systems", semester: 4, type: "notes", url: "https://ktuspecial.in/ktunotes/ktu2024schemenotes", source: "KTU Special", description: "Complete OS notes with process scheduling" },
  { id: "13", title: "Operating Systems PYQs", subjectCode: "CST402", subjectName: "Operating Systems", semester: 4, type: "pyq", url: "https://ktuspecial.in/ktunotes/ktu2024schemenotes", source: "KTU Special", description: "Previous year questions 2019-2024" },
  { id: "14", title: "Database Management Systems (CST404) Notes", subjectCode: "CST404", subjectName: "Database Management Systems", semester: 4, type: "notes", url: "https://www.ktunotes.in/ktu-2024-scheme-notes/", source: "KTU Notes", description: "DBMS notes covering SQL, normalization, ER" },
  // Semester 5
  { id: "15", title: "Computer Networks (CST305) Notes", subjectCode: "CST305", subjectName: "Computer Networks", semester: 5, type: "notes", url: "https://www.ktunotes.in/ktu-2024-scheme-notes/", source: "KTU Notes", description: "Networking notes covering OSI, TCP/IP" },
  { id: "16", title: "Compiler Design (CST502) Notes", subjectCode: "CST502", subjectName: "Compiler Design", semester: 5, type: "notes", url: "https://ktuspecial.in/ktunotes/ktu2024schemenotes", source: "KTU Special", description: "Lexical analysis, parsing, code generation" },
  { id: "17", title: "Compiler Design PYQs", subjectCode: "CST502", subjectName: "Compiler Design", semester: 5, type: "pyq", url: "https://ktuspecial.in/ktunotes/ktu2024schemenotes", source: "KTU Special", description: "Previous year question papers" },
  // Semester 6
  { id: "18", title: "Distributed Computing (CST604) Notes", subjectCode: "CST604", subjectName: "Distributed Computing", semester: 6, type: "notes", url: "https://www.ktunotes.in/ktu-2024-scheme-notes/", source: "KTU Notes", description: "Distributed systems and cloud computing" },
  { id: "19", title: "Machine Learning (CST654) Notes", subjectCode: "CST654", subjectName: "Machine Learning", semester: 6, type: "notes", url: "https://ktuspecial.in/ktunotes/ktu2024schemenotes", source: "KTU Special", description: "ML algorithms and applications" },
  // Semester 7 & 8
  { id: "20", title: "Artificial Intelligence (CST402) Notes", subjectCode: "CST402", subjectName: "Artificial Intelligence", semester: 7, type: "notes", url: "https://www.ktunotes.in/ktu-2024-scheme-notes/", source: "KTU Notes", description: "AI search algorithms, knowledge representation" },
  { id: "21", title: "Soft Computing (CST802) Notes", subjectCode: "CST802", subjectName: "Soft Computing", semester: 8, type: "notes", url: "https://ktuspecial.in/ktunotes/ktu2024schemenotes", source: "KTU Special", description: "Fuzzy logic, neural networks, genetic algorithms" },
];

// GET /api/materials
router.get("/materials", (req, res) => {
  const semesterParam = req.query["semester"];
  const typeParam = (req.query["type"] as string) || "all";
  const searchParam = ((req.query["search"] as string) || "").toLowerCase();

  let filtered = MATERIALS;

  if (semesterParam !== undefined) {
    const sem = parseInt(semesterParam as string, 10);
    if (!isNaN(sem)) {
      filtered = filtered.filter((m) => m.semester === sem);
    }
  }

  if (typeParam !== "all") {
    filtered = filtered.filter((m) => m.type === typeParam);
  }

  if (searchParam) {
    filtered = filtered.filter(
      (m) =>
        m.title.toLowerCase().includes(searchParam) ||
        m.subjectName.toLowerCase().includes(searchParam) ||
        m.subjectCode.toLowerCase().includes(searchParam)
    );
  }

  res.json({
    materials: filtered,
    total: filtered.length,
    sources: ["ktunotes.in", "ktuspecial.in"],
  });
});

export default router;
