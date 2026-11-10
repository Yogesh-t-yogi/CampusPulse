import { useEffect, useRef, useState } from "react";
import { ChevronDown, LogOut, Moon, Sun } from "lucide-react";
import { getTheme, setTheme } from "../theme/theme";
import { roleLabel } from "../permissions";

function initials(name = "") {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join("") || "?";
}

// Header: page title, analysis context (syllabus + job), theme, user menu.
// The empty "shell-tenant" slot is where the Phase 6 college/department switcher goes.
function Topbar({
  title,
  user,
  onLogout,
  syllabi,
  selectedSyllabusId,
  onSyllabusChange,
  jobs,
  selectedJobId,
  onJobChange,
}) {
  const [theme, setThemeState] = useState(getTheme);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    if (!menuOpen) return undefined;
    const close = (event) => {
      if (event.type === "keydown" ? event.key === "Escape" : !menuRef.current?.contains(event.target)) {
        setMenuOpen(false);
      }
    };
    window.addEventListener("mousedown", close);
    window.addEventListener("keydown", close);
    return () => {
      window.removeEventListener("mousedown", close);
      window.removeEventListener("keydown", close);
    };
  }, [menuOpen]);

  const toggleTheme = () => {
    const next = theme === "dark" ? "light" : "dark";
    setTheme(next);
    setThemeState(next);
  };

  return (
    <header className="shell-topbar">
      <div className="shell-topbar-title">
        <span className="shell-crumb">CampusPulse</span>
        <span className="shell-crumb-sep" aria-hidden="true">/</span>
        <strong>{title}</strong>
      </div>

      <div className="shell-tenant" aria-hidden="true" />

      <div className="shell-context">
        <label className="shell-context-item">
          <span>Syllabus</span>
          <select
            value={selectedSyllabusId}
            onChange={onSyllabusChange}
            disabled={syllabi.length === 0}
          >
            <option value="">{syllabi.length ? "Choose a syllabus" : "No syllabi yet"}</option>
            {syllabi.map((item) => (
              <option key={item.id} value={String(item.id)}>
                {item.name || item.fileName || `Syllabus ${item.id}`}
              </option>
            ))}
          </select>
        </label>
        <label className="shell-context-item">
          <span>Job</span>
          <select
            value={selectedJobId}
            onChange={onJobChange}
            disabled={jobs.length === 0 || !selectedSyllabusId}
          >
            <option value="">{jobs.length ? "Choose a job" : "No jobs yet"}</option>
            {jobs.slice(0, 300).map((job) => (
              <option key={job.id} value={String(job.id)}>
                {job.title}
                {job.company ? ` — ${job.company}` : ""}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="shell-topbar-actions">
        <button
          type="button"
          className="ui-icon-btn ui-btn-ghost"
          onClick={toggleTheme}
          aria-label={theme === "dark" ? "Switch to light theme" : "Switch to dark theme"}
          title={theme === "dark" ? "Light theme" : "Dark theme"}
        >
          {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
        </button>

        <div className="shell-user" ref={menuRef}>
          <button
            type="button"
            className="shell-user-button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-haspopup="menu"
            aria-expanded={menuOpen}
          >
            <span className="shell-avatar" aria-hidden="true">{initials(user?.name)}</span>
            <span className="shell-user-text">
              <strong>{user?.name || "Account"}</strong>
              <small>{roleLabel(user?.role)}</small>
            </span>
            <ChevronDown size={16} aria-hidden="true" />
          </button>

          {menuOpen && (
            <div className="shell-menu" role="menu">
              <div className="shell-menu-head">
                <strong>{user?.name}</strong>
                <small>{user?.email}</small>
              </div>
              <button type="button" role="menuitem" className="shell-menu-item" onClick={onLogout}>
                <LogOut size={16} aria-hidden="true" />
                Sign out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

export default Topbar;
