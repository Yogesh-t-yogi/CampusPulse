import { useEffect, useState } from "react";
import { GitCompare, History } from "lucide-react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import api, { errorMessage } from "../apiClient";
import { Alert, Badge, Button, Card, DataTable, EmptyState, Skeleton } from "../ui";

const STATUS_TONE = { MATCHED: "success", PARTIAL: "warning", GAP: "danger" };
const STATUS_LABEL = { MATCHED: "Covered", PARTIAL: "Partly", GAP: "Gap" };
const when = (value) => (value ? new Date(value).toLocaleString() : "—");

// Report history (5.3): coverage of this job over time across syllabus versions,
// and what changed between this version and an earlier one.
function ReportHistory({ syllabusId, jobId }) {
  const [runs, setRuns] = useState(null);
  const [versions, setVersions] = useState([]);
  const [error, setError] = useState("");
  const [against, setAgainst] = useState("");
  const [diff, setDiff] = useState(null);
  const [diffing, setDiffing] = useState(false);

  useEffect(() => {
    if (!syllabusId || !jobId) return;
    let cancelled = false;
    Promise.all([
      api.get(`/api/syllabus/${syllabusId}/history`, { params: { jobId } }),
      api.get(`/api/syllabus/${syllabusId}/versions`),
    ])
      .then(([history, chain]) => {
        if (cancelled) return;
        setRuns(history.data || []);
        const list = chain.data || [];
        setVersions(list);
        if (list.length > 1) setAgainst(String(list[1].id));
      })
      .catch((err) => !cancelled && setError(errorMessage(err, "Couldn't load the history.")));
    return () => {
      cancelled = true;
    };
  }, [syllabusId, jobId]);

  const compare = async () => {
    setDiffing(true);
    setError("");
    try {
      const { data } = await api.get(`/api/syllabus/${syllabusId}/diff`, { params: { against, jobId } });
      setDiff(data);
    } catch (err) {
      setError(errorMessage(err, "Couldn't compare the versions."));
    } finally {
      setDiffing(false);
    }
  };

  if (!syllabusId || !jobId) return <EmptyState icon={History} title="Pick a syllabus and a job first" />;
  if (error && !runs) return <Alert tone="danger">{error}</Alert>;
  if (!runs) return <Skeleton lines={6} />;

  const chart = runs.map((run) => ({
    label: `v${run.versionNo} · ${new Date(run.createdAt).toLocaleDateString()}`,
    coverage: Math.round(run.coverage),
  }));

  return (
    <div className="ui-stack">
      {error && <Alert tone="danger">{error}</Alert>}
      <Card title="Coverage over time" description="Every time this report was computed or re-run, across all versions of the syllabus.">
        {runs.length === 0 ? (
          <EmptyState icon={History} title="No history yet" description="History starts with the next run of this report." />
        ) : (
          <>
            {runs.length > 1 && (
              <div style={{ width: "100%", height: 220 }}>
                <ResponsiveContainer>
                  <LineChart data={chart} margin={{ top: 8, right: 16, left: 0, bottom: 8 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--cp-border)" />
                    <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                    <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Line type="monotone" dataKey="coverage" stroke="var(--cp-accent)" strokeWidth={2} dot />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}
            <DataTable
              columns={[
                { key: "createdAt", label: "When", render: (r) => when(r.createdAt) },
                { key: "versionNo", label: "Version", render: (r) => <Badge tone="info">v{r.versionNo}</Badge> },
                { key: "coverage", label: "Coverage", render: (r) => `${Math.round(r.coverage)}%` },
                { key: "matchedSkills", label: "Covered" },
                { key: "partialSkills", label: "Partly" },
                { key: "gapSkills", label: "Gaps" },
              ]}
              rows={[...runs].reverse()}
            />
          </>
        )}
      </Card>

      <Card title="Compare syllabus versions" description="Topics added or removed, and skills whose status changed for this job.">
        {versions.length < 2 ? (
          <EmptyState
            icon={GitCompare}
            title="Only one version"
            description='Upload a revised PDF with "Upload new version" on the Syllabi page to compare.'
          />
        ) : (
          <>
            <div className="ui-row-actions" style={{ marginBottom: 12 }}>
              <span>v{versions[0].versionNo} (this) against</span>
              <select className="ui-select" style={{ width: "auto" }} value={against} onChange={(e) => setAgainst(e.target.value)}>
                {versions.slice(1).map((v) => (
                  <option key={v.id} value={String(v.id)}>
                    v{v.versionNo} · {new Date(v.createdAt).toLocaleDateString()}
                  </option>
                ))}
              </select>
              <Button variant="primary" icon={GitCompare} onClick={compare} disabled={diffing || !against}>
                {diffing ? "Comparing…" : "Compare"}
              </Button>
            </div>
            {diff && (
              <div className="ui-stack">
                {diff.coverageBefore !== undefined && (
                  <p>
                    Coverage <strong>{Math.round(diff.coverageBefore)}%</strong> →{" "}
                    <strong>{Math.round(diff.coverageAfter)}%</strong>{" "}
                    <Badge tone={diff.coverageAfter >= diff.coverageBefore ? "success" : "danger"}>
                      {diff.coverageAfter >= diff.coverageBefore ? "+" : ""}
                      {Math.round(diff.coverageAfter - diff.coverageBefore)} pts
                    </Badge>
                  </p>
                )}
                {diff.skillChanges?.length > 0 && (
                  <div className="ui-chip-list">
                    {diff.skillChanges.map((change) => (
                      <Badge key={change.skill} tone={STATUS_TONE[change.after]}>
                        {change.skill}: {STATUS_LABEL[change.before]} → {STATUS_LABEL[change.after]}
                      </Badge>
                    ))}
                  </div>
                )}
                {diff.subjects.length === 0 ? (
                  <p className="ui-muted">No topic changes.</p>
                ) : (
                  <DataTable
                    columns={[
                      {
                        key: "subject",
                        label: "Subject",
                        render: (s) => (
                          <span>
                            {s.subject}{" "}
                            {s.status !== "CHANGED" && <Badge tone={s.status === "NEW" ? "success" : "danger"}>{s.status === "NEW" ? "New" : "Removed"}</Badge>}
                          </span>
                        ),
                      },
                      { key: "added", label: "Topics added", render: (s) => s.added.join(", ") || "—" },
                      { key: "removed", label: "Topics removed", render: (s) => s.removed.join(", ") || "—" },
                    ]}
                    rows={diff.subjects.map((s) => ({ ...s, id: s.subject }))}
                  />
                )}
              </div>
            )}
          </>
        )}
      </Card>
    </div>
  );
}

export default ReportHistory;
