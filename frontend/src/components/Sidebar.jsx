import { useEffect, useState } from "react";
import {
  BookOpen,
  BriefcaseBusiness,
  ChartNoAxesCombined,
  Sparkles,
  FileUp,
  TrendingUp,
  Database,
} from "lucide-react";
import api from "../apiClient";
import { isPageAvailable, WORKFLOW_STEPS } from "../workflow";

const ICONS = {
  "Upload Syllabus": FileUp,
  Curriculum: BookOpen,
  "Job Market": BriefcaseBusiness,
  Report: ChartNoAxesCombined,
  "Market insights": TrendingUp,
  "Job catalog": Database,
};

const EXTRA_PAGES = [{ page: "Market insights", label: "Market insights" }];
const ADMIN_PAGES = [{ page: "Job catalog", label: "Job catalog" }];

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

function Sidebar({ activePage, setActivePage, hasSyllabus, hasJob, isAdmin }) {
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
              <span>{step.label}</span>
            </button>
          );
        })}

        <p className="nav-title">TOOLS</p>
        {EXTRA_PAGES.concat(isAdmin ? ADMIN_PAGES : []).map((item) => {
          const Icon = ICONS[item.page];
          return (
            <button
              key={item.page}
              type="button"
              onClick={() => setActivePage(item.page)}
              aria-current={activePage === item.page ? "page" : undefined}
              className={activePage === item.page ? "nav-item active" : "nav-item"}
            >
              <Icon size={19} />
              <span>{item.label}</span>
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
