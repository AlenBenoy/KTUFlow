import { Router, type IRouter } from "express";
import healthRouter from "./health.js";
import profileRouter from "./profile.js";
import todosRouter from "./todos.js";
import gradesRouter from "./grades.js";
import focusRouter from "./focus.js";
import announcementsRouter from "./announcements.js";
import resultsRouter from "./results.js";
import attendanceRouter from "./attendance.js";
import materialsRouter from "./materials.js";

const router: IRouter = Router();

router.use(healthRouter);
router.use(profileRouter);
router.use(todosRouter);
router.use(gradesRouter);
router.use(focusRouter);
router.use(announcementsRouter);
router.use(resultsRouter);
router.use(attendanceRouter);
router.use(materialsRouter);

export default router;
