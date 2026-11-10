import { useState } from "react";
import { useSearchParams } from "react-router";
import { Download, RefreshCw } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import Dashboard from "./Dashboard";
import GapAnalysis from "./GapAnalysis";
import AIRecommendations from "./AIRecommendations";
import CompareJobs from "./CompareJobs";

const TABS = [
  { id: "overview", label: "Overview" },
  { id: "skills", label: "Skill by skill" },
  { id: "recommendations", label: "Recommendations" },
  { id: "compare", label: "Compare jobs" },
];

// One report for the selected syllabus + job. Results are saved on the server;
// "Re-run analysis" recomputes them.
function Report({ syllabusId, jobId, token, syllabusName, jobTitle, role, jobs = [] }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const requested = searchParams.get("tab");
  const tab = TABS.some((item) => item.id === requested) ? requested : "overview";

  // Bumped after a re-run so the tab reloads the fresh saved result.
  const [version, setVersion] = useState(0);
  const [rerunning, setRerunning] = useState(false);
  const [notice, setNotice] = useState("");

  const selectTab = (id) => setSearchParams({ tab: id }, { replace: true });
  // Re-run applies to the selected job; the compare tab uses saved results per job.

  const rerun = async () => {
    setRerunning(true);
    setNotice("");
    try {
      await api.get(
        `/api/matching/semantic/report/jobs/${jobId}/syllabus/${syllabusId}`,
        { params: { refresh: true } }
      );
      setVersion((value) => value + 1);
    } catch (error) {
      setNotice(errorMessage(error, "Couldn't re-run the analysis. Try again."));
    } finally {
      setRerunning(false);
    }
  };

  const pageProps = { syllabusId, jobId, token, embedded: true };

  return (
    <div className="cp-report">
      <header className="cp-report-header">
        <div>
          <h1>Skill gap report</h1>
          <p>
            {syllabusName || "Selected syllabus"} · {jobTitle || "Selected job"}
          </p>
        </div>

        <div className="cp-report-actions">
          <button
            type="button"
            className="cp-button"
            onClick={rerun}
            disabled={rerunning || !syllabusId || !jobId}
          >
            <RefreshCw size={16} aria-hidden="true" />
            {rerunning ? "Re-running…" : "Re-run analysis"}
          </button>
          <button
            type="button"
            className="cp-button cp-button-primary"
            onClick={() => window.print()}
          >
            <Download size={16} aria-hidden="true" />
            Export PDF
          </button>
        </div>
      </header>

      {notice && (
        <p className="cp-report-notice" role="alert">
          {notice}
        </p>
      )}

      <div className="cp-tabs" role="tablist" aria-label="Report sections">
        {TABS.map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            className="cp-tab"
            aria-selected={tab === item.id}
            onClick={() => selectTab(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div role="tabpanel">
        {tab === "overview" && <Dashboard key={`o-${version}`} {...pageProps} />}
        {tab === "skills" && <GapAnalysis key={`s-${version}`} {...pageProps} />}
        {tab === "compare" && (
          <CompareJobs syllabusId={syllabusId} jobs={jobs} currentJobId={jobId} />
        )}
        {tab === "recommendations" && (
          <AIRecommendations key={`r-${version}`} {...pageProps} role={role} />
        )}
      </div>
    </div>
  );
}

export default Report;
