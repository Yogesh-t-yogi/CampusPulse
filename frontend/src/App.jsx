
import { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import api, { INSTITUTION_KEY, PASSWORD_CHANGE_EVENT, SESSION_EXPIRED_EVENT } from "./apiClient";
import ChangePassword from "./pages/ChangePassword";

import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";
import Home from "./pages/Home";
import Syllabi from "./pages/Syllabi";
import JobCatalog from "./pages/JobCatalog";
import Organisation from "./pages/Organisation";
import StudentHome from "./pages/StudentHome";
import DepartmentDashboard from "./pages/DepartmentDashboard";
import StudentProfile from "./pages/StudentProfile";
import LearningPlan from "./pages/LearningPlan";
import ResumeMatch from "./pages/ResumeMatch";
import MarketInsights from "./pages/MarketInsights";
import Curriculum from "./pages/Curriculum";
import JobMarket from "./pages/JobMarket";
import Report from "./pages/Report";
import Users from "./pages/Users";
import { canSeePage } from "./permissions";
import SyllabusUpload from "./pages/SyllabusUpload";
import Login from "./pages/Login";
import Register from "./pages/Register";
import WorkflowSteps from "./components/WorkflowSteps";
import "./App.css";
import "./FeatureAdditions.css";
import "./theme/components.css";
import "./ui/ui.css";
import "./components/shell.css";
import { Alert, EmptyState, Page } from "./ui";
import { ShieldAlert } from "lucide-react";
import { setMode } from "./theme/theme";

// Each page has its own URL, so refresh and the back button work.
const PAGE_PATHS = {
  Home: "/home",
  Syllabi: "/syllabi",
  "Upload Syllabus": "/upload",
  Curriculum: "/curriculum",
  "Job Market": "/jobs",
  Report: "/report",
  "Market insights": "/insights",
  "Resume match": "/resume",
  "Job catalog": "/catalog",
  Organisation: "/organisation",
  "My profile": "/profile",
  "Department dashboard": "/dashboard-department",
  "Learning plan": "/learning",
  Users: "/users",
};

const MUST_CHANGE_KEY = "campuspulse_must_change";

// Header title per page.
const PAGE_TITLES = {
  "Upload Syllabus": "Upload syllabus",
  Curriculum: "Review curriculum",
  "Job Market": "Choose job",
};

// Pages that show the analysis step bar.
const WORKFLOW_PAGES = new Set(["Upload Syllabus", "Curriculum", "Job Market", "Report"]);

// Old page URLs now open the matching Report tab.
const LEGACY_PATHS = {
  "/dashboard": "/report?tab=overview",
  "/gap-analysis": "/report?tab=skills",
  "/recommendations": "/report?tab=recommendations",
};

function pageFromPath(pathname) {
  const match = Object.entries(PAGE_PATHS).find(
    ([, path]) => pathname === path || pathname.startsWith(`${path}/`)
  );
  return match ? match[0] : null;
}

function getSyllabusId(payload) {
  if (!payload || typeof payload !== "object") return "";

  const candidates = [
    payload.id,
    payload.syllabusId,
    payload.syllabus?.id,
    payload.data?.id,
    payload.data?.syllabusId,
    payload.data?.syllabus?.id,
  ];

  const id = candidates.find(
    (value) =>
      value !== undefined &&
      value !== null &&
      String(value).trim() !== ""
  );

  return id === undefined ? "" : String(id);
}

function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const defaultPage = "Home";
  const activePage = pageFromPath(location.pathname) || defaultPage;
  const setActivePage = useCallback(
    (page) => navigate(PAGE_PATHS[page] || "/upload"),
    [navigate]
  );
  const [sessionNotice, setSessionNotice] = useState("");
  const [currentUser, setCurrentUser] = useState(null);
  const role = currentUser?.role;
  const isAdmin = role === "ADMIN" || role === "SUPER_ADMIN";
  // Platform admins pick which college they work in (sent as X-Institution-Id).
  const [institutions, setInstitutions] = useState([]);
  const [actingInstitutionId, setActingInstitutionId] = useState(
    () => sessionStorage.getItem(INSTITUTION_KEY) || ""
  );
  const [pageVersion, setPageVersion] = useState(0);
  // One-time password: the user must set their own before using the app.
  const [mustChangePassword, setMustChangePassword] = useState(
    () => sessionStorage.getItem(MUST_CHANGE_KEY) === "1"
  );

  const [token, setToken] = useState(
    () => sessionStorage.getItem("campuspulse_token")
  );

  const [authPage, setAuthPage] = useState("login");
  const [syllabi, setSyllabi] = useState([]);
  const [jobs, setJobs] = useState([]);

  const [selectedSyllabusId, setSelectedSyllabusId] = useState(
    () => sessionStorage.getItem("campuspulse_syllabus_id") || ""
  );
  const [selectedJobId, setSelectedJobId] = useState(
    () => sessionStorage.getItem("campuspulse_job_id") || ""
  );

  const [syllabiLoading, setSyllabiLoading] = useState(false);
  const [syllabiError, setSyllabiError] = useState("");
  const [, setJobsLoading] = useState(false);
  const [jobsError, setJobsError] = useState("");
  const [workflowNotice, setWorkflowNotice] = useState("");

  const handleLogin = (jwtToken, mustChange = false) => {
    sessionStorage.setItem("campuspulse_token", jwtToken);
    if (mustChange) sessionStorage.setItem(MUST_CHANGE_KEY, "1");
    else sessionStorage.removeItem(MUST_CHANGE_KEY);
    setMustChangePassword(mustChange);
    sessionStorage.removeItem("campuspulse_syllabus_id");
    sessionStorage.removeItem("campuspulse_job_id");

    setToken(jwtToken);
    setSessionNotice("");
    setWorkflowNotice("");
    setSelectedSyllabusId("");
    setSelectedJobId("");
    setActivePage("Home");
  };

  const handleLogout = useCallback(() => {
    sessionStorage.removeItem("campuspulse_token");
    sessionStorage.removeItem(INSTITUTION_KEY);
    sessionStorage.removeItem(MUST_CHANGE_KEY);
    setMustChangePassword(false);
    sessionStorage.removeItem("campuspulse_syllabus_id");
    sessionStorage.removeItem("campuspulse_job_id");

    setToken(null);
    setCurrentUser(null);
    setWorkflowNotice("");
    setSyllabi([]);
    setJobs([]);
    setSelectedSyllabusId("");
    setSelectedJobId("");
    setAuthPage("login");
    setActivePage("Home");
  }, [setActivePage]);

  // Any API call that gets HTTP 401 ends the session in one place.
  useEffect(() => {
    const onExpired = () => {
      if (!sessionStorage.getItem("campuspulse_token")) return;
      handleLogout();
      setSessionNotice("Your session expired. Sign in again.");
    };
    const onMustChange = () => {
      sessionStorage.setItem(MUST_CHANGE_KEY, "1");
      setMustChangePassword(true);
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    window.addEventListener(PASSWORD_CHANGE_EVENT, onMustChange);
    return () => {
      window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
      window.removeEventListener(PASSWORD_CHANGE_EVENT, onMustChange);
    };
  }, [handleLogout]);

  // Bare "/" or unknown paths go to the default page.
  useEffect(() => {
    if (token && !pageFromPath(location.pathname)) {
      navigate(LEGACY_PATHS[location.pathname] || PAGE_PATHS[defaultPage], {
        replace: true,
      });
    }
  }, [token, location.pathname, navigate, defaultPage]);

  // Fetch the logged-in user's syllabi.
  // After upload, prefer the newly created syllabus ID.
  const refreshSyllabi = useCallback(
    async (preferredId = "", selectFirstAfterUpload = false) => {
      if (!token) {
        setSyllabi([]);
        setSelectedSyllabusId("");
        setSyllabiLoading(false);
        return;
      }

      setSyllabiLoading(true);
      setSyllabiError("");

      try {
        const response = await api.get(
          `/api/syllabus`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          }
        );

        const userSyllabi = Array.isArray(response.data)
          ? response.data
          : [];

        setSyllabi(userSyllabi);

        const savedId =
          sessionStorage.getItem("campuspulse_syllabus_id") || "";

        const preferredExists =
          preferredId &&
          userSyllabi.some(
            (item) => String(item.id) === String(preferredId)
          );

        const savedExists =
          savedId &&
          userSyllabi.some(
            (item) => String(item.id) === String(savedId)
          );

        const nextId = preferredExists
          ? String(preferredId)
          : selectFirstAfterUpload && userSyllabi.length
            ? String(userSyllabi[0].id)
            : savedExists
              ? savedId
              : userSyllabi.length
                ? String(userSyllabi[0].id)
                : "";

        setSelectedSyllabusId(nextId);

        if (nextId) {
          sessionStorage.setItem("campuspulse_syllabus_id", nextId);
        } else {
          sessionStorage.removeItem("campuspulse_syllabus_id");
        }
      } catch (error) {
        console.error("Failed to load syllabi:", {
          message: error.message,
          status: error.response?.status,
          response: error.response?.data,
          url: error.config?.url,
        });

        setSyllabiError(
          error.response?.status === 401
            ? "Your session may have expired. Please log in again."
            : error.response?.status === 403
              ? "Access denied. Please check your account permissions."
              : !error.response
                ? "Cannot connect to the backend. Check port 8081."
                : "Failed to load your syllabi."
        );
      } finally {
        setSyllabiLoading(false);
      }
    },
    [token]
  );

  const refreshJobs = useCallback(async () => {
    if (!token) {
      setJobs([]);
      setJobsLoading(false);
      return;
    }

    setJobsLoading(true);
    setJobsError("");

    try {
      const response = await api.get(`/api/jobs`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const userJobs = Array.isArray(response.data) ? response.data : [];
      const savedId = sessionStorage.getItem("campuspulse_job_id") || "";
      const savedExists = userJobs.some(
        (job) => String(job.id) === String(savedId)
      );
      // Never pick a job automatically: choosing one is a workflow step.
      const nextId = savedExists ? savedId : "";

      setJobs(userJobs);
      setSelectedJobId(nextId);
      if (nextId) {
        sessionStorage.setItem("campuspulse_job_id", nextId);
      } else {
        sessionStorage.removeItem("campuspulse_job_id");
      }
    } catch (error) {
      console.error("Failed to load job postings:", {
        message: error.message,
        status: error.response?.status,
        url: error.config?.url,
      });
      setJobs([]);
      setSelectedJobId("");
      setJobsError(
        error.response?.status === 401
          ? "Your session may have expired. Please sign in again."
          : !error.response
            ? "Cannot connect to the backend. Check port 8081."
            : "Unable to load job profiles."
      );
    } finally {
      setJobsLoading(false);
    }
  }, [token]);

  // Who is logged in (role decides whether admin tools are shown).
  useEffect(() => {
    if (!token) return undefined;
    let cancelled = false;
    api
      .get("/api/auth/me")
      .then(({ data }) => {
        if (cancelled) return;
        setCurrentUser(data);
        if (data.mustChangePassword) {
          sessionStorage.setItem(MUST_CHANGE_KEY, "1");
          setMustChangePassword(true);
        }
        // Students and faculty always see their own mode; admins can switch.
        if (data.role === "SUPER_ADMIN") {
          api
            .get("/api/org/institutions")
            .then((response) => !cancelled && setInstitutions(response.data || []))
            .catch(() => !cancelled && setInstitutions([]));
        }
        // Colours follow the role: teal for students, indigo for faculty/admin.
        setMode(data.role === "STUDENT" ? "student" : "faculty");
      })
      .catch(() => !cancelled && setCurrentUser(null));
    return () => {
      cancelled = true;
    };
  }, [token]);

  // Load the user's saved analysis inputs after login.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      void refreshSyllabi();
      void refreshJobs();
    }, 0);

    return () => window.clearTimeout(timer);
  }, [refreshSyllabi, refreshJobs]);

  const changeInstitution = (id) => {
    if (id) sessionStorage.setItem(INSTITUTION_KEY, String(id));
    else sessionStorage.removeItem(INSTITUTION_KEY);
    setActingInstitutionId(id ? String(id) : "");
    setPageVersion((value) => value + 1);
    void refreshJobs();
  };

  const selectSyllabus = (id) => {
    const value = id ? String(id) : "";
    setSelectedSyllabusId(value);
    setWorkflowNotice("");
    if (value) sessionStorage.setItem("campuspulse_syllabus_id", value);
    else sessionStorage.removeItem("campuspulse_syllabus_id");
  };

  const handleSyllabusChange = (event) => {
    const id = event.target.value;

    setSelectedSyllabusId(id);
    setWorkflowNotice("");

    if (id) {
      sessionStorage.setItem("campuspulse_syllabus_id", id);
    } else {
      sessionStorage.removeItem("campuspulse_syllabus_id");
    }
  };

  const handleJobChange = (event) => {
    const id = event.target.value;
    setSelectedJobId(id);
    if (id) {
      sessionStorage.setItem("campuspulse_job_id", id);
    } else {
      sessionStorage.removeItem("campuspulse_job_id");
    }
  };

  const handleJobSelected = (job) => {
    const id = String(job.id);
    setSelectedJobId(id);
    sessionStorage.setItem("campuspulse_job_id", id);
    setActivePage("Report");
  };

  const handleJobCreated = async (job) => {
    await refreshJobs();
    const id = String(job.id);
    setSelectedJobId(id);
    sessionStorage.setItem("campuspulse_job_id", id);
    setActivePage("Report");
  };

  // Called after the PDF upload succeeds.
  const handleUploaded = async (uploadResponse, navigate = true) => {
    const uploadedId = getSyllabusId(uploadResponse);
    await refreshSyllabi(uploadedId, true);
    setWorkflowNotice(
      uploadResponse.processing?.embeddingWarning || ""
    );
    if (navigate) {
      setActivePage("Curriculum");
    }
  };

  if (!token) {
    if (authPage === "register") {
      return (
        <Register
          onRegistered={() => setAuthPage("login")}
          onShowLogin={() => setAuthPage("login")}
        />
      );
    }

    return (
      <Login
        notice={sessionNotice}
        onLogin={handleLogin}
        onShowRegister={() => setAuthPage("register")}
      />
    );
  }

  if (mustChangePassword) {
    return (
      <ChangePassword
        user={currentUser}
        onLogout={handleLogout}
        onChanged={() => {
          sessionStorage.removeItem(MUST_CHANGE_KEY);
          setMustChangePassword(false);
          setPageVersion((value) => value + 1);
          void refreshSyllabi();
          void refreshJobs();
        }}
      />
    );
  }

  const selectedSyllabusName = syllabi.find(
    (item) => String(item.id) === String(selectedSyllabusId)
  )?.name;
  const selectedJobTitle = jobs.find(
    (item) => String(item.id) === String(selectedJobId)
  )?.title;

  const renderPage = () => {
    // Role check (the backend enforces the same rules).
    if (currentUser && !canSeePage(role, activePage)) {
      return (
        <Page>
          <EmptyState
            icon={ShieldAlert}
            title="Not available for your role"
            description="This page belongs to another role. Use the menu on the left."
          />
        </Page>
      );
    }

    switch (activePage) {
      case "Home":
        if (role === "STUDENT") {
          return <StudentHome user={currentUser} setActivePage={setActivePage} />;
        }
        return (
          <Home
            user={currentUser}
            syllabi={syllabi}
            jobs={jobs}
            selectedSyllabusId={selectedSyllabusId}
            selectedJobId={selectedJobId}
            setActivePage={setActivePage}
          />
        );

      case "Syllabi":
        return (
          <Syllabi
            syllabi={syllabi}
            loading={syllabiLoading}
            error={syllabiError}
            selectedSyllabusId={selectedSyllabusId}
            onSelect={selectSyllabus}
            onChanged={() => refreshSyllabi()}
            setActivePage={setActivePage}
            currentUser={currentUser}
          />
        );

      case "Upload Syllabus":
        return (
          <SyllabusUpload
            token={token}
            currentUser={currentUser}
            onUploaded={handleUploaded}
          />
        );

      case "Curriculum":
        return (
          <Curriculum
            key={selectedSyllabusId}
            syllabusId={selectedSyllabusId}
            onNext={() => setActivePage("Job Market")}
            canManage={
              syllabi.find((item) => String(item.id) === String(selectedSyllabusId))?.canManage !== false
            }
          />
        );

      case "Job Market":
        return (
          <JobMarket
            key="choose"
            section="choose"
            isAdmin={isAdmin}
            selectedJobId={selectedJobId}
            hasSyllabus={Boolean(selectedSyllabusId)}
            onJobCreated={handleJobCreated}
            onJobSelected={handleJobSelected}
            onCatalogImported={refreshJobs}
          />
        );

      case "Market insights":
        return <MarketInsights user={currentUser} selectedSyllabusId={selectedSyllabusId} syllabi={syllabi} />;

      case "Resume match":
        return <ResumeMatch setActivePage={setActivePage} />;

      case "My profile":
        return <StudentProfile user={currentUser} jobs={jobs} syllabi={syllabi} onChanged={() => {}} />;

      case "Department dashboard":
        return <DepartmentDashboard user={currentUser} jobs={jobs} />;

      case "Learning plan":
        return <LearningPlan setActivePage={setActivePage} />;

      case "Organisation":
        return (
          <Organisation
            user={currentUser}
            onChanged={() =>
              currentUser?.role === "SUPER_ADMIN" &&
              api.get("/api/org/institutions").then((response) => setInstitutions(response.data || []))
            }
          />
        );

      case "Users":
        return <Users currentUser={currentUser} />;

      case "Job catalog":
        return <JobCatalog onChanged={refreshJobs} />;

      case "Report":
      default:
        return (
          <Report
            key={`${selectedSyllabusId}-${selectedJobId}`}
            syllabusId={selectedSyllabusId}
            jobId={selectedJobId}
            token={token}
            syllabusName={selectedSyllabusName}
            jobTitle={selectedJobTitle}
            role={role}
            jobs={jobs}
          />
        );
    }
  };

  const pageTitle = PAGE_TITLES[activePage] || activePage;

  return (
    <div className="shell">
      <Sidebar
        activePage={activePage}
        setActivePage={setActivePage}
        hasSyllabus={Boolean(selectedSyllabusId)}
        hasJob={Boolean(selectedJobId)}
        role={role}
      />

      <div className="shell-main">
        <Topbar
          title={pageTitle}
          user={currentUser}
          onLogout={handleLogout}
          syllabi={Array.isArray(syllabi) ? syllabi : []}
          selectedSyllabusId={selectedSyllabusId}
          onSyllabusChange={handleSyllabusChange}
          jobs={jobs}
          selectedJobId={selectedJobId}
          onJobChange={handleJobChange}
          institutions={institutions}
          actingInstitutionId={actingInstitutionId}
          onInstitutionChange={changeInstitution}
        />

        {WORKFLOW_PAGES.has(activePage) && (
          <WorkflowSteps
            activePage={activePage}
            setActivePage={setActivePage}
            hasSyllabus={Boolean(selectedSyllabusId)}
            hasJob={Boolean(selectedJobId)}
          />
        )}

        {(workflowNotice || syllabiError || jobsError) && (
          <div className="shell-notice">
            <Alert tone="warning">{workflowNotice || syllabiError || jobsError}</Alert>
          </div>
        )}

        <div key={`${actingInstitutionId}-${pageVersion}`} className="shell-page">
          {renderPage()}
        </div>
      </div>
    </div>
  );
}

export default App;
