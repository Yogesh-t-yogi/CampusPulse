
import { useCallback, useEffect, useState } from "react";
import api from "../apiClient";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

const JOBS_PER_PAGE = 24;

// One component, three pages: choose a job (everyone), market insights
// (everyone), and catalog management (admins only).
const SECTION_COPY = {
  choose: {
    eyebrow: "STEP 3 · CHOOSE A JOB",
    title: "Choose a job",
    text: "Pick a role from the catalog or paste a job description. Its skills are compared with your curriculum.",
  },
  insights: {
    eyebrow: "MARKET INSIGHTS",
    title: "Market insights",
    text: "See which skills employers ask for most across the job catalog.",
  },
  resume: {
    eyebrow: "STUDENT · RESUME MATCH",
    title: "Resume match",
    text: "Upload your resume to see which catalog jobs fit your skills and what you still need to learn.",
  },
  catalog: {
    eyebrow: "ADMIN · JOB CATALOG",
    title: "Job catalog",
    text: "Import job datasets and keep the shared catalog's skill lists up to date.",
  },
};

function JobMarket({
  section = "choose",
  isAdmin = false,
  selectedJobId,
  hasSyllabus,
  onJobCreated,
  onJobSelected,
  onCatalogImported,
}) {
  const [jobs, setJobs] = useState([]);
  const [jobPage, setJobPage] = useState(1);
  const [jobSearch, setJobSearch] = useState("");
  const [jobSkills, setJobSkills] = useState({});
  const [extractingJob, setExtractingJob] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [customJob, setCustomJob] = useState({
    title: "",
    company: "",
    location: "",
    description: "",
  });
  const [creatingCustomJob, setCreatingCustomJob] = useState(false);
  const [csvFile, setCsvFile] = useState(null);
  const [importingCsv, setImportingCsv] = useState(false);
  const [skillDemand, setSkillDemand] = useState([]);
  const [skillDemandError, setSkillDemandError] = useState("");
  const [resumeFile, setResumeFile] = useState(null);
  const [resumeConsent, setResumeConsent] = useState(false);
  const [matchingResume, setMatchingResume] = useState(false);
  const [resumeError, setResumeError] = useState("");
  const [resumeResult, setResumeResult] = useState(null);

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
      const response = await api.get(
        `/api/jobs/${jobId}/skills`,
        getAuthConfig()
      );
      setJobSkills((previous) => ({
        ...previous,
        [jobId]: Array.isArray(response.data) ? response.data : [],
      }));
    },
    [getAuthConfig]
  );

  const fetchSkillDemand = useCallback(async () => {
    setSkillDemandError("");
    try {
      const response = await api.get(
        `/api/job-catalog/skill-demand`,
        getAuthConfig()
      );
      setSkillDemand(Array.isArray(response.data) ? response.data : []);
    } catch (err) {
      console.error("Failed to load dataset skill demand:", {
        status: err.response?.status,
        message: err.message,
      });
      setSkillDemandError(
        err.response?.data?.message ||
          err.response?.data?.detail ||
          "Could not load skill-demand data."
      );
    }
  }, [getAuthConfig]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void fetchSkillDemand();
    }, 0);

    return () => window.clearTimeout(timer);
  }, [fetchSkillDemand]);

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
      const response = await api.get(
        `/api/jobs`,
        getAuthConfig()
      );

      const jobList = Array.isArray(response.data) ? response.data : [];
      setJobs(jobList);
      setJobPage(1);

      setJobSkills({});
      try {
        const skillsResponse = await api.get(
          `/api/jobs/skills`,
          getAuthConfig()
        );
        const skillsByJob = Object.fromEntries(
          Object.entries(skillsResponse.data || {}).map(([jobId, skills]) => [
            jobId,
            Array.isArray(skills) ? skills.map((skill) => ({ skill })) : [],
          ])
        );
        setJobSkills(skillsByJob);
      } catch (skillsError) {
        console.error("Failed to load job skills:", {
          status: skillsError.response?.status,
          message: skillsError.message,
        });
        setNotice(
          "Job postings loaded, but some required skills could not be loaded."
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
    const timer = window.setTimeout(() => {
      void fetchJobs();
    }, 0);

    return () => window.clearTimeout(timer);
  }, [fetchJobs]);

  const extractSkillsWithAI = async (jobId) => {
    setExtractingJob(jobId);
    setError("");
    setNotice("");

    try {
      await api.post(
        `/api/ai/jobs/${jobId}/extract-skills`,
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
        setError(
          err.response?.data?.message ||
            "Only an admin can re-extract skills for shared catalog jobs."
        );
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

  const createCustomJob = async (event) => {
    event.preventDefault();
    setCreatingCustomJob(true);
    setError("");
    setNotice("");

    try {
      const response = await api.post(
        `/api/jobs/custom`,
        customJob,
        getAuthConfig()
      );
      setCustomJob({
        title: "",
        company: "",
        location: "",
        description: "",
      });
      await fetchJobs();
      setNotice("Job profile created and skills extracted. Continuing to curriculum review.");
      onJobCreated?.(response.data);
    } catch (err) {
      console.error("Custom job profile creation failed:", {
        status: err.response?.status,
        message: err.message,
      });
      setError(
        err.response?.data?.message ||
          err.response?.data?.detail ||
          (err.response?.status === 401
            ? "Your session has expired. Please sign in again."
            : !err.response
              ? "Cannot connect to the backend. Check port 8081."
              : "Could not create this job profile. Check the description and try again.")
      );
    } finally {
      setCreatingCustomJob(false);
    }
  };

  const importDataset = async (event) => {
    event.preventDefault();
    if (!csvFile) {
      setError("Choose a CSV dataset before importing.");
      return;
    }

    setImportingCsv(true);
    setError("");
    setNotice("");
    const formData = new FormData();
    formData.append("file", csvFile);

    try {
      const response = await api.post(
        `/api/job-catalog/import`,
        formData,
        getAuthConfig()
      );
      setCsvFile(null);
      await Promise.all([
        fetchJobs(),
        fetchSkillDemand(),
        onCatalogImported?.(),
      ]);
      setNotice(
        `${response.data.imported} job${response.data.imported === 1 ? "" : "s"} imported into the dataset.`
      );
    } catch (err) {
      console.error("Job dataset import failed:", {
        status: err.response?.status,
        message: err.message,
      });
      setError(
        err.response?.status === 403
          ? "Only an admin can import jobs into the shared catalog."
          : err.response?.data?.error ||
            err.response?.data?.message ||
            (!err.response
              ? "Cannot connect to the backend. Check port 8081."
              : "Could not import this CSV dataset.")
      );
    } finally {
      setImportingCsv(false);
    }
  };

  const matchResume = async (event) => {
    event.preventDefault();
    setResumeError("");
    setResumeResult(null);

    if (!resumeFile) {
      setResumeError("Choose a PDF resume first.");
      return;
    }
    if (!resumeConsent) {
      setResumeError("Confirm the resume processing notice before continuing.");
      return;
    }

    setMatchingResume(true);
    const formData = new FormData();
    formData.append("file", resumeFile);
    try {
      const response = await api.post(
        `/api/resume/match`,
        formData,
        getAuthConfig()
      );
      setResumeResult(response.data);
    } catch (err) {
      console.error("Resume job matching failed:", {
        status: err.response?.status,
        message: err.message,
      });
      setResumeError(
        err.response?.data?.detail ||
          err.response?.data?.message ||
          err.response?.data?.error ||
          (!err.response
            ? "Cannot connect to the backend. Check port 8081."
            : err.response?.status === 401
              ? "Your session has expired. Please sign in again."
              : "Could not extract skills from this resume. Check the PDF and try again.")
      );
    } finally {
      setMatchingResume(false);
    }
  };

  const companyCount = new Set(
    jobs
      .map((job) => (job.company || job.companyName)?.trim())
      .filter(Boolean)
      .map((name) => name.toLowerCase())
  ).size;

  const allSkills = Object.values(jobSkills).flat();
  const uniqueSkillCount = new Set(
    allSkills
      .map((skill) => skill.skill?.trim().toLowerCase())
      .filter(Boolean)
  ).size;
  const filteredJobs = jobs.filter((job) => {
    const query = jobSearch.trim().toLowerCase();
    return !query || [
      job.title,
      job.company || job.companyName,
      job.location,
      job.description,
    ].some((value) => String(value || "").toLowerCase().includes(query));
  });
  const jobPageCount = Math.max(
    1,
    Math.ceil(filteredJobs.length / JOBS_PER_PAGE)
  );
  const visibleJobs = filteredJobs.slice(
    (jobPage - 1) * JOBS_PER_PAGE,
    jobPage * JOBS_PER_PAGE
  );

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
          <span className="jm-eyebrow">{SECTION_COPY[section].eyebrow}</span>
          <h1>{SECTION_COPY[section].title}</h1>
          <p>{SECTION_COPY[section].text}</p>
        </div>

        <div className="jm-hero-icon" aria-hidden="true">
          <span>↗</span>
        </div>
      </section>

      {section === "choose" && (
      <section className="jm-custom-job">
        <div>
          <span className="jm-eyebrow">PERSONALIZED ANALYSIS</span>
          <h2>Use a job description</h2>
          <p>
            Paste a job description to extract required skills and compare
            them with your selected syllabus. Your custom profile is private
            to your account.
          </p>
          {!hasSyllabus && (
            <p className="jm-setup-note">
              Upload and process a syllabus before choosing a job profile.
            </p>
          )}
        </div>
        <form className="jm-custom-job-form" onSubmit={createCustomJob}>
          <label>
            Job title
            <input
              required
              maxLength={120}
              value={customJob.title}
              onChange={(event) =>
                setCustomJob((previous) => ({
                  ...previous,
                  title: event.target.value,
                }))
              }
              placeholder="e.g. Junior Backend Developer"
            />
          </label>
          <div className="jm-custom-job-row">
            <label>
              Company (optional)
              <input
                maxLength={120}
                value={customJob.company}
                onChange={(event) =>
                  setCustomJob((previous) => ({
                    ...previous,
                    company: event.target.value,
                  }))
                }
                placeholder="Company name"
              />
            </label>
            <label>
              Location (optional)
              <input
                maxLength={120}
                value={customJob.location}
                onChange={(event) =>
                  setCustomJob((previous) => ({
                    ...previous,
                    location: event.target.value,
                  }))
                }
                placeholder="Remote or city"
              />
            </label>
          </div>
          <label htmlFor="custom-job-description">
            Job description
            <textarea
              id="custom-job-description"
              required
              minLength={40}
              maxLength={20000}
              rows={7}
              aria-describedby="custom-job-description-hint"
              value={customJob.description}
              onChange={(event) =>
                setCustomJob((previous) => ({
                  ...previous,
                  description: event.target.value,
                }))
              }
              placeholder="Paste the responsibilities, qualifications, and technical requirements..."
            />
            <small id="custom-job-description-hint">
              Include at least 40 characters. {customJob.description.length}/20,000
            </small>
          </label>
          <button
            type="submit"
            className="jm-primary-button"
            disabled={
              !hasSyllabus ||
              creatingCustomJob ||
              !customJob.title.trim() ||
              customJob.description.trim().length < 40
            }
          >
            {creatingCustomJob
              ? "Extracting skills..."
              : "Create profile and extract skills"}
          </button>
        </form>
      </section>
      )}

      {(section === "insights" || (section === "catalog" && isAdmin)) && (
      <section className="jm-dataset-section">
      {section === "catalog" && isAdmin && (
        <div className="jm-dataset-card">
          <span className="jm-eyebrow">FREE LOCAL DATASET</span>
          <h2>Import job postings from CSV</h2>
          <p>
            Add your licensed dataset to the local catalog. Include a title
            column; company, location, description, and comma-separated skills
            are optional.
          </p>
          <form onSubmit={importDataset}>
            <label htmlFor="job-dataset-file">CSV dataset</label>
            <input
              id="job-dataset-file"
              type="file"
              accept=".csv,text/csv"
              onChange={(event) => setCsvFile(event.target.files?.[0] || null)}
            />
            <button type="submit" disabled={!csvFile || importingCsv}>
              {importingCsv ? "Importing dataset..." : "Import CSV"}
            </button>
          </form>
          <small>
            Accepted headers: title/job_title/role, company/employer,
            location/city, description, skills/required_skills.
          </small>
        </div>
        )}

      {section === "insights" && (
        <div className="jm-dataset-card jm-demand-card">
          <div className="jm-demand-heading">
            <div>
              <span className="jm-eyebrow">DATASET INSIGHTS</span>
              <h2>Most requested skills</h2>
              <p>Number of catalog postings listing each skill.</p>
            </div>
            <button
              type="button"
              className="jm-chart-refresh"
              onClick={fetchSkillDemand}
              aria-label="Refresh skill demand chart"
            >
              ↻
            </button>
          </div>
          {skillDemandError ? (
            <p className="jm-feature-error" role="alert">{skillDemandError}</p>
          ) : skillDemand.length === 0 ? (
            <p className="jm-feature-empty">
              No skill data yet. Import a CSV containing a skills column.
            </p>
          ) : (
            <div className="jm-demand-chart">
              <ResponsiveContainer width="100%" height={280}>
                <BarChart
                  data={skillDemand}
                  layout="vertical"
                  margin={{ top: 4, right: 12, left: 12, bottom: 4 }}
                >
                  <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                  <XAxis type="number" allowDecimals={false} />
                  <YAxis
                    type="category"
                    dataKey="skill"
                    width={104}
                    tick={{ fontSize: 12 }}
                  />
                  <Tooltip
                    formatter={(value) => [`${value} jobs`, "Demand"]}
                  />
                  <Bar dataKey="jobCount" fill="#6558d3" radius={[0, 5, 5, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
        )}
      </section>
      )}

      {section === "resume" && (
      <section className="jm-resume-section">
        <div className="jm-resume-copy">
          <span className="jm-eyebrow">STUDENT RESUME MATCHING</span>
          <h2>Find jobs that fit your skills</h2>
          <p>
            Upload a text-based PDF resume to extract skills and compare them
            with your job catalog. Your file is processed for this request and
            is not saved by CampusPulse.
          </p>
        </div>
        <form className="jm-resume-form" onSubmit={matchResume}>
          <label htmlFor="resume-file">Resume (PDF, max 5 MB)</label>
          <input
            id="resume-file"
            type="file"
            accept="application/pdf,.pdf"
            onChange={(event) => {
              setResumeFile(event.target.files?.[0] || null);
              setResumeResult(null);
              setResumeError("");
            }}
          />
          <label className="jm-resume-consent">
            <input
              type="checkbox"
              checked={resumeConsent}
              onChange={(event) => setResumeConsent(event.target.checked)}
            />
            <span>
              I agree that extracted resume text will be sent to the configured
              Groq API for skill extraction. CampusPulse does not save the file.
            </span>
          </label>
          <button type="submit" disabled={!resumeFile || !resumeConsent || matchingResume}>
            {matchingResume ? "Extracting and matching..." : "Match resume to jobs"}
          </button>
          {resumeError && <p className="jm-feature-error" role="alert">{resumeError}</p>}
        </form>

        {resumeResult && (
          <div className="jm-resume-results">
            <h3>Skills identified in your resume</h3>
            {resumeResult.extractedSkills?.length ? (
              <div className="jm-skill-chips">
                {resumeResult.extractedSkills.map((skill) => (
                  <span className="jm-skill-chip" key={skill}>{skill}</span>
                ))}
              </div>
            ) : (
              <p>No skills were extracted from the resume.</p>
            )}
            <h3>Best matching jobs</h3>
            {resumeResult.matches?.length ? (
              <div className="jm-resume-match-list">
                {resumeResult.matches.map((match) => (
                  <article key={match.jobId}>
                    <div>
                      <strong>{match.title}</strong>
                      <span>
                        {[match.company, match.location].filter(Boolean).join(" · ")}
                      </span>
                    </div>
                    <b>{match.matchPercentage}%</b>
                    <p>
                      Matched: {match.matchedSkills?.join(", ") || "None"}
                      {match.missingSkills?.length
                        ? ` · To learn: ${match.missingSkills.join(", ")}`
                        : ""}
                    </p>
                    {jobs.some((job) => String(job.id) === String(match.jobId)) && (
                      <button
                        type="button"
                        onClick={() => onJobSelected?.(
                          jobs.find((job) => String(job.id) === String(match.jobId))
                        )}
                      >
                        Use this job profile
                      </button>
                    )}
                  </article>
                ))}
              </div>
            ) : (
              <p>No catalog jobs matched the extracted skills. Try importing more job data.</p>
            )}
          </div>
        )}
      </section>
      )}

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

      {section !== "insights" && section !== "resume" && (
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
      )}

      {section === "choose" && (
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
      )}

      {section !== "insights" && section !== "resume" && (
      <section className="jm-listing-section">
        <div className="jm-section-header">
          <div>
            <span className="jm-eyebrow">INDUSTRY OPPORTUNITIES</span>
            <h2>Job postings</h2>
            <p>Review employers, locations, and identified skills.</p>
          </div>
          <div className="jm-listing-actions">
            <label htmlFor="job-list-search" className="jm-visually-hidden">
              Search job listings
            </label>
            <input
              id="job-list-search"
              type="search"
              value={jobSearch}
              onChange={(event) => {
                setJobSearch(event.target.value);
                setJobPage(1);
              }}
              placeholder="Search title, company, location..."
            />
            <button
              type="button"
              className="jm-refresh-button"
              onClick={fetchJobs}
            >
              ↻ Refresh
            </button>
          </div>
        </div>

        {filteredJobs.length === 0 ? (
          <div className="jm-empty">
            <span>▤</span>
            <h3>{jobs.length ? "No matching jobs found" : "No job postings found"}</h3>
            <p>
              {jobs.length
                ? "Try a different search term."
                : "Job listings will appear here when they are available in the CampusPulse database."}
            </p>
          </div>
        ) : (
          <div className="jm-job-list">
            {visibleJobs.map((job, index) => {
              const skills = jobSkills[job.id];
              const isExtracting = extractingJob === job.id;

              return (
                <article className="jm-job-card" key={job.id}>
                  <div className="jm-job-header">
                    <div className="jm-job-identity">
                      <div className="jm-company-avatar">
                        {(job.company || job.companyName || job.title || "J")
                          .trim()
                          .charAt(0)
                          .toUpperCase()}
                      </div>

                      <div>
                        <span className="jm-job-number">
                          JOB LISTING {String(
                            (jobPage - 1) * JOBS_PER_PAGE + index + 1
                          ).padStart(2, "0")}
                        </span>
                        <h3>{job.title || "Untitled position"}</h3>
                        <p className="jm-company-name">
                          {job.company || job.companyName || "Company not specified"}
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

                    {(isAdmin || job.source === "USER_PROVIDED") && (
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
                    )}
                    <button
                      type="button"
                      className="jm-secondary-button"
                      onClick={() => onJobSelected?.(job)}
                      disabled={!hasSyllabus || !onJobSelected}
                    >
                      {String(selectedJobId) === String(job.id)
                        ? "Continue with this profile"
                        : "Use this profile"}
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
        {jobPageCount > 1 && (
          <nav className="jm-pagination" aria-label="Job listings pages">
            <button
              type="button"
              onClick={() => setJobPage((page) => Math.max(1, page - 1))}
              disabled={jobPage === 1}
            >
              Previous
            </button>
            <span>Page {jobPage} of {jobPageCount}</span>
            <button
              type="button"
              onClick={() =>
                setJobPage((page) => Math.min(jobPageCount, page + 1))
              }
              disabled={jobPage === jobPageCount}
            >
              Next
            </button>
          </nav>
        )}
      </section>
      )}

      <footer className="jm-footer">
        CampusPulse · Industry skill intelligence
      </footer>
    </main>
  );
}

export default JobMarket;
