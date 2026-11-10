import { useState } from "react";
import { useSearchParams } from "react-router";
import { FileSpreadsheet, FileText, Printer, RefreshCw } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Button, PageHeader } from "../ui";
import Dashboard from "./Dashboard";
import GapAnalysis from "./GapAnalysis";
import AIRecommendations from "./AIRecommendations";
import CompareJobs from "./CompareJobs";
import OutcomesReport from "./OutcomesReport";
import ReportHistory from "./ReportHistory";

const TABS = [
  { id: "overview", label: "Overview" },
  { id: "skills", label: "Skill by skill" },
  { id: "recommendations", label: "Recommendations" },
  { id: "outcomes", label: "Outcomes (CO/PO)" },
  { id: "compare", label: "Compare jobs" },
  { id: "history", label: "History" },
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

  const [exporting, setExporting] = useState("");

  // Server-made file (college, date, skills, recommendations, PO coverage).
  const download = async (kind) => {
    setExporting(kind);
    setNotice("");
    try {
      const { data } = await api.get(`/api/export/report/${kind}`, {
        params: { syllabusId, jobId },
        responseType: "blob",
      });
      const slug = `${syllabusName || "syllabus"}-${jobTitle || "job"}`.replace(/[^A-Za-z0-9]+/g, "-").toLowerCase();
      const url = URL.createObjectURL(data);
      const link = document.createElement("a");
      link.href = url;
      link.download = `gap-report-${slug}.${kind === "pdf" ? "pdf" : "xlsx"}`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      setNotice(errorMessage(error, "Couldn't create the file. Try again."));
    } finally {
      setExporting("");
    }
  };

  const pageProps = { syllabusId, jobId, token, embedded: true };

  return (
    <div className="cp-report">
      <div className="cp-report-head">
        <PageHeader
          eyebrow="Step 4 of 4 · Skill gap report"
          title={jobTitle || "Selected job"}
          description={`Compared with ${syllabusName || "the selected syllabus"}. Results are saved; re-run after changing the curriculum or job skills.`}
          actions={
            <>
              <Button icon={RefreshCw} onClick={rerun} disabled={rerunning || !syllabusId || !jobId}>
                {rerunning ? "Re-running…" : "Re-run analysis"}
              </Button>
              <Button icon={Printer} onClick={() => window.print()}>
                Print
              </Button>
              <Button icon={FileSpreadsheet} onClick={() => download("excel")} disabled={!!exporting || !syllabusId || !jobId}>
                {exporting === "excel" ? "Preparing…" : "Excel"}
              </Button>
              <Button variant="primary" icon={FileText} onClick={() => download("pdf")} disabled={!!exporting || !syllabusId || !jobId}>
                {exporting === "pdf" ? "Preparing…" : "Download PDF"}
              </Button>
            </>
          }
        />
      </div>

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
        {tab === "outcomes" && (
          <OutcomesReport key={`co-${version}`} syllabusId={syllabusId} jobId={jobId} />
        )}
        {tab === "history" && <ReportHistory key={`h-${version}`} syllabusId={syllabusId} jobId={jobId} />}
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
