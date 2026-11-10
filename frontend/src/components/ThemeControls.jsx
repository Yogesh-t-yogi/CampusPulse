import { useState } from "react";
import { Moon, Sun, GraduationCap, Presentation } from "lucide-react";
import { getMode, getTheme, setMode, setTheme } from "../theme/theme";

// Faculty / Student mode switch and light / dark toggle.
function ThemeControls({ onModeChange }) {
  const [mode, setModeState] = useState(getMode);
  const [theme, setThemeState] = useState(getTheme);

  const chooseMode = (next) => {
    setMode(next);
    setModeState(next);
    onModeChange?.(next);
  };

  const toggleTheme = () => {
    const next = theme === "dark" ? "light" : "dark";
    setTheme(next);
    setThemeState(next);
  };

  return (
    <div className="cp-theme-controls">
      <div className="cp-mode-switch" role="group" aria-label="View mode">
        <button
          type="button"
          className={mode === "faculty" ? "active" : ""}
          aria-pressed={mode === "faculty"}
          onClick={() => chooseMode("faculty")}
        >
          <Presentation size={16} aria-hidden="true" />
          Faculty
        </button>
        <button
          type="button"
          className={mode === "student" ? "active" : ""}
          aria-pressed={mode === "student"}
          onClick={() => chooseMode("student")}
        >
          <GraduationCap size={16} aria-hidden="true" />
          Student
        </button>
      </div>
      <button
        type="button"
        className="cp-icon-button"
        onClick={toggleTheme}
        aria-label={theme === "dark" ? "Switch to light theme" : "Switch to dark theme"}
        title={theme === "dark" ? "Light theme" : "Dark theme"}
      >
        {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
      </button>
    </div>
  );
}

export default ThemeControls;
