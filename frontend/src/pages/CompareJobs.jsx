import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import api, { errorMessage } from "../apiClient";

const MAX_JOBS = 10;
const STATUS_LABEL = { MATCHED: "Matched", PARTIAL: "Partial", GAP: "Gap" };

// One syllabus vs several jobs: skill × job grid, combined coverage, top gaps.
// Chosen jobs live in the URL (?tab=compare&jobs=1,4,7).
function CompareJobs({ syllabusId, jobs, currentJobId }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const fromUrl = (searchParams.get("jobs") || "")
    .split(",")
    .filter(Boolean);
  const [selected, setSelected] = useState(() =>
    fromUrl.length ? fromUrl : currentJobId ? [String(currentJobId)] : []
  );
  const [filter, setFilter] = useState("");
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(fromUrl.length > 0 && Boolean(syllabusId));
  const [error, setError] = useState("");
  const [requestedIds, setRequestedIds] = useState(fromUrl.join(","));

  useEffect(() => {
    if (!requestedIds || !syllabusId) return undefined;
    let cancelled = false;
    api
      .get("/api/matching/semantic/report/multi", {
        params: { syllabusId, jobIds: requestedIds },
      })
      .then(({ data }) => !cancelled && setReport(data))
      .catch((err) => !cancelled && setError(errorMessage(err, "Couldn't compare these jobs.")))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [requestedIds, syllabusId]);

  const toggle = (id) => {
    setSelected((list) =>
      list.includes(id)
        ? list.filter((item) => item !== id)
        : list.length >= MAX_JOBS
          ? list
          : [...list, id]
    );
  };

  const compare = () => {
    const ids = selected.join(",");
    setSearchParams({ tab: "compare", jobs: ids }, { replace: true });
    setLoading(true);
    setError("");
    setRequestedIds(ids);
  };

  const visibleJobs = jobs.filter((job) =>
    `${job.title} ${job.company || ""}`.toLowerCase().includes(filter.trim().toLowerCase())
  );

  return (
    <div className="cp-compare">
      <section className="cp-compare-picker">
        <div className="cp-compare-picker-head">
          <h2>Choose jobs to compare</h2>
          <span>
            {selected.length} of max {MAX_JOBS} selected
          </span>
        </div>
        <input
          type="search"
          className="cp-compare-filter"
          placeholder="Filter jobs"
          aria-label="Filter jobs"
          value={filter}
          onChange={(event) => setFilter(event.target.value)}
        />
        <div className="cp-compare-list">
          {visibleJobs.map((job) => {
            const id = String(job.id);
            return (
              <label key={id} className="cp-compare-option">
                <input
                  type="checkbox"
                  checked={selected.includes(id)}
                  onChange={() => toggle(id)}
                  disabled={!selected.includes(id) && selected.length >= MAX_JOBS}
                />
                <span>
                  {job.title}
                  {job.company ? <small> · {job.company}</small> : null}
                </span>
              </label>
            );
          })}
        </div>
        <button
          type="button"
          className="cp-button cp-button-primary"
          onClick={compare}
          disabled={selected.length === 0 || loading}
        >
          {loading ? "Comparing…" : `Compare ${selected.length || ""} jobs`}
        </button>
        <p className="cp-compare-hint">
          The first comparison may take a moment per job; after that results are saved.
        </p>
      </section>

      {error && (
        <p className="cp-report-notice" role="alert">
          {error}
        </p>
      )}

      {report && !loading && (
        <section className="cp-compare-result">
          <div className="cp-compare-summary">
            <div className="cp-compare-metric">
              <span>Combined coverage</span>
              <strong>{Math.round(report.combinedCoverage)}%</strong>
              <small>weighted by how many selected jobs need each skill</small>
            </div>
            <div className="cp-compare-metric">
              <span>Top missing skills</span>
              <strong>{report.topGaps.length}</strong>
              <small>{report.topGaps.slice(0, 5).join(", ") || "None"}</small>
            </div>
          </div>

          <div className="cp-table-wrap">
            <table className="cp-table cp-heatmap">
              <thead>
                <tr>
                  <th>Skill</th>
                  {report.jobs.map((job) => (
                    <th key={job.jobId}>
                      {job.title}
                      <small>{Math.round(job.coverage)}% covered</small>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {report.skills.map((row) => (
                  <tr key={row.skill}>
                    <td>
                      <strong>{row.skill}</strong>
                      <small>
                        {row.topic ? row.topic : "No matching topic"} · needed by {row.jobCount}
                      </small>
                    </td>
                    {report.jobs.map((job) =>
                      row.jobIds.includes(job.jobId) ? (
                        <td key={job.jobId}>
                          <span className={`cp-status cp-status-${row.status.toLowerCase()}`}>
                            {STATUS_LABEL[row.status] || row.status}
                          </span>
                        </td>
                      ) : (
                        <td key={job.jobId} className="cp-heatmap-empty" aria-label="Not required">
                          —
                        </td>
                      )
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}

export default CompareJobs;
