
import { useState } from "react";

import Sidebar from "./components/Sidebar";
import Dashboard from "./pages/Dashboard";
import Curriculum from "./pages/Curriculum";
import JobMarket from "./pages/JobMarket";
import GapAnalysis from "./pages/GapAnalysis";
import AIRecommendations from "./pages/AIRecommendations";

import Login from "./pages/Login";
import Register from "./pages/Register";

import "./App.css";

function App() {
  const [activePage, setActivePage] = useState("Dashboard");
  const [token, setToken] = useState(
    () => sessionStorage.getItem("campuspulse_token")
  );
  const [authPage, setAuthPage] = useState("login");

  const handleLogin = (jwtToken) => {
    sessionStorage.setItem("campuspulse_token", jwtToken);
    setToken(jwtToken);
    setActivePage("Dashboard");
  };

  const handleLogout = () => {
    sessionStorage.removeItem("campuspulse_token");
    setToken(null);
    setAuthPage("login");
    setActivePage("Dashboard");
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
    if (activePage === "Dashboard") return <Dashboard />;
    if (activePage === "Curriculum") return <Curriculum />;
    if (activePage === "Job Market") return <JobMarket />;
    if (activePage === "Gap Analysis") return <GapAnalysis />;
    if (activePage === "AI Recommendations") {
      return <AIRecommendations />;
    }

    return <Dashboard />;
  };

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
            justifyContent: "flex-end",
            padding: "12px 20px",
          }}
        >
          <button
            type="button"
            onClick={handleLogout}
            style={{
              display: "inline-block",
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
