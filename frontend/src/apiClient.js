import axios from "axios";
import { API_BASE_URL } from "./api";

export const TOKEN_KEY = "campuspulse_token";
export const SESSION_EXPIRED_EVENT = "campuspulse:session-expired";

// One axios instance for the whole app: base URL, login token, and
// a single place that reacts to an expired session (HTTP 401).
const api = axios.create({ baseURL: API_BASE_URL });

api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
    return Promise.reject(error);
  }
);

// Readable message from any API error (backend sends {message, error}).
export function errorMessage(error, fallback = "Something went wrong. Try again.") {
  if (!error?.response) {
    return "Can't reach the CampusPulse backend. Check that it's running.";
  }
  return error.response.data?.message || error.response.data?.error || fallback;
}

export default api;
