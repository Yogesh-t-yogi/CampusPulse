// What each role can see. The backend enforces the same rules
// (SecurityConfig + JobPostingService); this only shapes the UI.
const ALL = ["ADMIN", "FACULTY", "STUDENT"];

const PAGE_ROLES = {
  "Upload Syllabus": ALL,
  Curriculum: ALL,
  "Job Market": ALL,
  Report: ALL,
  "Market insights": ALL,
  "Resume match": ["ADMIN", "STUDENT"],
  "Job catalog": ["ADMIN"],
  Users: ["ADMIN"],
};

const FEATURE_ROLES = {
  curriculumRecommendations: ["ADMIN", "FACULTY"],
  learningRoadmap: ["ADMIN", "STUDENT"],
  manageCatalog: ["ADMIN"],
};

export function canSeePage(role, page) {
  return (PAGE_ROLES[page] || []).includes(role);
}

export function can(role, feature) {
  return (FEATURE_ROLES[feature] || []).includes(role);
}

export function roleLabel(role) {
  return { ADMIN: "Admin", FACULTY: "Faculty", STUDENT: "Student" }[role] || "";
}
