
import { useEffect, useState } from "react";
import axios from "axios";

function GapAnalysis() {
  const [report, setReport] = useState(null);
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
  // LOAD GAP ANALYSIS REPORT
  // =========================================

  useEffect(() => {
    fetchGapReport();
  }, []);

  const fetchGapReport = async () => {
    try {
      const response = await axios.get(
        "http://localhost:8081/api/matching/semantic/report/jobs/1/syllabus/4",
        getAuthConfig()
      );

      setReport(response.data);
      } catch (error) {
    console.error("Failed to load gap analysis:", {
      status: error.response?.status,
      response: error.response?.data,
      url: error.config?.url,
      authorizationHeaderPresent: Boolean(
        error.config?.headers?.Authorization
      ),
    });

    setError(
      error.response?.status === 403
        ? "Access denied. Please check the authentication and backend security configuration."
        : "Failed to load gap analysis."
    );
  }finally {
      setLoading(false);
    }
  };

  // =========================================
  // LOADING STATE
  // =========================================

  if (loading) {
    return (
      <div className="page-content">
        <h1>Gap Analysis</h1>
        <p>Analyzing curriculum and industry skills...</p>
      </div>
    );
  }

  // =========================================
  // ERROR STATE
  // =========================================

  if (error) {
    return (
      <div className="page-content">
        <h1>Gap Analysis</h1>
        <p className="error-message">{error}</p>
      </div>
    );
  }

  // =========================================
  // MAIN PAGE
  // =========================================

  if (!report) {
    return (
      <div className="page-content">
        <h1>Gap Analysis</h1>
        <p>No gap analysis report is available.</p>
      </div>
    );
  }

  return (
    <div className="page-content">
      {/* PAGE HEADER */}
      <div className="page-header">
        <h1>Skill Gap Analysis</h1>

        <p>
          Compare curriculum skills with real-world
          industry requirements.
        </p>
      </div>

      {/* OVERVIEW */}
      <div className="stats-grid">
        <div className="stat-card">
          <h3>Total Skills</h3>
          <strong>{report.totalSkills}</strong>
        </div>

        <div className="stat-card">
          <h3>Matched</h3>
          <strong>{report.matchedSkills}</strong>
        </div>

        <div className="stat-card">
          <h3>Partial</h3>
          <strong>{report.partialSkills}</strong>
        </div>

        <div className="stat-card">
          <h3>Skill Gaps</h3>
          <strong>{report.gapSkills}</strong>
        </div>
      </div>

      {/* CURRICULUM COVERAGE */}
      <div className="card">
        <h2>Curriculum Coverage</h2>

        <h1>{report.coveragePercentage}%</h1>

        <p>
          Industry skill coverage based on semantic
          similarity analysis.
        </p>
      </div>

      {/* SKILL DETAILS */}
      <div className="card">
        <h2>Skill Analysis</h2>

        {report.skillDetails?.length > 0 ? (
          report.skillDetails.map((item) => (
            <div
              key={item.skill}
              className="recommendation-card"
            >
              <h3>{item.skill}</h3>

              <p>
                <strong>Curriculum Topic:</strong>{" "}
                {item.topic || "No matching topic"}
              </p>

              <p>
                <strong>Similarity:</strong>{" "}
                {(item.similarity * 100).toFixed(2)}%
              </p>

              <p>
                <strong>Status:</strong>{" "}
                <span
                  className={`skill-status ${item.status.toLowerCase()}`}
                >
                  {item.status}
                </span>
              </p>

              <div className="similarity-bar">
                <div
                  className={`similarity-fill ${item.status.toLowerCase()}`}
                  style={{
                    width: `${Math.max(
                      0,
                      Math.min(100, item.similarity * 100)
                    )}%`,
                  }}
                ></div>
              </div>
            </div>
          ))
        ) : (
          <p>No skill details are available.</p>
        )}
      </div>
    </div>
  );
}

export default GapAnalysis;
