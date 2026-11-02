export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ||
  (import.meta.env.DEV
    ? "http://localhost:8081"
    : "https://campuspulse-backend-x5xh.onrender.com")
).replace(/\/$/, "");
