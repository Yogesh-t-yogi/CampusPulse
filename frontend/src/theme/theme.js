// Light/dark theme and faculty/student mode, kept per browser (localStorage).
const THEME_KEY = "campuspulse_theme";
const MODE_KEY = "campuspulse_mode";

function read(key) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Storage blocked: the choice just won't be remembered.
  }
}

export function getTheme() {
  const saved = read(THEME_KEY);
  if (saved === "light" || saved === "dark") return saved;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
}

export function getMode() {
  return read(MODE_KEY) === "student" ? "student" : "faculty";
}

export function setTheme(theme) {
  write(THEME_KEY, theme);
  document.documentElement.dataset.theme = theme;
}

export function setMode(mode) {
  write(MODE_KEY, mode);
  document.documentElement.dataset.mode = mode;
}

export function applyStoredPreferences() {
  document.documentElement.dataset.theme = getTheme();
  document.documentElement.dataset.mode = getMode();
}
