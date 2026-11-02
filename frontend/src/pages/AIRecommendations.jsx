
import { useEffect, useState } from "react";
import axios from "axios";
import { API_BASE_URL } from "../api";

const OFFICIAL_SKILL_DOCS = {
  java: "https://dev.java/learn/",
  javascript: "https://developer.mozilla.org/en-US/docs/Web/JavaScript",
  typescript: "https://www.typescriptlang.org/docs/",
  python: "https://docs.python.org/3/tutorial/",
  react: "https://react.dev/learn",
  "spring boot": "https://spring.io/guides",
  postgresql: "https://www.postgresql.org/docs/current/tutorial.html",
  sql: "https://www.freecodecamp.org/news/sql-tutorial/",
  docker: "https://docs.docker.com/get-started/",
  kubernetes: "https://kubernetes.io/docs/tutorials/",
  pandas: "https://pandas.pydata.org/docs/getting_started/intro_tutorials/",
  git: "https://git-scm.com/docs/gittutorial",
};

function getLearningResources(skill) {
  const key = String(skill || "").trim().toLowerCase();
  return [
    {
      label: "freeCodeCamp",
      url: `https://www.freecodecamp.org/news/search/?query=${encodeURIComponent(skill || "")}`,
    },
    ...(OFFICIAL_SKILL_DOCS[key]
      ? [{ label: "Official documentation", url: OFFICIAL_SKILL_DOCS[key] }]
      : []),
  ];
}

function AIRecommendations({ syllabusId, jobId, token }) {
  const [recommendations, setRecommendations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    async function fetchRecommendations() {
      setRecommendations([]);
      setError("");

      if (!syllabusId) {
        setError("Please select a syllabus to view recommendations.");
        setLoading(false);
        return;
      }

      if (!jobId) {
        setError("Choose a job profile to generate recommendations.");
        setLoading(false);
        return;
      }

      if (!token) {
        setError("Your login session is missing. Please log in again.");
        setLoading(false);
        return;
      }

      setLoading(true);

      try {
        const response = await axios.get(
          `${API_BASE_URL}/api/recommendations/jobs/${jobId}/syllabus/${syllabusId}`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          }
        );

        if (cancelled) return;

        if (!Array.isArray(response.data)) {
          throw new Error("INVALID_RESPONSE");
        }

        setRecommendations(response.data);
      } catch (err) {
        if (cancelled) return;

        console.error("AI recommendations error:", {
          status: err.response?.status,
          url: err.config?.url,
          message: err.message,
        });

        if (err.message === "INVALID_RESPONSE") {
          setError("The server returned an unexpected recommendations format.");
        } else if (err.response?.status === 401) {
          setError("Your session has expired. Please log in again.");
        } else if (err.response?.status === 403) {
          setError("Access denied. Check your account permissions and syllabus.");
        } else if (err.response?.status === 404) {
          setError("No recommendations were found for this syllabus and job.");
        } else if (!err.response) {
          setError("Cannot connect to the backend on port 8081.");
        } else {
          setError("Failed to load AI recommendations. Please try again.");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    fetchRecommendations();

    return () => {
      cancelled = true;
    };
  }, [syllabusId, jobId, token]);

  const getPriority = (priority) => {
    const value = String(priority || "MEDIUM").toUpperCase();

    if (value === "HIGH" || value === "MEDIUM" || value === "LOW") {
      return value;
    }

    return "MEDIUM";
  };

  const getStatus = (status) => {
    const value = String(status || "REVIEW").toUpperCase();

    if (value === "MATCHED" || value === "PARTIAL" || value === "GAP") {
      return value;
    }

    return "REVIEW";
  };

  if (loading) {
    return (
      <main className="page-content cp-recs">
        <div className="cr-state">
          <div className="cr-spinner" />
          <h2>Preparing AI recommendations</h2>
          <p>Reviewing skill alignment and identifying next steps...</p>
        </div>
      </main>
    );
  }

  if (error) {
    return (
      <main className="page-content cp-recs">
        <div className="cr-state cr-state-card">
          <span className="cr-state-icon">!</span>
          <h1>AI Recommendations</h1>
          <p role="alert">{error}</p>
          <button
            type="button"
            className="cr-primary-button"
            onClick={() => window.location.reload()}
          >
            Try again
          </button>
        </div>
      </main>
    );
  }

  const highPriorityCount = recommendations.filter(
    (item) => getPriority(item.priority) === "HIGH"
  ).length;

  const gapCount = recommendations.filter(
    (item) => getStatus(item.status) === "GAP"
  ).length;

  return (
    <main className="page-content cp-recs">
      <section className="cr-hero">
        <div>
          <span className="cr-eyebrow">PERSONALIZED LEARNING · AI INSIGHTS</span>
          <h1>AI Recommendations</h1>
          <p>
            Turn industry skill gaps into practical actions for curriculum
            improvement and student learning.
          </p>
        </div>

        <div className="cr-hero-icon" aria-hidden="true">
          ✦
        </div>
      </section>

      <section className="cr-summary">
        <article className="cr-summary-card">
          <span className="cr-summary-icon cr-purple">✦</span>
          <div>
            <span>Total recommendations</span>
            <strong>{recommendations.length}</strong>
          </div>
        </article>

        <article className="cr-summary-card">
          <span className="cr-summary-icon cr-red">!</span>
          <div>
            <span>High-priority actions</span>
            <strong>{highPriorityCount}</strong>
          </div>
        </article>

        <article className="cr-summary-card">
          <span className="cr-summary-icon cr-amber">◎</span>
          <div>
            <span>Identified skill gaps</span>
            <strong>{gapCount}</strong>
          </div>
        </article>
      </section>

      <section className="cr-intro-panel">
        <div className="cr-intro-icon">✧</div>
        <div>
          <span className="cr-eyebrow">YOUR ACTION PLAN</span>
          <h2>From skill gaps to skill growth</h2>
          <p>
            Each recommendation connects an industry skill with curriculum
            improvement suggestions and practical steps students can take.
            Prioritize high-priority gaps first.
          </p>
        </div>
      </section>

      <section className="cr-results">
        <div className="cr-results-header">
          <div>
            <span className="cr-eyebrow">RECOMMENDATION REPORT</span>
            <h2>Recommended actions</h2>
            <p>AI-generated guidance based on the selected syllabus.</p>
          </div>

          <span className="cr-count">
            {recommendations.length} recommendations
          </span>
        </div>

        {recommendations.length === 0 ? (
          <div className="cr-empty">
            <span>✧</span>
            <h3>No recommendations available</h3>
            <p>
              No recommendations were returned for this syllabus and job.
            </p>
          </div>
        ) : (
          <div className="cr-list">
            {recommendations.map((item, index) => {
              const priority = getPriority(item.priority);
              const status = getStatus(item.status);

              return (
                <article
                  className="cr-card"
                  key={item.id ?? `${item.skill}-${index}`}
                >
                  <div className="cr-card-header">
                    <div className="cr-skill-heading">
                      <span className="cr-number">
                        {String(index + 1).padStart(2, "0")}
                      </span>

                      <div>
                        <h3>{item.skill || "Skill recommendation"}</h3>
                        <span className={`cr-status cr-status-${status.toLowerCase()}`}>
                          {status === "REVIEW" ? "Needs review" : status}
                        </span>
                      </div>
                    </div>

                    <span className={`cr-priority cr-priority-${priority.toLowerCase()}`}>
                      {priority} PRIORITY
                    </span>
                  </div>

                  <div className="cr-card-content">
                    <section className="cr-detail-block cr-analysis-block">
                      <div className="cr-detail-title">
                        <span className="cr-detail-icon">✦</span>
                        <h4>AI analysis</h4>
                      </div>
                      <p>
                        {item.explanation ||
                          "No analysis explanation is available."}
                      </p>
                    </section>

                    <section className="cr-detail-block">
                      <div className="cr-detail-title">
                        <span className="cr-detail-icon cr-detail-blue">▤</span>
                        <h4>Curriculum improvement</h4>
                      </div>
                      <p>
                        {item.curriculumRecommendation ||
                          "No curriculum recommendation is available."}
                      </p>
                    </section>

                    <section className="cr-detail-block">
                      <div className="cr-detail-title">
                        <span className="cr-detail-icon cr-detail-green">↗</span>
                        <h4>Student action plan</h4>
                      </div>
                      <p>
                        {item.studentRecommendation ||
                          "No student recommendation is available."}
                      </p>
                    </section>
                  </div>
                  {status !== "MATCHED" && (
                    <section className="cr-roadmap">
                      <div className="cr-roadmap-heading">
                        <div>
                          <span className="cr-eyebrow">LEARNING ROADMAP</span>
                          <h4>Build {item.skill || "this skill"} step by step</h4>
                        </div>
                        <span>Free learning resources</span>
                      </div>
                      {Array.isArray(item.learningRoadmap) &&
                      item.learningRoadmap.length > 0 ? (
                        <ol>
                          {item.learningRoadmap.map((step, stepIndex) => (
                            <li key={`${step.title}-${stepIndex}`}>
                              <strong>{step.title || `Step ${stepIndex + 1}`}</strong>
                              <p>{step.action}</p>
                            </li>
                          ))}
                        </ol>
                      ) : (
                        <p className="cr-roadmap-empty" role="status">
                          No learning steps were returned for this skill.
                          Refresh recommendations to try again.
                        </p>
                      )}
                      <div className="cr-resource-links">
                        {getLearningResources(item.skill).map((resource) => (
                          <a
                            href={resource.url}
                            key={resource.label}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            {resource.label}
                          </a>
                        ))}
                      </div>
                    </section>
                  )}
                </article>
              );
            })}
          </div>
        )}
      </section>

      <footer className="cr-footer">
        CampusPulse · AI-powered learning recommendations
      </footer>
    </main>
  );
}

export default AIRecommendations;
