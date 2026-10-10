
import { useEffect, useState } from "react";
import axios from "axios";

import {
  PieChart,
  Pie,
  Cell,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
} from "recharts";

function Dashboard() {
  const [report, setReport] = useState(null);
  const [recommendations, setRecommendations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // =========================================
  // JWT AUTHENTICATION CONFIG
  // =========================================

  const getAuthConfig = () => ({
    headers: {
      Authorization: `Bearer ${sessionStorage.getItem(
        "campuspulse_token"
      )}`,
    },
  });

  // =========================================
  // LOAD DASHBOARD DATA
  // =========================================

  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        const config = getAuthConfig();

        const [reportResponse, recommendationResponse] =
          await Promise.all([
            axios.get(
              "http://localhost:8081/api/matching/semantic/report/jobs/1/syllabus/4",
              config
            ),
            axios.get(
              "http://localhost:8081/api/recommendations/jobs/1/syllabus/4",
              config
            ),
          ]);

        setReport(reportResponse.data);
        setRecommendations(recommendationResponse.data);
      } catch (error) {
        console.error("Dashboard error:", {
          status: error.response?.status,
          response: error.response?.data,
          url: error.config?.url,
        });

        setError("Unable to load dashboard data.");
      } finally {
        setLoading(false);
      }
    };

    fetchDashboardData();
  }, []);

  // =========================================
  // LOADING STATE
  // =========================================

  if (loading) {
    return (
      <div className="page-content">
        <h1>Dashboard</h1>
        <p>Loading CampusPulse analytics...</p>
      </div>
    );
  }

  // =========================================
  // ERROR STATE
  // =========================================

  if (error || !report) {
    return (
      <div className="page-content">
        <h1>Dashboard</h1>
        <p className="error-message">
          {error || "Unable to load dashboard data."}
        </p>
        <button
          onClick={() => window.location.reload()}
        >
          Retry
        </button>
      </div>
    );
  }

  // =========================================
  // PIE CHART DATA
  // =========================================

  const pieData = [
    { name: "Matched", value: report.matchedSkills ?? 0 },
    { name: "Partial", value: report.partialSkills ?? 0 },
    { name: "Gap", value: report.gapSkills ?? 0 },
  ];

  const pieColors = ["#22c55e", "#f59e0b", "#ef4444"];

  // =========================================
  // BAR CHART DATA
  // =========================================

  const barData = (report.skillDetails ?? []).map((item) => ({
    skill: item.skill,
    similarity: Number(
      ((item.similarity ?? 0) * 100).toFixed(2)
    ),
  }));

  // =========================================
  // MAIN DASHBOARD
  // =========================================

  return (
    <div className="page-content">
      {/* HEADER */}
      <div className="page-header">
        <h1>CampusPulse Dashboard</h1>
        <p>
          AI-driven curriculum-to-industry gap analysis.
        </p>
      </div>

      {/* STATISTICS */}
      <div className="stats-grid">
        <div className="stat-card dashboard-stat">
          <div className="stat-header">
            <span>Total Skills</span>
            <span className="stat-icon">◎</span>
          </div>
          <strong>{report.totalSkills ?? 0}</strong>
          <p>Industry requirements analyzed</p>
        </div>

        <div className="stat-card dashboard-stat">
          <div className="stat-header">
            <span>Matched Skills</span>
            <span className="stat-icon">✓</span>
          </div>
          <strong>{report.matchedSkills ?? 0}</strong>
          <p>Skills covered by curriculum</p>
        </div>

        <div className="stat-card dashboard-stat">
          <div className="stat-header">
            <span>Skill Gaps</span>
            <span className="stat-icon">!</span>
          </div>
          <strong>{report.gapSkills ?? 0}</strong>
          <p>Skills requiring attention</p>
        </div>

        <div className="stat-card dashboard-stat">
          <div className="stat-header">
            <span>Industry Coverage</span>
            <span className="stat-icon">%</span>
          </div>
          <strong>{report.coveragePercentage ?? 0}%</strong>
          <p>Overall curriculum alignment</p>
        </div>
      </div>

      {/* ANALYTICS */}
      <div className="analytics-grid">
        {/* PIE CHART */}
        <div className="card">
          <h2>Skill Distribution</h2>

          <div className="chart-container">
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={pieData}
                  dataKey="value"
                  nameKey="name"
                  cx="50%"
                  cy="50%"
                  outerRadius={100}
                  label
                >
                  {pieData.map((entry, index) => (
                    <Cell
                      key={`cell-${index}`}
                      fill={pieColors[index]}
                    />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* BAR CHART */}
        <div className="card">
          <h2>Semantic Skill Similarity</h2>

          <div className="chart-container">
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={barData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis
                  dataKey="skill"
                  angle={-20}
                  textAnchor="end"
                  height={70}
                />
                <YAxis domain={[0, 100]} />
                <Tooltip />
                <Bar
                  dataKey="similarity"
                  name="Similarity %"
                  fill="#6366f1"
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* AI RECOMMENDATIONS */}
      <div className="card">
        <h2>AI Recommendations</h2>

        <p>
          {recommendations.length} AI-generated
          recommendations available.
        </p>

        {recommendations.length === 0 ? (
          <p>No recommendations available.</p>
        ) : (
          recommendations.slice(0, 3).map((item, index) => (
            <div
              key={item.id ?? index}
              className="recommendation-card"
            >
              <h3>{item.skill}</h3>

              <p>
                <strong>Priority:</strong> {item.priority}
              </p>

              <p>{item.explanation}</p>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

export default Dashboard;
