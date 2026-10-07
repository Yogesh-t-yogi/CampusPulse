import { useState } from "react";

import Sidebar from "./components/Sidebar";

import Dashboard from "./pages/Dashboard";
import Curriculum from "./pages/Curriculum";
import JobMarket from "./pages/JobMarket";
import GapAnalysis from "./pages/GapAnalysis";
import AIRecommendations from "./pages/AIRecommendations";

import "./App.css";


function App() {

  const [activePage, setActivePage] = useState("Dashboard");


  const renderPage = () => {

    if (activePage === "Dashboard") {
      return <Dashboard />;
    }

    if (activePage === "Curriculum") {
      return <Curriculum />;
    }
    if (activePage === "Job Market") {
      return <JobMarket />;
    }
    if (activePage === "Gap Analysis") {
      return <GapAnalysis />;
    }
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

        {renderPage()}

      </main>

    </div>

  );
}


export default App;