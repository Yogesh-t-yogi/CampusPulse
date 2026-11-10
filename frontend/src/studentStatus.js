// How a skill stands for a student (see StudentService).
export const SKILL_STATUS = {
  HAVE: { label: "You have it", tone: "success" },
  TAUGHT: { label: "Taught in your course", tone: "info" },
  PARTLY: { label: "Partly taught", tone: "warning" },
  TO_LEARN: { label: "To learn", tone: "danger" },
  NOT_TAUGHT: { label: "Not taught", tone: "danger" },
  UNKNOWN: { label: "Choose a syllabus", tone: "neutral" },
};
