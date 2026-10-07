import { useEffect, useState } from "react";
import axios from "axios";

function GapAnalysis() {

  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchGapReport();
  }, []);

  const fetchGapReport = async () => {

    try {

      const response = await axios.get(
        "http://localhost:8081/api/matching/semantic/report/jobs/1/syllabus/4"
      );

      setReport(response.data);

    } catch (error) {

      console.error(error);
      setError("Failed to load gap analysis.");

    } finally {

      setLoading(false);

    }
  };

  if (loading) {
    return (
      <div className="page-content">
        <h1>Gap Analysis</h1>
        <p>Analyzing curriculum and industry skills...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page-content">
        <h1>Gap Analysis</h1>
        <p className="error-message">{error}</p>
      </div>
    );
  }

  return (
    <div className="page-content">

      <div className="page-header">
        <h1>Skill Gap Analysis</h1>

        <p>
          Compare curriculum skills with real-world
          industry requirements.
        </p>
      </div>

      {/* Overview */}

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

      {/* Coverage */}

      <div className="card">

        <h2>Curriculum Coverage</h2>

        <h1>
          {report.coveragePercentage}%
        </h1>

        <p>
          Industry skill coverage based on semantic
          similarity analysis.
        </p>

      </div>

      {/* Skill Details */}

      <div className="card">

        <h2>Skill Analysis</h2>

        {report.skillDetails?.map((item) => (

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
                width: `${item.similarity * 100}%`
                }}
            ></div>
            </div>

          </div>

        ))}

      </div>

    </div>
  );
}

export default GapAnalysis;