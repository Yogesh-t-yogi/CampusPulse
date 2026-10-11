import { useEffect, useState } from "react";
import axios from "axios";

import Sidebar from "./components/Sidebar";
import Dashboard from "./pages/Dashboard";
import Curriculum from "./pages/Curriculum";
import JobMarket from "./pages/JobMarket";
import GapAnalysis from "./pages/GapAnalysis";
import AIRecommendations from "./pages/AIRecommendations";

import Login from "./pages/Login";
import Register from "./pages/Register";

import "./App.css";

const API_BASE_URL = "http://localhost:8081";

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

  useEffect(() => {
    if (!token) {
      setSyllabi([]);
      setSelectedSyllabusId("");
      setSyllabiLoading(false);
      return;
    }

    let cancelled = false;

    const fetchSyllabi = async () => {
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

        if (cancelled) return;

        // Prevent the UI from crashing if the API response isn't an array.
        const userSyllabi = Array.isArray(response.data)
          ? response.data
          : [];

        if (!Array.isArray(response.data)) {
          console.error(
            "Expected an array from GET /api/syllabus:",
            response.data
          );
        }

        setSyllabi(userSyllabi);

        const savedId = sessionStorage.getItem(
          "campuspulse_syllabus_id"
        );

        const savedSyllabusExists = userSyllabi.some(
          (syllabus) => String(syllabus.id) === savedId
        );

        const nextId = savedSyllabusExists
          ? savedId
          : userSyllabi.length > 0
            ? String(userSyllabi[0].id)
            : "";

        setSelectedSyllabusId(nextId);

        if (nextId) {
          sessionStorage.setItem("campuspulse_syllabus_id", nextId);
        } else {
          sessionStorage.removeItem("campuspulse_syllabus_id");
        }
      } catch (error) {
        if (cancelled) return;

        console.error("Failed to load syllabi:", {
          message: error.message,
          code: error.code,
          status: error.response?.status,
          response: error.response?.data,
          url: error.config?.url,
        });

        setSyllabi([]);
        setSelectedSyllabusId("");
        sessionStorage.removeItem("campuspulse_syllabus_id");

        if (error.response?.status === 401) {
          setSyllabiError(
            "Your session may have expired. Please log in again."
          );
        } else if (error.response?.status === 403) {
          setSyllabiError(
            "Access denied. Please check your account permissions."
          );
        } else if (!error.response) {
          setSyllabiError(
            "Cannot connect to the backend. Check that Spring Boot is running on port 8081."
          );
        } else {
          setSyllabiError("Failed to load your syllabi.");
        }
      } finally {
        if (!cancelled) {
          setSyllabiLoading(false);
        }
      }
    };

    fetchSyllabi();

    return () => {
      cancelled = true;
    };
  }, [token]);

  const handleSyllabusChange = (event) => {
    const id = event.target.value;

    setSelectedSyllabusId(id);

    if (id) {
      sessionStorage.setItem("campuspulse_syllabus_id", id);
    } else {
      sessionStorage.removeItem("campuspulse_syllabus_id");
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
        onLogin={handleLogin}
        onShowRegister={() => setAuthPage("register")}
      />
    );
  }

  const renderPage = () => {
    if (activePage === "Dashboard") {
      return <Dashboard syllabusId={selectedSyllabusId} />;
    }

    if (activePage === "Curriculum") {
      return <Curriculum syllabusId={selectedSyllabusId} />;
    }

    if (activePage === "Job Market") {
      return <JobMarket />;
    }

    if (activePage === "Gap Analysis") {
      return <GapAnalysis syllabusId={selectedSyllabusId} />;
    }

    if (activePage === "AI Recommendations") {
      return (
        <AIRecommendations syllabusId={selectedSyllabusId} />
      );
    }

    return <Dashboard syllabusId={selectedSyllabusId} />;
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
              disabled={syllabiLoading || safeSyllabi.length === 0}
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
                  {" (ID: "}
                  {syllabus.id}
                  {")"}
                </option>
              ))}
            </select>

            {syllabiError && (
              <p className="error-message">{syllabiError}</p>
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