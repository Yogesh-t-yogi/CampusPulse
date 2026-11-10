import { useEffect, useState } from "react";
import { ListChecks } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Alert, Badge, Card, EmptyState, Skeleton } from "../ui";

const STATUS_TONE = { MATCHED: "success", PARTIAL: "warning" };

// Report → Outcomes: PO/PSO coverage and, per CO, the job skills it covers and the gaps that fit it.
function OutcomesReport({ syllabusId, jobId }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    api
      .get("/api/outcomes/report", { params: { syllabusId, jobId } })
      .then((response) => !cancelled && setData(response.data))
      .catch((err) => !cancelled && setError(errorMessage(err, "Couldn't load the outcomes view.")));
    return () => {
      cancelled = true;
    };
  }, [syllabusId, jobId]);

  if (error) return <div className="cp-compare"><Alert tone="danger">{error}</Alert></div>;
  if (!data) return <div className="cp-compare"><Skeleton lines={6} /></div>;

  if (!data.courseOutcomes?.length) {
    return (
      <div className="cp-compare">
        <EmptyState
          icon={ListChecks}
          title="No course outcomes yet"
          description="Add or extract course outcomes on the Review curriculum page, then come back."
        />
      </div>
    );
  }

  const pos = data.poCoverage || [];

  return (
    <div className="cp-compare ui-form">
      <Card
        title="Program outcome coverage for this job"
        description="How much of the job's skills each PO/PSO covers through its mapped course outcomes (weighted by mapping level)."
      >
        {pos.length === 0 ? (
          <p className="ui-muted">No program outcomes are mapped yet.</p>
        ) : (
          <div className="cp-po-bars">
            {pos.map((po) => (
              <div key={po.id} className="cp-po-bar">
                <span className="cp-po-bar-label" title={po.title}>
                  <strong>{po.code}</strong> {po.title}
                </span>
                <span className="cp-po-bar-track" aria-hidden="true">
                  <span style={{ width: `${po.coverage ?? 0}%` }} />
                </span>
                <span className="cp-po-bar-value">
                  {po.coverage === null ? "not mapped" : `${Math.round(po.coverage)}%`}
                </span>
              </div>
            ))}
          </div>
        )}
      </Card>

      <div className="ui-grid ui-grid-2">
        {data.courseOutcomes.map((co) => (
          <Card
            key={co.id}
            title={`${co.code} · ${co.subjectName}`}
            description={co.description}
          >
            <p className="ui-field-label">Job skills this CO covers</p>
            <div className="ui-chip-list" style={{ marginBottom: 12 }}>
              {co.skills.length === 0 ? (
                <span className="ui-muted">None</span>
              ) : (
                co.skills.map((skill) => (
                  <Badge key={skill.skill} tone={STATUS_TONE[skill.status]}>
                    {skill.skill}
                  </Badge>
                ))
              )}
            </div>
            <p className="ui-field-label">Missing skills that fit here</p>
            <div className="ui-chip-list">
              {co.gapSkills.length === 0 ? (
                <span className="ui-muted">None</span>
              ) : (
                co.gapSkills.map((skill) => (
                  <Badge key={skill} tone="danger">
                    {skill}
                  </Badge>
                ))
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

export default OutcomesReport;
