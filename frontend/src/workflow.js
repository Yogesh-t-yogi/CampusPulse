// The analysis flow, shared by the sidebar and the step bar so they always agree.
export const WORKFLOW_STEPS = [
  { page: "Upload Syllabus", label: "Upload syllabus" },
  { page: "Curriculum", label: "Review curriculum" },
  { page: "Job Market", label: "Choose job" },
  { page: "Report", label: "Report" },
];

export function isPageAvailable(page, { hasSyllabus, hasJob }) {
  if (page === "Upload Syllabus") return true;
  if (page === "Curriculum" || page === "Job Market") return hasSyllabus;
  return hasSyllabus && hasJob;
}

export function isStepDone(page, { hasSyllabus, hasJob }) {
  if (page === "Upload Syllabus" || page === "Curriculum") return hasSyllabus;
  if (page === "Job Market") return hasJob;
  return false;
}
