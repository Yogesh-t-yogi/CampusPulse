
import { useEffect, useState } from "react";
import api from "../apiClient";

function GapAnalysis({ syllabusId, jobId, token, embedded = false }) {
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function fetchGapReport() {
      setReport(null);
      setError("");

      if (!syllabusId) {
        setError("Please select a syllabus to analyze.");
        setLoading(false);
        return;
      }

      if (!jobId) {
        setError("Choose a job profile to analyze your skill gaps.");
        setLoading(false);
        return;
      }

      if (!token) {
        setError("Your session is missing. Please log in again.");
        setLoading(false);
        return;
      }

      setLoading(true);

      try {
        const response = await api.get(
          `/api/matching/semantic/report/jobs/${jobId}/syllabus/${syllabusId}`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          }
        );

        if (cancelled) return;
        setReport(response.data);
      } catch (err) {
        if (cancelled) return;

        console.error("Gap analysis error:", {
          status: err.response?.status,
          url: err.config?.url,
          message: err.message,
        });

        if (err.response?.status === 401) {
          setError("Your session has expired. Please log in again.");
        } else if (err.response?.status === 403) {
          setError(
            "Access denied. Check your account permissions and selected syllabus."
          );
        } else if (err.response?.status === 404) {
          setError("The selected syllabus or job was not found.");
        } else if (!err.response) {
          setError("Cannot connect to the backend on port 8081.");
        } else {
          setError("Failed to load the skill gap report. Please try again.");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    fetchGapReport();

    return () => {
      cancelled = true;
    };
  }, [syllabusId, jobId, token, reloadKey]);

  if (loading) {
    return (
      <main className={`page-content cp-gap${embedded ? " cp-embedded" : ""}`}>
        <div className="cg-state">
          <div className="cg-spinner" />
          <h2>Analyzing skill alignment</h2>
          <p>Comparing curriculum topics with industry requirements...</p>
        </div>
      </main>
    );
  }

  if (error || !report) {
    return (
      <main className={`page-content cp-gap${embedded ? " cp-embedded" : ""}`}>
        <div className="cg-state cg-state-card">
          <span className="cg-state-icon">!</span>
          <h1>Skill Gap Analysis</h1>
          <p>{error || "No gap analysis report is available."}</p>
          <button
            type="button"
            className="cg-primary-button"
            onClick={() => setReloadKey((key) => key + 1)}
          >
            Try again
          </button>
        </div>
      </main>
    );
  }

  const totalSkills = report.totalSkills ?? 0;
  const matchedSkills = report.matchedSkills ?? 0;
  const partialSkills = report.partialSkills ?? 0;
  const gapSkills = report.gapSkills ?? 0;
  const coverage = Math.max(
    0,
    Math.min(100, Number(report.coveragePercentage) || 0)
  );

  const skillDetails = Array.isArray(report.skillDetails)
    ? report.skillDetails
    : [];

  const statusConfig = {
    MATCHED: {
      label: "Matched",
      className: "matched",
      description: "Covered by the curriculum",
    },
    PARTIAL: {
      label: "Partial match",
      className: "partial",
      description: "Some learning coverage exists",
    },
    GAP: {
      label: "Skill gap",
      className: "gap",
      description: "Additional learning is recommended",
    },
  };

  const getStatus = (status) =>
    statusConfig[String(status || "").toUpperCase()] || {
      label: status || "Unclassified",
      className: "unknown",
      description: "Review this skill",
    };

  const getSimilarity = (similarity) =>
    Math.max(0, Math.min(100, (Number(similarity) || 0) * 100));

  const getCoverageMessage = () => {
    if (coverage >= 80) {
      return "Strong alignment with the selected industry skill requirements.";
    }
    if (coverage >= 60) {
      return "Good progress, with some opportunities to strengthen coverage.";
    }
    if (coverage >= 40) {
      return "Several industry skills could benefit from deeper curriculum coverage.";
    }
    return "There are important opportunities to improve curriculum alignment.";
  };

  return (
    <main className="page-content cp-gap cg-pdf-report">
      <header className="cg-print-header">
        <strong>CampusPulse</strong>
        <span>
          Skill gap report · Syllabus {syllabusId} · Job profile {jobId}
        </span>
      </header>
      <section className="cg-hero">
        <div>
          <span className="cg-eyebrow">
            INDUSTRY ALIGNMENT · SEMANTIC ANALYSIS
          </span>
          <h1>Skill Gap Analysis</h1>
          <p>
            Discover which industry skills are covered by your curriculum,
            which are partially covered, and which need attention.
          </p>
        </div>

        <div className="cg-hero-icon" aria-hidden="true">
          <span>⌁</span>
        </div>
      </section>
      <div className="cg-export-row cg-no-print">
        <p>Save a faculty-ready PDF using your browser's print dialog.</p>
        <button type="button" onClick={() => window.print()}>
          Export report as PDF
        </button>
      </div>

      <section className="cg-overview">
        <div className="cg-overview-copy">
          <span className="cg-eyebrow">CURRICULUM COVERAGE</span>
          <h2>{coverage}% alignment</h2>
          <p>{getCoverageMessage()}</p>

          <div
            className="cg-progress-track"
            role="progressbar"
            aria-label="Industry skill coverage"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={coverage}
          >
            <div
              className="cg-progress-fill"
              style={{ width: `${coverage}%` }}
            />
          </div>

          <div className="cg-progress-labels">
            <span>0% coverage</span>
            <strong>{coverage}%</strong>
            <span>100%</span>
          </div>
        </div>

        <div className="cg-overview-circle">
          <div className="cg-overview-circle-inner">
            <strong>{matchedSkills}</strong>
            <span>Fully matched</span>
            <small>of {totalSkills} skills</small>
          </div>
        </div>
      </section>

      <section className="cg-stat-grid">
        <article className="cg-stat-card cg-stat-total">
          <div className="cg-stat-heading">
            <span>Total skills</span>
            <span className="cg-stat-icon">◎</span>
          </div>
          <strong>{totalSkills}</strong>
          <p>Industry requirements analyzed</p>
        </article>

        <article className="cg-stat-card cg-stat-matched">
          <div className="cg-stat-heading">
            <span>Matched</span>
            <span className="cg-stat-icon">✓</span>
          </div>
          <strong>{matchedSkills}</strong>
          <p>Covered by the curriculum</p>
        </article>

        <article className="cg-stat-card cg-stat-partial">
          <div className="cg-stat-heading">
            <span>Partial matches</span>
            <span className="cg-stat-icon">◐</span>
          </div>
          <strong>{partialSkills}</strong>
          <p>Coverage can be improved</p>
        </article>

        <article className="cg-stat-card cg-stat-gap">
          <div className="cg-stat-heading">
            <span>Skill gaps</span>
            <span className="cg-stat-icon">!</span>
          </div>
          <strong>{gapSkills}</strong>
          <p>Require additional attention</p>
        </article>
      </section>

      <section className="cg-results">
        <div className="cg-results-header">
          <div>
            <span className="cg-eyebrow">SKILL-BY-SKILL BREAKDOWN</span>
            <h2>Industry skill comparison</h2>
            <p>
              Semantic similarity estimates between required skills and
              curriculum topics.
            </p>
          </div>
          <span className="cg-result-count">
            {skillDetails.length} skills analyzed
          </span>
        </div>

        <div className="cg-legend">
          <span><i className="cg-legend-dot cg-dot-matched" /> Matched</span>
          <span><i className="cg-legend-dot cg-dot-partial" /> Partial</span>
          <span><i className="cg-legend-dot cg-dot-gap" /> Gap</span>
        </div>

        {skillDetails.length === 0 ? (
          <div className="cg-empty">
            <h3>No skill details available</h3>
            <p>The report does not contain individual skill results.</p>
          </div>
        ) : (
          <div className="cg-skill-list">
            {skillDetails.map((item, index) => {
              const status = getStatus(item.status);
              const similarity = getSimilarity(item.similarity);

              return (
                <article
                  className="cg-skill-card"
                  key={`${item.skill || "skill"}-${index}`}
                >
                  <div className="cg-skill-main">
                    <span className="cg-skill-number">
                      {String(index + 1).padStart(2, "0")}
                    </span>

                    <div className="cg-skill-title">
                      <h3>{item.skill || "Unnamed skill"}</h3>
                      <span className={`cg-status cg-status-${status.className}`}>
                        <span className="cg-status-dot" />
                        {status.label}
                      </span>
                    </div>
                  </div>

                  <div className="cg-skill-match">
                    <span className="cg-field-label">CURRICULUM TOPIC</span>
                    <strong>
                      {item.topic || "No matching topic identified"}
                    </strong>
                    {item.topic ? (
                      <span className="cg-topic-note">
                        Topic ID: {item.topicId ?? "—"}
                      </span>
                    ) : (
                      <span className="cg-topic-note">
                        Additional curriculum coverage may be needed
                      </span>
                    )}
                  </div>

                  <div className="cg-similarity">
                    <div className="cg-similarity-heading">
                      <span>Similarity score</span>
                      <strong>{similarity.toFixed(1)}%</strong>
                    </div>

                    <div
                      className="cg-similarity-track"
                      role="progressbar"
                      aria-label={`${item.skill} similarity`}
                      aria-valuemin={0}
                      aria-valuemax={100}
                      aria-valuenow={similarity}
                    >
                      <div
                        className={`cg-similarity-fill cg-fill-${status.className}`}
                        style={{ width: `${similarity}%` }}
                      />
                    </div>

                    <span className="cg-similarity-caption">
                      {status.description}
                    </span>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <section className="cg-insight">
        <div className="cg-insight-icon">✦</div>
        <div>
          <span className="cg-eyebrow">ANALYSIS SUMMARY</span>
          <h2>What these results mean</h2>
          <p>
            Semantic similarity helps identify potentially related curriculum
            topics. A high score indicates stronger textual or semantic
            alignment, but it does not independently prove mastery or complete
            coverage. Review the matched topic and learning outcomes when
            deciding whether a skill is adequately taught.
          </p>
        </div>
      </section>

      <footer className="cg-footer">
        CampusPulse · Industry skill gap intelligence
      </footer>
    </main>
  );
}

export default GapAnalysis;
