
import { useCallback, useEffect, useState } from "react";
import axios from "axios";

const API_BASE_URL = "https://campuspulse-backend-x5xh.onrender.com";

function JobMarket() {
  const [jobs, setJobs] = useState([]);
  const [jobSkills, setJobSkills] = useState({});
  const [extractingJob, setExtractingJob] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const getAuthConfig = useCallback(() => {
    const token = sessionStorage.getItem("campuspulse_token");

    return {
      headers: {
        Authorization: `Bearer ${token || ""}`,
      },
    };
  }, []);

  const fetchJobSkills = useCallback(
    async (jobId) => {
      const response = await axios.get(
        `${API_BASE_URL}/api/jobs/${jobId}/skills`,
        getAuthConfig()
      );

      setJobSkills((previous) => ({
        ...previous,
        [jobId]: Array.isArray(response.data) ? response.data : [],
      }));
    },
    [getAuthConfig]
  );

  const fetchJobs = useCallback(async () => {
    setLoading(true);
    setError("");
    setNotice("");

    const token = sessionStorage.getItem("campuspulse_token");

    if (!token) {
      setError("Your login session is missing. Please log in again.");
      setLoading(false);
      return;
    }

    try {
      const response = await axios.get(
        `${API_BASE_URL}/api/jobs`,
        getAuthConfig()
      );

      const jobList = Array.isArray(response.data) ? response.data : [];
      setJobs(jobList);

      // Reset the skills map for the current list of jobs.
      setJobSkills({});

      const results = await Promise.allSettled(
        jobList.map(async (job) => {
          const skillsResponse = await axios.get(
            `${API_BASE_URL}/api/jobs/${job.id}/skills`,
            getAuthConfig()
          );

          return {
            jobId: job.id,
            skills: Array.isArray(skillsResponse.data)
              ? skillsResponse.data
              : [],
          };
        })
      );

      const skillsByJob = {};

      results.forEach((result) => {
        if (result.status === "fulfilled") {
          skillsByJob[result.value.jobId] = result.value.skills;
        }
      });

      setJobSkills(skillsByJob);

      const failedSkillsRequest = results.some(
        (result) => result.status === "rejected"
      );

      if (failedSkillsRequest) {
        setNotice(
          "Some job skills could not be loaded. You can retry the page."
        );
      }
    } catch (err) {
      console.error("Failed to load job market:", {
        status: err.response?.status,
        message: err.message,
      });

      if (err.response?.status === 401) {
        setError("Your session has expired. Please log in again.");
      } else if (err.response?.status === 403) {
        setError("Your account is not authorized to view these job postings.");
      } else if (!err.response) {
        setError("Cannot connect to Spring Boot. Check that port 8081 is running.");
      } else {
        setError("Failed to load job market data. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  }, [getAuthConfig]);

  useEffect(() => {
    fetchJobs();
  }, [fetchJobs]);

  const extractSkillsWithAI = async (jobId) => {
    setExtractingJob(jobId);
    setError("");
    setNotice("");

    try {
      await axios.post(
        `${API_BASE_URL}/api/ai/jobs/${jobId}/extract-skills`,
        {},
        getAuthConfig()
      );

      await fetchJobSkills(jobId);
      setNotice("AI skill extraction completed successfully.");
    } catch (err) {
      console.error("AI skill extraction failed:", {
        status: err.response?.status,
        message: err.message,
      });

      if (err.response?.status === 401) {
        setError("Your session has expired. Please log in again.");
      } else if (err.response?.status === 403) {
        setError("You are not authorized to extract skills for this job.");
      } else if (!err.response) {
        setError("Cannot connect to the backend. Check port 8081.");
      } else {
        setError(
          err.response?.data?.message ||
            "AI skill extraction failed. Please try again."
        );
      }
    } finally {
      setExtractingJob(null);
    }
  };

  const companyCount = new Set(
    jobs
      .map((job) => job.companyName?.trim())
      .filter(Boolean)
      .map((name) => name.toLowerCase())
  ).size;

  const allSkills = Object.values(jobSkills).flat();
  const uniqueSkillCount = new Set(
    allSkills
      .map((skill) => skill.skill?.trim().toLowerCase())
      .filter(Boolean)
  ).size;

  if (loading) {
    return (
      <main className="page-content cp-jobs">
        <div className="jm-state">
          <div className="jm-spinner" />
          <h2>Loading the job market</h2>
          <p>Collecting job postings and their required skills...</p>
        </div>
      </main>
    );
  }

  if (error && jobs.length === 0) {
    return (
      <main className="page-content cp-jobs">
        <div className="jm-state jm-state-card">
          <span className="jm-state-icon">!</span>
          <h1>Job Market</h1>
          <p role="alert">{error}</p>
          <button
            type="button"
            className="jm-primary-button"
            onClick={fetchJobs}
          >
            Retry loading
          </button>
        </div>
      </main>
    );
  }

  return (
    <main className="page-content cp-jobs">
      <section className="jm-hero">
        <div>
          <span className="jm-eyebrow">INDUSTRY INSIGHTS · SKILL DISCOVERY</span>
          <h1>Job Market</h1>
          <p>
            Explore industry job requirements, discover in-demand skills, and
            use AI to identify the capabilities employers are looking for.
          </p>
        </div>

        <div className="jm-hero-icon" aria-hidden="true">
          <span>↗</span>
        </div>
      </section>

      {error && (
        <div className="jm-alert jm-alert-error" role="alert">
          <span>{error}</span>
          <button type="button" onClick={() => setError("")}>
            Dismiss
          </button>
        </div>
      )}

      {notice && (
        <div className="jm-alert jm-alert-success" role="status">
          <span>{notice}</span>
          <button type="button" onClick={() => setNotice("")}>
            Dismiss
          </button>
        </div>
      )}

      <section className="jm-stats">
        <article className="jm-stat-card">
          <span className="jm-stat-icon jm-icon-purple">▤</span>
          <div>
            <span>Total job postings</span>
            <strong>{jobs.length}</strong>
            <small>Available listings</small>
          </div>
        </article>

        <article className="jm-stat-card">
          <span className="jm-stat-icon jm-icon-blue">⌂</span>
          <div>
            <span>Companies</span>
            <strong>{companyCount}</strong>
            <small>Distinct company names</small>
          </div>
        </article>

        <article className="jm-stat-card">
          <span className="jm-stat-icon jm-icon-green">✧</span>
          <div>
            <span>Unique skills identified</span>
            <strong>{uniqueSkillCount}</strong>
            <small>Across loaded job listings</small>
          </div>
        </article>

        <article className="jm-stat-card">
          <span className="jm-stat-icon jm-icon-amber">◎</span>
          <div>
            <span>AI skill extraction</span>
            <strong>AI</strong>
            <small>On-demand analysis</small>
          </div>
        </article>
      </section>

      <section className="jm-info-panel">
        <div className="jm-info-icon">✦</div>
        <div>
          <span className="jm-eyebrow">HOW IT WORKS</span>
          <h2>Connect job requirements with curriculum skills</h2>
          <p>
            Open a job listing to review its required skills. Select
            <strong> Extract Skills with AI </strong>
            to run skill extraction for that job and refresh its skill list.
          </p>
        </div>
      </section>

      <section className="jm-listing-section">
        <div className="jm-section-header">
          <div>
            <span className="jm-eyebrow">INDUSTRY OPPORTUNITIES</span>
            <h2>Job postings</h2>
            <p>Review employers, locations, and identified skills.</p>
          </div>

          <button
            type="button"
            className="jm-refresh-button"
            onClick={fetchJobs}
          >
            ↻ Refresh
          </button>
        </div>

        {jobs.length === 0 ? (
          <div className="jm-empty">
            <span>▤</span>
            <h3>No job postings found</h3>
            <p>
              Job listings will appear here when they are available in the
              CampusPulse database.
            </p>
          </div>
        ) : (
          <div className="jm-job-list">
            {jobs.map((job, index) => {
              const skills = jobSkills[job.id];
              const isExtracting = extractingJob === job.id;

              return (
                <article className="jm-job-card" key={job.id}>
                  <div className="jm-job-header">
                    <div className="jm-job-identity">
                      <div className="jm-company-avatar">
                        {(job.companyName || job.title || "J")
                          .trim()
                          .charAt(0)
                          .toUpperCase()}
                      </div>

                      <div>
                        <span className="jm-job-number">
                          JOB LISTING {String(index + 1).padStart(2, "0")}
                        </span>
                        <h3>{job.title || "Untitled position"}</h3>
                        <p className="jm-company-name">
                          {job.companyName || "Company not specified"}
                        </p>
                      </div>
                    </div>

                    <span className="jm-job-badge">Industry role</span>
                  </div>

                  <div className="jm-job-meta">
                    <span>
                      <span className="jm-meta-icon">⌖</span>
                      {job.location || "Location not specified"}
                    </span>

                    <span>
                      <span className="jm-meta-icon">✧</span>
                      {skills ? `${skills.length} listed skills` : "Skills pending"}
                    </span>
                  </div>

                  <div className="jm-skills-section">
                    <div className="jm-skills-heading">
                      <div>
                        <h4>Required skills</h4>
                        <p>Skills identified for this position</p>
                      </div>

                      <span className="jm-skill-count">
                        {skills ? skills.length : "—"}
                      </span>
                    </div>

                    {!skills ? (
                      <p className="jm-skills-message">
                        Loading skills for this position...
                      </p>
                    ) : skills.length === 0 ? (
                      <p className="jm-skills-message">
                        No skills have been identified yet. Try AI extraction.
                      </p>
                    ) : (
                      <div className="jm-skill-chips">
                        {skills.map((skill, skillIndex) => (
                          <span
                            key={skill.id ?? `${skill.skill}-${skillIndex}`}
                            className="jm-skill-chip"
                          >
                            <span>•</span>
                            {skill.skill || "Unnamed skill"}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>

                  <div className="jm-job-footer">
                    <p>
                      <span className="jm-ai-dot" />
                      {isExtracting
                        ? "AI is analyzing this job..."
                        : "AI-powered skill discovery"}
                    </p>

                    <button
                      type="button"
                      className="jm-primary-button"
                      onClick={() => extractSkillsWithAI(job.id)}
                      disabled={isExtracting || extractingJob !== null}
                    >
                      {isExtracting ? (
                        <>
                          <span className="jm-button-spinner" />
                          Extracting skills...
                        </>
                      ) : (
                        <>✦ Extract Skills with AI</>
                      )}
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <footer className="jm-footer">
        CampusPulse · Industry skill intelligence
      </footer>
    </main>
  );
}

export default JobMarket;
