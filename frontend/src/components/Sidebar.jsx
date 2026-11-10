import { useEffect, useState } from "react";
import {
  BookOpen,
  BriefcaseBusiness,
  ChartNoAxesCombined,
  Database,
  FileUp,
  FileUser,
  Files,
  House,
  Lock,
  Sparkles,
  TrendingUp,
  Users as UsersIcon,
} from "lucide-react";
import api from "../apiClient";
import { canSeePage } from "../permissions";
import { isPageAvailable } from "../workflow";

// Menu groups. Workflow items lock until earlier steps are done.
const GROUPS = [
  {
    title: "Overview",
    items: [
      { page: "Home", label: "Home", icon: House },
      { page: "Syllabi", label: "Syllabi", icon: Files },
    ],
  },
  {
    title: "Analysis",
    workflow: true,
    items: [
      { page: "Upload Syllabus", label: "Upload syllabus", icon: FileUp },
      { page: "Curriculum", label: "Review curriculum", icon: BookOpen },
      { page: "Job Market", label: "Choose job", icon: BriefcaseBusiness },
      { page: "Report", label: "Report", icon: ChartNoAxesCombined },
    ],
  },
  {
    title: "Tools",
    items: [
      { page: "Market insights", label: "Market insights", icon: TrendingUp },
      { page: "Resume match", label: "Resume match", icon: FileUser },
    ],
  },
  {
    title: "Admin",
    items: [
      { page: "Job catalog", label: "Job catalog", icon: Database },
      { page: "Users", label: "Users", icon: UsersIcon },
    ],
  },
];

const STATUS_CHECK_MS = 60000;

function useServiceStatus() {
  const [status, setStatus] = useState({ state: "checking", text: "Checking…" });

  useEffect(() => {
    let cancelled = false;
    const check = () =>
      api
        .get("/api/health/services")
        .then(({ data }) => {
          if (cancelled) return;
          if (data.ready) setStatus({ state: "ok", text: "All services running" });
          else if (!data.embeddingService) setStatus({ state: "warn", text: "AI service offline" });
          else setStatus({ state: "warn", text: "Groq key missing" });
        })
        .catch(() => !cancelled && setStatus({ state: "down", text: "Backend offline" }));

    check();
    const timer = window.setInterval(check, STATUS_CHECK_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  return status;
}

function Sidebar({ activePage, setActivePage, hasSyllabus, hasJob, role }) {
  const status = useServiceStatus();

  return (
    <aside className="shell-sidebar" aria-label="Main menu">
      <div className="shell-brand">
        <span className="shell-brand-mark" aria-hidden="true">
          <Sparkles size={18} />
        </span>
        <span>
          <strong>CampusPulse</strong>
          <small>Curriculum intelligence</small>
        </span>
      </div>

      <nav className="shell-nav">
        {GROUPS.map((group) => {
          const items = group.items.filter((item) => canSeePage(role, item.page));
          if (items.length === 0) return null;
          return (
            <div className="shell-nav-group" key={group.title}>
              <p className="shell-nav-title">{group.title}</p>
              {items.map((item) => {
                const Icon = item.icon;
                const locked =
                  group.workflow && !isPageAvailable(item.page, { hasSyllabus, hasJob });
                const active = activePage === item.page;
                return (
                  <button
                    key={item.page}
                    type="button"
                    className={`shell-nav-item${active ? " active" : ""}`}
                    onClick={() => setActivePage(item.page)}
                    disabled={locked}
                    title={locked ? "Finish the earlier steps first" : undefined}
                    aria-current={active ? "page" : undefined}
                  >
                    <Icon size={18} aria-hidden="true" />
                    <span>{item.label}</span>
                    {locked && <Lock size={14} className="shell-nav-lock" aria-label="Locked" />}
                  </button>
                );
              })}
            </div>
          );
        })}
      </nav>

      <div className={`shell-status shell-status-${status.state}`} role="status">
        <span className="shell-status-dot" aria-hidden="true" />
        <span>
          <strong>AI engine</strong>
          <small>{status.text}</small>
        </span>
      </div>
    </aside>
  );
}

export default Sidebar;
