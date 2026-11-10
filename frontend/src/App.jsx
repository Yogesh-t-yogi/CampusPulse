
import { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import api, { SESSION_EXPIRED_EVENT } from "./apiClient";

import Sidebar from "./components/Sidebar";
import Curriculum from "./pages/Curriculum";
import JobMarket from "./pages/JobMarket";
import Report from "./pages/Report";
import SyllabusUpload from "./pages/SyllabusUpload";
import Login from "./pages/Login";
import Register from "./pages/Register";
import WorkflowSteps from "./components/WorkflowSteps";
import "./App.css";
import "./FeatureAdditions.css";
import "./theme/components.css";
import ThemeControls from "./components/ThemeControls";

// Each page has its own URL, so refresh and the back button work.
const PAGE_PATHS = {
  "Upload Syllabus": "/upload",
  Curriculum: "/curriculum",
  "Job Market": "/jobs",
  Report: "/report",
};

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
  const defaultPage =
    sessionStorage.getItem("campuspulse_syllabus_id") &&
    sessionStorage.getItem("campuspulse_job_id")
      ? "Report"
      : "Upload Syllabus";
  const activePage = pageFromPath(location.pathname) || defaultPage;
  const setActivePage = useCallback(
    (page) => navigate(PAGE_PATHS[page] || "/upload"),
    [navigate]
  );
  const [sessionNotice, setSessionNotice] = useState("");

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
  const [jobsLoading, setJobsLoading] = useState(false);
  const [jobsError, setJobsError] = useState("");
  const [workflowNotice, setWorkflowNotice] = useState("");

  const handleLogin = (jwtToken) => {
    sessionStorage.setItem("campuspulse_token", jwtToken);
    sessionStorage.removeItem("campuspulse_syllabus_id");
    sessionStorage.removeItem("campuspulse_job_id");

    setToken(jwtToken);
    setSessionNotice("");
    setWorkflowNotice("");
    setSelectedSyllabusId("");
    setSelectedJobId("");
    setActivePage("Upload Syllabus");
  };

  const handleLogout = useCallback(() => {
    sessionStorage.removeItem("campuspulse_token");
    sessionStorage.removeItem("campuspulse_syllabus_id");
    sessionStorage.removeItem("campuspulse_job_id");

    setToken(null);
    setWorkflowNotice("");
    setSyllabi([]);
    setJobs([]);
    setSelectedSyllabusId("");
    setSelectedJobId("");
    setAuthPage("login");
    setActivePage("Upload Syllabus");
  }, [setActivePage]);

  // Any API call that gets HTTP 401 ends the session in one place.
  useEffect(() => {
    const onExpired = () => {
      if (!sessionStorage.getItem("campuspulse_token")) return;
      handleLogout();
      setSessionNotice("Your session expired. Sign in again.");
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
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

  // Load the user's saved analysis inputs after login.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      void refreshSyllabi();
      void refreshJobs();
    }, 0);

    return () => window.clearTimeout(timer);
  }, [refreshSyllabi, refreshJobs]);

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

  const selectedSyllabusName = syllabi.find(
    (item) => String(item.id) === String(selectedSyllabusId)
  )?.name;
  const selectedJobTitle = jobs.find(
    (item) => String(item.id) === String(selectedJobId)
  )?.title;

  const renderPage = () => {
    switch (activePage) {
      case "Upload Syllabus":
        return (
          <SyllabusUpload
            token={token}
            onUploaded={handleUploaded}
          />
        );

      case "Curriculum":
        return (
          <Curriculum
            key={selectedSyllabusId}
            syllabusId={selectedSyllabusId}
          />
        );

      case "Job Market":
        return (
          <JobMarket
            selectedJobId={selectedJobId}
            hasSyllabus={Boolean(selectedSyllabusId)}
            onJobCreated={handleJobCreated}
            onJobSelected={handleJobSelected}
            onCatalogImported={refreshJobs}
          />
        );

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
          />
        );
    }
  };

  const safeSyllabi = Array.isArray(syllabi) ? syllabi : [];
  const recentJobOptions = jobs.slice(0, 200);
  const selectedJob = jobs.find(
    (job) => String(job.id) === String(selectedJobId)
  );
  const jobSelectorOptions =
    selectedJob &&
    !recentJobOptions.some((job) => String(job.id) === String(selectedJobId))
      ? [selectedJob, ...recentJobOptions]
      : recentJobOptions;

  return (
    <div className="app-layout">
      <Sidebar
        activePage={activePage}
        setActivePage={setActivePage}
        hasSyllabus={Boolean(selectedSyllabusId)}
        hasJob={Boolean(selectedJobId)}
      />

      <main className="main-content">
        <WorkflowSteps
          activePage={activePage}
          setActivePage={setActivePage}
          hasSyllabus={Boolean(selectedSyllabusId)}
          hasJob={Boolean(selectedJobId)}
        />
        {workflowNotice && (
          <p className="error-message" role="status">
            {workflowNotice}
          </p>
        )}
        <div
          className="app-topbar"
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            gap: "16px",
            flexWrap: "wrap",
            padding: "12px 20px",
          }}
        >
          <div className="cp-selection-control">
            <label htmlFor="syllabus-selector">
              <strong>Selected syllabus: </strong>
            </label>

            <select
              id="syllabus-selector"
              value={selectedSyllabusId}
              onChange={handleSyllabusChange}
              disabled={
                syllabiLoading || safeSyllabi.length === 0
              }
            >
              <option value="">
                {syllabiLoading
                  ? "Loading syllabi..."
                  : "Select a syllabus"}
              </option>

              {safeSyllabi.map((syllabus) => (
                <option
                  key={syllabus.id}
                  value={String(syllabus.id)}
                >
                  {syllabus.name ||
                    syllabus.fileName ||
                    `Syllabus ${syllabus.id}`}
                  {syllabus.createdAt
                    ? ` · ${new Date(syllabus.createdAt).toLocaleDateString()}`
                    : ""}
                </option>
              ))}
            </select>

            {syllabiError && (
              <p className="error-message" role="alert">
                {syllabiError}
              </p>
            )}

            {!syllabiLoading &&
              !syllabiError &&
              safeSyllabi.length === 0 && (
                <p>No syllabi found. Upload a syllabus first.</p>
              )}
          </div>

          <div className="cp-selection-control">
            <label htmlFor="job-selector">
              <strong>Job profile: </strong>
            </label>
            <select
              id="job-selector"
              value={selectedJobId}
              onChange={handleJobChange}
              disabled={
                jobsLoading ||
                jobs.length === 0 ||
                !selectedSyllabusId
              }
            >
              <option value="">
                {jobsLoading ? "Loading job profiles..." : "Select a job"}
              </option>
              {jobSelectorOptions.map((job) => (
                <option key={job.id} value={String(job.id)}>
                  {job.title || `Job ${job.id}`}
                  {job.company ? ` — ${job.company}` : ""}
                </option>
              ))}
            </select>
            {jobs.length > 200 && (
              <p>Showing recent profiles; search the full catalog in Job Market.</p>
            )}
            {jobsError && <p className="error-message" role="alert">{jobsError}</p>}
            {!jobsLoading && !jobsError && jobs.length === 0 && (
              <p>No job profiles yet. Choose or paste a description in Job Market.</p>
            )}
          </div>

          <ThemeControls />

          <button
            type="button"
            className="cp-button cp-button-danger"
            onClick={handleLogout}
          >
            Logout
          </button>
        </div>

        {renderPage()}
      </main>
    </div>
  );
}

export default App;
