import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, CircleDashed, ExternalLink, Lightbulb, ListChecks } from "lucide-react";
import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
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

// Rules the matching service applies (SemanticMatchingService; thresholds from application.properties).
const MATCHED_AT = 0.5;
const PARTIAL_AT = 0.25;

const DEFINITIONS = [
  ["Coverage", "How much of the job the syllabus teaches: (covered × 100 + partly covered × 50) ÷ total job skills."],
  ["Covered", `The closest syllabus topic has a similarity of ${MATCHED_AT.toFixed(2)} or more, or the skill is named in a topic title.`],
  ["Partly covered", `The closest topic has a similarity from ${PARTIAL_AT.toFixed(2)} to ${MATCHED_AT.toFixed(2)}, or the skill appears only in a topic's description.`],
  ["Gap", `No topic is close enough (similarity below ${PARTIAL_AT.toFixed(2)}) and the skill is not mentioned in any topic.`],
  ["Similarity", "Cosine similarity between the skill and the topic, from their sentence embeddings (0 = unrelated, 1 = same meaning)."],
  ["Matched by", "Skill named: found as text in a topic. Closest by meaning: found by embedding similarity."],
];

const band = (coverage) =>
  coverage >= 70 ? { label: "Strong alignment", tone: "success" } : coverage >= 40 ? { label: "Moderate alignment", tone: "warning" } : { label: "Low alignment", tone: "danger" };
const short = (text, n = 24) => (String(text).length > n ? `${String(text).slice(0, n - 1)}…` : String(text));
const two = (n) => (Number(n) || 0).toFixed(2);

function ChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const s = payload[0].payload;
  return (
    <div className="rp-tooltip">
      <strong>{s.skill}</strong>
      <span>{STATUS[s.statusKey].label} · similarity {two(s.similarity)}</span>
      {s.topic && s.statusKey !== "GAP" && <span>Topic: {s.topic}</span>}
    </div>
  );
}

function OverviewBody({ report }) {
  const skills = (report.skillDetails || []).map((s) => ({ ...s, statusKey: statusOf(s.status), similarity: Number(s.similarity) || 0 }));
  const total = report.totalSkills || skills.length;
  const pie = [
    { key: "MATCHED", value: report.matchedSkills },
    { key: "PARTIAL", value: report.partialSkills },
    { key: "GAP", value: report.gapSkills },
  ].filter((slice) => slice.value > 0);
  const gaps = skills.filter((s) => s.statusKey === "GAP");
  const strongest = skills.filter((s) => s.statusKey === "MATCHED").sort((a, b) => b.similarity - a.similarity).slice(0, 6);
  const bySimilarity = [...skills].sort((a, b) => b.similarity - a.similarity);
  const share = (n) => (total ? Math.round((n / total) * 100) : 0);
  const level = band(report.coveragePercentage);

  // similarity histogram, 10 bins of 0.1
  const bins = Array.from({ length: 10 }, (_, i) => ({ range: `${(i / 10).toFixed(1)}–${((i + 1) / 10).toFixed(1)}`, from: i / 10, count: 0 }));
  skills.forEach((s) => { bins[Math.min(9, Math.floor(s.similarity * 10))].count += 1; });
  const binColor = (from) => (from >= MATCHED_AT ? STATUS.MATCHED.color : from >= PARTIAL_AT - 0.001 ? STATUS.PARTIAL.color : STATUS.GAP.color);

  // how each skill was matched
  const methods = [
    { name: "Skill named in a topic", value: skills.filter((s) => s.matchedBy === "KEYWORD" && s.statusKey !== "GAP").length, color: "var(--cp-accent)" },
    { name: "Closest by meaning", value: skills.filter((s) => s.matchedBy !== "KEYWORD" && s.statusKey !== "GAP").length, color: "var(--cp-info)" },
    { name: "No match", value: gaps.length, color: STATUS.GAP.color },
  ];

  return (
    <div className="ui-stack">
      <Card>
        <div className="rp-hero">
          <div className="rp-donut rp-donut-lg">
            <ResponsiveContainer width="100%" height={200}>
              <PieChart>
                <Pie data={pie} dataKey="value" nameKey="key" innerRadius={62} outerRadius={88} paddingAngle={2} startAngle={90} endAngle={-270}>
                  {pie.map((slice) => (
                    <Cell key={slice.key} fill={STATUS[slice.key].color} />
                  ))}
                </Pie>
                <Tooltip formatter={(value, key) => [`${value} skills`, STATUS[key]?.label || key]} />
              </PieChart>
            </ResponsiveContainer>
            <div className="rp-donut-label">
              <strong>{pct(report.coveragePercentage)}</strong>
              <small>coverage</small>
            </div>
          </div>
          <div className="rp-hero-text">
            <Badge tone={level.tone}>{level.label}</Badge>
            <p>
              Of the <strong>{total}</strong> skills this job asks for, the syllabus fully covers <strong>{report.matchedSkills}</strong> (
              {share(report.matchedSkills)}%), partly covers <strong>{report.partialSkills}</strong> ({share(report.partialSkills)}%) and misses{" "}
              <strong>{report.gapSkills}</strong> ({share(report.gapSkills)}%).
            </p>
            <p className="ui-muted rp-formula">
              Coverage = (covered × 100 + partly covered × 50) ÷ total skills = ({report.matchedSkills} × 100 + {report.partialSkills} × 50) ÷ {total} ={" "}
              {pct(report.coveragePercentage)}
            </p>
            <div className="rp-stackbar" role="img" aria-label="Share of covered, partly covered and missing skills">
              {["MATCHED", "PARTIAL", "GAP"].map((key) => {
                const n = key === "MATCHED" ? report.matchedSkills : key === "PARTIAL" ? report.partialSkills : report.gapSkills;
                return n > 0 ? <i key={key} style={{ width: `${share(n)}%`, background: STATUS[key].color }} title={`${STATUS[key].label}: ${n}`} /> : null;
              })}
            </div>
            <div className="rp-legend rp-legend-left">
              {Object.entries(STATUS).map(([key, s]) => (
                <span key={key}>
                  <i style={{ background: s.color }} /> {s.label}
                </span>
              ))}
            </div>
          </div>
        </div>
      </Card>

      <div className="ui-grid ui-grid-4">
        <StatCard icon={ListChecks} label="Job skills" value={total} hint="required by the selected job" />
        <StatCard icon={CheckCircle2} label="Covered" value={report.matchedSkills} hint={`similarity ≥ ${two(MATCHED_AT)} or named in a topic`} tone="success" />
        <StatCard icon={CircleDashed} label="Partly covered" value={report.partialSkills} hint={`similarity ${two(PARTIAL_AT)}–${two(MATCHED_AT)} or in a description`} tone="warning" />
        <StatCard icon={AlertTriangle} label="Gaps" value={report.gapSkills} hint={`similarity < ${two(PARTIAL_AT)}, not mentioned`} tone="danger" />
      </div>

      <Card title="Similarity per skill" description={`Best match score of each job skill against the syllabus topics. Lines mark the Partly covered (${two(PARTIAL_AT)}) and Covered (${two(MATCHED_AT)}) thresholds; a skill named in a topic can be covered below the line.`}>
        {bySimilarity.length === 0 ? (
          <p className="ui-muted">This job has no skills to compare.</p>
        ) : (
          <ResponsiveContainer width="100%" height={Math.max(160, bySimilarity.length * 26 + 40)}>
            <BarChart data={bySimilarity} layout="vertical" margin={{ top: 8, right: 24, left: 8, bottom: 8 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="var(--cp-border)" />
              <XAxis type="number" domain={[0, 1]} ticks={[0, 0.25, 0.5, 0.75, 1]} tick={{ fontSize: 12 }} />
              <YAxis type="category" dataKey="skill" width={150} tick={{ fontSize: 12 }} tickFormatter={(v) => short(v)} interval={0} />
              <Tooltip content={<ChartTooltip />} cursor={{ fill: "var(--cp-accent-tint)" }} />
              <ReferenceLine x={PARTIAL_AT} stroke={STATUS.PARTIAL.color} strokeDasharray="4 4" label={{ value: "Partly", position: "top", fontSize: 11 }} />
              <ReferenceLine x={MATCHED_AT} stroke={STATUS.MATCHED.color} strokeDasharray="4 4" label={{ value: "Covered", position: "top", fontSize: 11 }} />
              <Bar dataKey="similarity" radius={[0, 4, 4, 0]} barSize={16}>
                {bySimilarity.map((s) => (
                  <Cell key={s.skill} fill={STATUS[s.statusKey].color} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        )}
      </Card>

      <div className="ui-grid ui-grid-2">
        <Card title="Similarity distribution" description="How many job skills fall in each similarity range (bars coloured by the band they fall in).">
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={bins} margin={{ top: 8, right: 8, left: -16, bottom: 8 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--cp-border)" />
              <XAxis dataKey="range" tick={{ fontSize: 10 }} interval={0} angle={-30} textAnchor="end" height={44} />
              <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
              <Tooltip formatter={(v) => [`${v} skills`, "Skills"]} labelFormatter={(l) => `Similarity ${l}`} />
              <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                {bins.map((b) => (
                  <Cell key={b.range} fill={binColor(b.from)} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </Card>

        <Card title="How each skill was matched" description="Skill named: the skill text appears in a topic. Closest by meaning: found by embedding similarity.">
          <ResponsiveContainer width="100%" height={220}>
            <PieChart>
              <Pie data={methods.filter((m) => m.value > 0)} dataKey="value" nameKey="name" outerRadius={80} label={({ value }) => value}>
                {methods.filter((m) => m.value > 0).map((m) => (
                  <Cell key={m.name} fill={m.color} />
                ))}
              </Pie>
              <Tooltip formatter={(v, n) => [`${v} skills`, n]} />
              <Legend verticalAlign="bottom" height={28} iconType="circle" wrapperStyle={{ fontSize: 12 }} />
            </PieChart>
          </ResponsiveContainer>
        </Card>
      </div>

      <div className="ui-grid ui-grid-2">
        <Card title="Biggest gaps" description="Job skills with no matching topic. The Recommendations tab suggests what to add.">
          {gaps.length === 0 ? (
            <p className="ui-muted">No gaps. Every skill is at least partly covered.</p>
          ) : (
            <div className="ui-chip-list">
              {gaps.map((s) => (
                <Badge key={s.skill} tone="danger">{s.skill}</Badge>
              ))}
            </div>
          )}
        </Card>
        <Card title="Strongest matches" description="Covered skills with the highest similarity, and the topic that covers them.">
          {strongest.length === 0 ? (
            <p className="ui-muted">No skill is fully covered yet.</p>
          ) : (
            <ul className="ui-list">
              {strongest.map((s) => (
                <li key={s.skill}>
                  <span className="ui-cell-title">
                    <strong>{s.skill}</strong>
                    <small>{s.topic}</small>
                  </span>
                  <Badge tone="success">{two(s.similarity)}</Badge>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <Card title="How to read this report">
        <dl className="rp-defs">
          {DEFINITIONS.map(([term, text]) => (
            <div key={term}>
              <dt>{term}</dt>
              <dd>{text}</dd>
            </div>
          ))}
        </dl>
      </Card>
    </div>
  );
}

export function ReportOverview({ syllabusId, jobId }) {
  const state = useSaved(reportPath, syllabusId, jobId);
  return (
    <Guard state={state}>
      {state.data && <OverviewBody report={state.data} />}
    </Guard>
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
      <Card title="Skill by skill" description={`Each job skill, its closest syllabus topic, the similarity score and how it was matched. Covered: similarity ≥ ${two(MATCHED_AT)} or skill named in a topic. Partly covered: ${two(PARTIAL_AT)}–${two(MATCHED_AT)} or in a description. Gap: below ${two(PARTIAL_AT)}.`}>
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
                  <span>{s.topic}</span>
                ) : (
                  <span className="ui-muted">No matching topic</span>
                ),
            },
            {
              key: "matchedBy",
              label: "Matched by",
              render: (s) =>
                s.statusKey === "GAP" ? <span className="ui-muted">No match</span> : s.matchedBy === "KEYWORD" ? "Skill named in topic" : "Closest by meaning",
            },
            {
              key: "similarity",
              label: "Similarity (0–1)",
              render: (s) => (
                <span className="rp-meter-row">
                  <span className="rp-meter" title={two(s.similarity)}>
                    <i style={{ width: pct(Math.max(0, Math.min(1, s.similarity || 0)) * 100), background: STATUS[s.statusKey].color }} />
                  </span>
                  <span className="rp-meter-value">{two(s.similarity)}</span>
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

function RecommendationSummary({ items, high }) {
  const byPriority = ["HIGH", "MEDIUM", "LOW"].map((key) => ({
    name: key.charAt(0) + key.slice(1).toLowerCase(),
    count: items.filter((r) => String(r.priority || "MEDIUM").toUpperCase() === key).length,
    color: key === "HIGH" ? STATUS.GAP.color : key === "MEDIUM" ? STATUS.PARTIAL.color : "var(--cp-text-muted)",
  }));
  const byStatus = ["GAP", "PARTIAL"].map((key) => ({
    name: STATUS[key].label, count: items.filter((r) => statusOf(r.status) === key).length, color: STATUS[key].color,
  }));
  const chart = (data, label) => (
    <ResponsiveContainer width="100%" height={150}>
      <BarChart data={data} layout="vertical" margin={{ top: 4, right: 24, left: 8, bottom: 4 }}>
        <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12 }} />
        <YAxis type="category" dataKey="name" width={110} tick={{ fontSize: 12 }} />
        <Tooltip formatter={(v) => [`${v} recommendations`, label]} />
        <Bar dataKey="count" barSize={18} radius={[0, 4, 4, 0]}>
          {data.map((d) => (
            <Cell key={d.name} fill={d.color} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
  return (
    <div className="ui-grid ui-grid-2">
      <Card title={`${items.length} recommendations · ${high} high priority`} description="Priority set by the AI from how important the skill is for the job.">
        {chart(byPriority, "Priority")}
      </Card>
      <Card title="Skill status" description="Gap: not in the syllabus. Partly covered: only related topics exist.">
        {chart(byStatus, "Status")}
      </Card>
    </div>
  );
}

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
            <RecommendationSummary items={items} high={high} />
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
