
import { useCallback, useEffect, useState } from "react";
import axios from "axios";

import Sidebar from "./components/Sidebar";
import Dashboard from "./pages/Dashboard";
import Curriculum from "./pages/Curriculum";
import JobMarket from "./pages/JobMarket";
import GapAnalysis from "./pages/GapAnalysis";
import AIRecommendations from "./pages/AIRecommendations";
import SyllabusUpload from "./pages/SyllabusUpload";
import Login from "./pages/Login";
import Register from "./pages/Register";
import "./App.css";


const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || "https://campuspulse-backend-x5xh.onrender.com"
).replace(/\/$/, "");

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
  const [activePage, setActivePage] = useState("Dashboard");

  const [token, setToken] = useState(
    () => sessionStorage.getItem("campuspulse_token")
  );

  const [authPage, setAuthPage] = useState("login");
  const [syllabi, setSyllabi] = useState([]);

  const [selectedSyllabusId, setSelectedSyllabusId] = useState(
    () => sessionStorage.getItem("campuspulse_syllabus_id") || ""
  );

  const [syllabiLoading, setSyllabiLoading] = useState(false);
  const [syllabiError, setSyllabiError] = useState("");

  const handleLogin = (jwtToken) => {
    sessionStorage.setItem("campuspulse_token", jwtToken);
    sessionStorage.removeItem("campuspulse_syllabus_id");

    setToken(jwtToken);
    setSelectedSyllabusId("");
    setActivePage("Dashboard");
  };

  const handleLogout = () => {
    sessionStorage.removeItem("campuspulse_token");
    sessionStorage.removeItem("campuspulse_syllabus_id");

    setToken(null);
    setSyllabi([]);
    setSelectedSyllabusId("");
    setAuthPage("login");
    setActivePage("Dashboard");
  };

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
        const response = await axios.get(
          `${API_BASE_URL}/api/syllabus`,
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

  // Load syllabi after login and whenever the token changes.
  useEffect(() => {
    refreshSyllabi();
  }, [refreshSyllabi]);

  const handleSyllabusChange = (event) => {
    const id = event.target.value;

    setSelectedSyllabusId(id);

    if (id) {
      sessionStorage.setItem("campuspulse_syllabus_id", id);
    } else {
      sessionStorage.removeItem("campuspulse_syllabus_id");
    }
  };

  // Called after the PDF upload succeeds.
  const handleUploaded = async (uploadResponse) => {
    const uploadedId = getSyllabusId(uploadResponse);

    await refreshSyllabi(uploadedId, true);
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
        onLogin={handleLogin}
        onShowRegister={() => setAuthPage("register")}
      />
    );
  }

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
        return <JobMarket />;

      case "Gap Analysis":
        return (
          <GapAnalysis
            key={selectedSyllabusId}
            syllabusId={selectedSyllabusId}
          />
        );

      case "AI Recommendations":
        return (
          <AIRecommendations
            key={selectedSyllabusId}
            syllabusId={selectedSyllabusId}
          />
        );

      case "Dashboard":
      default:
        return (
          <Dashboard
            key={selectedSyllabusId}
            syllabusId={selectedSyllabusId}
          />
        );
    }
  };

  const safeSyllabi = Array.isArray(syllabi) ? syllabi : [];

  return (
    <div className="app-layout">
      <Sidebar
        activePage={activePage}
        setActivePage={setActivePage}
      />

      <main className="main-content">
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
          <div>
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
                  {" "}(ID: {syllabus.id})
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

          <button
            type="button"
            onClick={handleLogout}
            style={{
              padding: "10px 20px",
              backgroundColor: "#dc2626",
              color: "#ffffff",
              border: "none",
              borderRadius: "6px",
              cursor: "pointer",
              fontSize: "14px",
              fontWeight: "600",
            }}
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
