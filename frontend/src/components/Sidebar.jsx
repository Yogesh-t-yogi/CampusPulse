import { useEffect, useState } from "react";
import {
  LayoutDashboard,
  BookOpen,
  BriefcaseBusiness,
  ChartNoAxesCombined,
  Sparkles,
  FileUp,
} from "lucide-react";
import api from "../apiClient";
import { isPageAvailable, WORKFLOW_STEPS } from "../workflow";

const ICONS = {
  "Upload Syllabus": FileUp,
  Curriculum: BookOpen,
  "Job Market": BriefcaseBusiness,
  "Gap Analysis": ChartNoAxesCombined,
  "AI Recommendations": Sparkles,
  Dashboard: LayoutDashboard,
};

const STATUS_CHECK_MS = 60000;

function useServiceStatus() {
  const [status, setStatus] = useState({ state: "checking" });

  useEffect(() => {
    let cancelled = false;
    const check = () =>
      api
        .get("/api/health/services")
        .then(({ data }) => {
          if (cancelled) return;
          if (data.ready) {
            setStatus({ state: "ok", text: "Connected" });
          } else if (!data.embeddingService) {
            setStatus({ state: "warn", text: "AI service offline" });
          } else {
            setStatus({ state: "warn", text: "Groq key missing" });
          }
        })
        .catch(() => {
          if (!cancelled) setStatus({ state: "down", text: "Backend offline" });
        });

    check();
    const timer = window.setInterval(check, STATUS_CHECK_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  return status;
}

function Sidebar({ activePage, setActivePage, hasSyllabus, hasJob }) {
  const status = useServiceStatus();

  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <div className="logo-icon">
          <Sparkles size={22} />
        </div>

        <div>
          <h2>CampusPulse</h2>
          <span>AI Intelligence</span>
        </div>
      </div>

      <nav className="sidebar-nav">
        <p className="nav-title">ANALYSIS</p>

        {WORKFLOW_STEPS.map((step) => {
          const Icon = ICONS[step.page];
          const available = isPageAvailable(step.page, { hasSyllabus, hasJob });

          return (
            <button
              key={step.page}
              type="button"
              onClick={() => setActivePage(step.page)}
              disabled={!available}
              title={available ? "" : "Finish the earlier steps first"}
              aria-current={activePage === step.page ? "page" : undefined}
              className={activePage === step.page ? "nav-item active" : "nav-item"}
            >
              <Icon size={19} />
              <span>{step.page}</span>
            </button>
          );
        })}
      </nav>

      <div className="sidebar-footer">
        <div className={`ai-status ai-status-${status.state}`} role="status">
          <span className="status-dot"></span>

          <div>
            <strong>AI Engine</strong>
            <small>{status.state === "checking" ? "Checking…" : status.text}</small>
          </div>
        </div>
      </div>
    </aside>
  );
}

export default Sidebar;
