
import { useEffect, useState } from "react";
import axios from "axios";

function JobMarket() {
  // =========================================
  // STATE
  // =========================================

  const [jobs, setJobs] = useState([]);
  const [jobSkills, setJobSkills] = useState({});
  const [extractingJob, setExtractingJob] = useState(null);
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
  // LOAD JOBS WHEN PAGE OPENS
  // =========================================

  useEffect(() => {
    fetchJobs();
  }, []);

  // =========================================
  // FETCH JOBS
  // =========================================

  const fetchJobs = async () => {
    try {
      const response = await axios.get(
        "http://localhost:8081/api/jobs",
        getAuthConfig()
      );

      setJobs(response.data);

      // Fetch skills for every job
      response.data.forEach((job) => {
        fetchJobSkills(job.id);
      });
    } catch (error) {
      console.error("Failed to load jobs:", error);

      setError("Failed to load job market data.");
    } finally {
      setLoading(false);
    }
  };

  // =========================================
  // FETCH SKILLS FOR A JOB
  // =========================================

  const fetchJobSkills = async (jobId) => {
    try {
      const response = await axios.get(
        `http://localhost:8081/api/jobs/${jobId}/skills`,
        getAuthConfig()
      );

      setJobSkills((previous) => ({
        ...previous,
        [jobId]: response.data,
      }));
    } catch (error) {
      console.error(
        `Failed to load skills for job ${jobId}:`,
        error
      );
    }
  };

  // =========================================
  // EXTRACT SKILLS WITH AI
  // =========================================

  const extractSkillsWithAI = async (jobId) => {
    try {
      setExtractingJob(jobId);

      await axios.post(
        `http://localhost:8081/api/ai/jobs/${jobId}/extract-skills`,
        {},
        getAuthConfig()
      );

      // Reload skills after AI extraction
      await fetchJobSkills(jobId);
    } catch (error) {
      console.error("AI skill extraction failed:", error);

      alert(
        error.response?.data?.message ||
          "Failed to extract skills. Please try again."
      );
    } finally {
      setExtractingJob(null);
    }
  };

  // =========================================
  // LOADING STATE
  // =========================================

  if (loading) {
    return (
      <div className="page-content">
        <h1>Job Market</h1>
        <p>Loading job postings...</p>
      </div>
    );
  }

  // =========================================
  // ERROR STATE
  // =========================================

  if (error) {
    return (
      <div className="page-content">
        <h1>Job Market</h1>
        <p className="error-message">{error}</p>
      </div>
    );
  }

  // =========================================
  // MAIN PAGE
  // =========================================

  return (
    <div className="page-content">
      {/* PAGE HEADER */}
      <div className="page-header">
        <h1>Job Market</h1>

        <p>
          Explore industry job requirements and understand
          the skills companies are looking for.
        </p>
      </div>

      {/* STATISTICS */}
      <div className="stats-grid">
        <div className="stat-card">
          <h3>Total Jobs</h3>
          <strong>{jobs.length}</strong>
        </div>

        <div className="stat-card">
          <h3>Companies</h3>
          <strong>{jobs.length}</strong>
        </div>

        <div className="stat-card">
          <h3>Skills Identified</h3>
          <strong>
            {Object.values(jobSkills).flat().length}
          </strong>
        </div>

        <div className="stat-card">
          <h3>Job Sources</h3>
          <strong>{jobs.length > 0 ? "1" : "0"}</strong>
        </div>
      </div>

      {/* JOB POSTINGS */}
      <div className="card">
        <h2>Job Postings</h2>

        {jobs.length === 0 ? (
          <p>No job postings found.</p>
        ) : (
          jobs.map((job) => (
            <div
              key={job.id}
              className="recommendation-card"
            >
              {/* JOB TITLE */}
              <h3>{job.title}</h3>

              {/* COMPANY */}
              <p>
                <strong>Company:</strong>{" "}
                {job.companyName}
              </p>

              {/* LOCATION */}
              <p>
                <strong>Location:</strong>{" "}
                {job.location}
              </p>

              {/* AI SKILL EXTRACTION */}
              <button
                onClick={() => extractSkillsWithAI(job.id)}
                disabled={extractingJob === job.id}
              >
                {extractingJob === job.id
                  ? "Extracting Skills..."
                  : "Extract Skills with AI"}
              </button>

              {/* REQUIRED SKILLS */}
              <p>
                <strong>Required Skills:</strong>
              </p>

              <div className="topics-list">
                {jobSkills[job.id]?.length > 0 ? (
                  jobSkills[job.id].map((skill) => (
                    <span
                      key={skill.id}
                      className="topic-item"
                    >
                      {skill.skill}
                    </span>
                  ))
                ) : (
                  <p>
                    {jobSkills[job.id]
                      ? "No skills found for this job."
                      : "Loading skills..."}
                  </p>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

export default JobMarket;
