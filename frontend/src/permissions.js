// What each role can see. The backend enforces the same rules
// (SecurityConfig, TenantService, JobPostingService); this only shapes the UI.
// Roles: SUPER_ADMIN (platform) > ADMIN (college) > HOD (department) > FACULTY > STUDENT.
const ALL = ["SUPER_ADMIN", "ADMIN", "HOD", "FACULTY", "STUDENT"];
const STAFF = ["SUPER_ADMIN", "ADMIN", "HOD", "FACULTY"];

const PAGE_ROLES = {
  Home: ALL,
  Syllabi: ALL,
  "Upload Syllabus": ALL,
  Curriculum: ALL,
  "Job Market": ALL,
  Report: ALL,
  "Market insights": ALL,
  "Resume match": ["SUPER_ADMIN", "ADMIN", "STUDENT"],
  "My profile": ["STUDENT"],
  "Learning plan": ["STUDENT"],
  "Job catalog": ["SUPER_ADMIN", "ADMIN"],
  Organisation: ["SUPER_ADMIN", "ADMIN", "HOD"],
  Users: ["SUPER_ADMIN", "ADMIN", "HOD"],
};

const FEATURE_ROLES = {
  curriculumRecommendations: STAFF,
  learningRoadmap: ["SUPER_ADMIN", "ADMIN", "STUDENT"],
  manageCatalog: ["SUPER_ADMIN", "ADMIN"],
  manageColleges: ["SUPER_ADMIN"],
  manageDepartments: ["SUPER_ADMIN", "ADMIN"],
  managePrograms: ["SUPER_ADMIN", "ADMIN", "HOD"],
};

const RANK = { SUPER_ADMIN: 4, ADMIN: 3, HOD: 2, FACULTY: 1, STUDENT: 0 };

export function canSeePage(role, page) {
  return (PAGE_ROLES[page] || []).includes(role);
}

export function can(role, feature) {
  return (FEATURE_ROLES[feature] || []).includes(role);
}

/** Roles this user may give to others (mirrors TenantService.requireCanAssignRole). */
export function assignableRoles(role) {
  const mine = RANK[role] ?? -1;
  if (mine < RANK.HOD) return [];
  return Object.keys(RANK).filter(
    (other) => RANK[other] < mine || (RANK[other] === mine && mine >= RANK.ADMIN)
  );
}

export function roleLabel(role) {
  return (
    {
      SUPER_ADMIN: "Platform admin",
      ADMIN: "College admin",
      HOD: "HOD",
      FACULTY: "Faculty",
      STUDENT: "Student",
    }[role] || ""
  );
}
