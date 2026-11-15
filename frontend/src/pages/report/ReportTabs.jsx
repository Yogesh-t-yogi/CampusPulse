import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, CircleDashed, ExternalLink, Gauge, Lightbulb, ListChecks } from "lucide-react";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import api, { errorMessage } from "../../apiClient";
import { can } from "../../permissions";
import { Alert, Badge, Card, DataTable, EmptyState, SearchInput, Skeleton, StatCard } from "../../ui";

// Report tabs rebuilt on the UI kit (change 046): Overview, Skill by skill, Recommendations.

const STATUS = {
  MATCHED: { label: "Covered", tone: "success", color: "var(--cp-matched)" },
  PARTIAL: { label: "Partly covered", tone: "warning", color: "var(--cp-partial)" },
  GAP: { label: "Gap", tone: "danger", color: "var(--cp-gap)" },
};
const statusOf = (value) => (STATUS[String(value || "").toUpperCase()] ? String(value).toUpperCase() : "GAP");
const pct = (n) => `${Math.round(Number(n) || 0)}%`;

/** Loads one saved API result for the syllabus × job pair. */
function useSaved(path, syllabusId, jobId) {
  const [state, setState] = useState({ data: null, error: "", loading: true });
  useEffect(() => {
    if (!syllabusId || !jobId) return;
    let cancelled = false;
    api
      .get(path(jobId, syllabusId))
      .then(({ data }) => !cancelled && setState({ data, error: "", loading: false }))
      .catch((err) => !cancelled && setState({ data: null, error: errorMessage(err, "Couldn't load this part of the report."), loading: false }));
    return () => {
      cancelled = true;
    };
  }, [path, syllabusId, jobId]);
  if (!syllabusId || !jobId) return { data: null, error: "Choose a syllabus and a job first.", loading: false };
  return state;
}

const reportPath = (jobId, syllabusId) => `/api/matching/semantic/report/jobs/${jobId}/syllabus/${syllabusId}`;
const recommendationsPath = (jobId, syllabusId) => `/api/recommendations/jobs/${jobId}/syllabus/${syllabusId}`;

function Guard({ state, lines = 6, children }) {
  if (state.loading) return <Skeleton lines={lines} />;
  if (state.error) return <Alert tone="danger">{state.error}</Alert>;
  return children;
}

// ------------------------------------------------------------------ Overview

export function ReportOverview({ syllabusId, jobId }) {
  const state = useSaved(reportPath, syllabusId, jobId);
  return (
    <Guard state={state}>
      {state.data && <OverviewBody report={state.data} />}
    </Guard>
  );
}

function OverviewBody({ report }) {
  const skills = report.skillDetails || [];
  const pie = [
    { key: "MATCHED", value: report.matchedSkills },
    { key: "PARTIAL", value: report.partialSkills },
    { key: "GAP", value: report.gapSkills },
  ].filter((slice) => slice.value > 0);
  const gaps = skills.filter((s) => statusOf(s.status) === "GAP");
  const strongest = skills
    .filter((s) => statusOf(s.status) === "MATCHED")
    .sort((a, b) => (b.similarity || 0) - (a.similarity || 0))
    .slice(0, 8);

  return (
    <div className="ui-stack">
      <div className="ui-grid ui-grid-4">
        <StatCard icon={Gauge} label="Coverage" value={pct(report.coveragePercentage)} hint={`${report.totalSkills} job skills`} />
        <StatCard icon={CheckCircle2} label="Covered" value={report.matchedSkills} hint="taught in the syllabus" tone="success" />
        <StatCard icon={CircleDashed} label="Partly covered" value={report.partialSkills} hint="related topics only" tone="warning" />
        <StatCard icon={AlertTriangle} label="Gaps" value={report.gapSkills} hint="not in the syllabus" tone="danger" />
      </div>

      <div className="ui-grid ui-grid-2">
        <Card title="Skill coverage" description="Share of the job's skills the syllabus covers.">
          <div className="rp-donut">
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie data={pie} dataKey="value" nameKey="key" innerRadius={60} outerRadius={90} paddingAngle={2}>
                  {pie.map((slice) => (
                    <Cell key={slice.key} fill={STATUS[slice.key].color} />
                  ))}
                </Pie>
                <Tooltip formatter={(value, key) => [value, STATUS[key]?.label || key]} />
              </PieChart>
            </ResponsiveContainer>
            <strong className="rp-donut-label">{pct(report.coveragePercentage)}</strong>
          </div>
          <div className="rp-legend">
            {Object.entries(STATUS).map(([key, s]) => (
              <span key={key}>
                <i style={{ background: s.color }} /> {s.label}
              </span>
            ))}
          </div>
        </Card>

        <Card title="Biggest gaps" description="Job skills with no matching topic. See Recommendations for what to do.">
          {gaps.length === 0 ? (
            <p className="ui-muted">No gaps. Every skill is at least partly covered.</p>
          ) : (
            <div className="ui-chip-list">
              {gaps.map((s) => (
                <Badge key={s.skill} tone="danger">{s.skill}</Badge>
              ))}
            </div>
          )}
          {strongest.length > 0 && (
            <>
              <h4 className="rp-subhead">Strongest matches</h4>
              <ul className="ui-list">
                {strongest.map((s) => (
                  <li key={s.skill}>
                    <span>
                      <strong>{s.skill}</strong> <span className="ui-muted">· {s.topic}</span>
                    </span>
                    <Badge tone="success">{pct((s.similarity || 0) * 100)}</Badge>
                  </li>
                ))}
              </ul>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ Skill by skill

export function ReportSkills({ syllabusId, jobId }) {
  const state = useSaved(reportPath, syllabusId, jobId);
  const [filter, setFilter] = useState("ALL");
  const [search, setSearch] = useState("");

  const skills = (state.data?.skillDetails || []).map((s, i) => ({ ...s, id: `${s.skill}-${i}`, statusKey: statusOf(s.status) }));
  const counts = skills.reduce((acc, s) => ({ ...acc, [s.statusKey]: (acc[s.statusKey] || 0) + 1 }), {});
  const query = search.trim().toLowerCase();
  const rows = skills.filter(
    (s) =>
      (filter === "ALL" || s.statusKey === filter) &&
      (!query || `${s.skill} ${s.topic || ""}`.toLowerCase().includes(query))
  );

  return (
    <Guard state={state}>
      <Card title="Skill by skill" description="Each job skill, the closest syllabus topic, and how it was matched.">
        <DataTable
          toolbar={
            <>
              <div className="rp-filters" role="group" aria-label="Filter by status">
                {["ALL", "MATCHED", "PARTIAL", "GAP"].map((key) => (
                  <button key={key} type="button" aria-pressed={filter === key} onClick={() => setFilter(key)}>
                    {key === "ALL" ? `All ${skills.length}` : `${STATUS[key].label} ${counts[key] || 0}`}
                  </button>
                ))}
              </div>
              <SearchInput value={search} onChange={setSearch} placeholder="Search skill or topic" />
            </>
          }
          columns={[
            { key: "skill", label: "Job skill", render: (s) => <strong>{s.skill}</strong> },
            { key: "status", label: "Status", render: (s) => <Badge tone={STATUS[s.statusKey].tone}>{STATUS[s.statusKey].label}</Badge> },
            {
              key: "topic",
              label: "Closest topic",
              render: (s) =>
                s.topic && s.statusKey !== "GAP" ? (
                  <span className="ui-cell-title">
                    <span>{s.topic}</span>
                    <small>{s.matchedBy === "KEYWORD" ? "Skill named in this topic" : "Closest by meaning"}</small>
                  </span>
                ) : (
                  <span className="ui-muted">No matching topic</span>
                ),
            },
            {
              key: "similarity",
              label: "Similarity",
              render: (s) => (
                <span className="rp-meter" title={pct((s.similarity || 0) * 100)}>
                  <i style={{ width: pct(Math.max(0, Math.min(1, s.similarity || 0)) * 100), background: STATUS[s.statusKey].color }} />
                </span>
              ),
            },
          ]}
          rows={rows}
          empty={<EmptyState icon={ListChecks} title="No skills match this filter" />}
        />
      </Card>
    </Guard>
  );
}

// ------------------------------------------------------------------ Recommendations

const OFFICIAL_DOCS = {
  java: "https://dev.java/learn/",
  javascript: "https://developer.mozilla.org/en-US/docs/Web/JavaScript",
  typescript: "https://www.typescriptlang.org/docs/",
  python: "https://docs.python.org/3/tutorial/",
  react: "https://react.dev/learn",
  "spring boot": "https://spring.io/guides",
  postgresql: "https://www.postgresql.org/docs/current/tutorial.html",
  docker: "https://docs.docker.com/get-started/",
  kubernetes: "https://kubernetes.io/docs/tutorials/",
  git: "https://git-scm.com/docs/gittutorial",
};

function resources(skill) {
  const key = String(skill || "").trim().toLowerCase();
  return [
    { label: "freeCodeCamp", url: `https://www.freecodecamp.org/news/search/?query=${encodeURIComponent(skill || "")}` },
    ...(OFFICIAL_DOCS[key] ? [{ label: "Official docs", url: OFFICIAL_DOCS[key] }] : []),
  ];
}

const PRIORITY_TONE = { HIGH: "danger", MEDIUM: "warning", LOW: "neutral" };

export function ReportRecommendations({ syllabusId, jobId, role }) {
  const state = useSaved(recommendationsPath, syllabusId, jobId);
  const showCurriculum = can(role, "curriculumRecommendations");
  const showRoadmap = can(role, "learningRoadmap");
  const items = Array.isArray(state.data) ? state.data : [];
  const high = items.filter((r) => String(r.priority).toUpperCase() === "HIGH").length;

  return (
    <Guard state={state}>
      <div className="ui-stack">
        <Alert tone="info">
          Generated by AI from the gaps in this report — review before acting. They are saved; "Re-run analysis" makes new ones.
        </Alert>
        {items.length === 0 ? (
          <EmptyState icon={Lightbulb} title="No recommendations" description="No gaps to act on, or the AI service is unavailable." />
        ) : (
          <>
            <p className="ui-muted">
              {items.length} recommendations · {high} high priority
            </p>
            {items.map((item, index) => {
              const priority = String(item.priority || "MEDIUM").toUpperCase();
              const status = statusOf(item.status);
              return (
                <Card
                  key={`${item.skill}-${index}`}
                  title={item.skill || "Skill"}
                  actions={
                    <>
                      <Badge tone={STATUS[status].tone}>{STATUS[status].label}</Badge>
                      <Badge tone={PRIORITY_TONE[priority] || "neutral"}>{priority} priority</Badge>
                    </>
                  }
                >
                  {item.explanation && <p className="rp-why">{item.explanation}</p>}
                  <div className="ui-grid ui-grid-2">
                    {showCurriculum && item.curriculumRecommendation && (
                      <div className="rp-block">
                        <h4>Curriculum change</h4>
                        <p>{item.curriculumRecommendation}</p>
                      </div>
                    )}
                    {item.studentRecommendation && (
                      <div className="rp-block">
                        <h4>For students</h4>
                        <p>{item.studentRecommendation}</p>
                      </div>
                    )}
                  </div>
                  {showRoadmap && Array.isArray(item.learningRoadmap) && item.learningRoadmap.length > 0 && (
                    <ol className="rp-steps">
                      {item.learningRoadmap.map((step, i) => (
                        <li key={`${step.title}-${i}`}>
                          <strong>{step.title || `Step ${i + 1}`}</strong>
                          {step.action && <span> — {step.action}</span>}
                        </li>
                      ))}
                    </ol>
                  )}
                  {showRoadmap && (
                    <div className="rp-links">
                      {resources(item.skill).map((r) => (
                        <a key={r.label} href={r.url} target="_blank" rel="noopener noreferrer">
                          {r.label} <ExternalLink size={12} aria-hidden="true" />
                        </a>
                      ))}
                    </div>
                  )}
                </Card>
              );
            })}
          </>
        )}
      </div>
    </Guard>
  );
}
