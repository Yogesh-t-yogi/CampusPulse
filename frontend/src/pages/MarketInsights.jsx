import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Briefcase, MapPin, TrendingUp } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Alert, Badge, Card, Field, Page, PageHeader, SearchInput, Skeleton, StatCard } from "../ui";
import { SKILL_STATUS } from "../studentStatus";

// Market demand from the job catalog, filtered by location / keyword, plus gaps for the user:
// students against their skills + course; staff against the selected syllabus.
function MarketInsights({ user, selectedSyllabusId, syllabi }) {
  const isStudent = user?.role === "STUDENT";
  const [location, setLocation] = useState("");
  const [keyword, setKeyword] = useState("");
  const [query, setQuery] = useState({ location: "", q: "" });
  const [syllabusId, setSyllabusId] = useState(selectedSyllabusId || "");
  const [market, setMarket] = useState(null);
  const [gaps, setGaps] = useState(null);
  const [error, setError] = useState("");

  // Wait for the user to stop typing before asking the server.
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery({ location, q: keyword }), 400);
    return () => window.clearTimeout(timer);
  }, [location, keyword]);

  useEffect(() => {
    let cancelled = false;
    api
      .get("/api/insights", { params: query })
      .then(({ data }) => !cancelled && setMarket(data))
      .catch((err) => !cancelled && setError(errorMessage(err, "Couldn't load market data.")));
    return () => {
      cancelled = true;
    };
  }, [query]);

  useEffect(() => {
    let cancelled = false;
    api
      .get("/api/insights/gaps", { params: { ...query, syllabusId: isStudent ? undefined : syllabusId || undefined } })
      .then(({ data }) => !cancelled && setGaps(data))
      .catch(() => !cancelled && setGaps(null));
    return () => {
      cancelled = true;
    };
  }, [query, syllabusId, isStudent]);

  const locations = market?.locations || [];
  const counts = (gaps?.skills || []).reduce((acc, row) => ({ ...acc, [row.status]: (acc[row.status] || 0) + 1 }), {});

  return (
    <Page>
      <PageHeader
        eyebrow="Tools"
        title="Market insights"
        description="What employers in the job catalog ask for, filtered by place and role — and how that compares with you."
      />

      <Card>
        <div className="ui-form-row">
          <Field label="Location">
            <select value={location} onChange={(e) => setLocation(e.target.value)}>
              <option value="">All locations</option>
              {locations.map((item) => (
                <option key={item.location} value={item.location}>
                  {item.location} ({item.jobCount})
                </option>
              ))}
            </select>
          </Field>
          <Field label="Role or keyword">
            <SearchInput value={keyword} onChange={setKeyword} placeholder="e.g. data, developer, cloud" />
          </Field>
        </div>
      </Card>

      {error && <Alert tone="danger">{error}</Alert>}
      {!market && !error && <Skeleton lines={6} />}

      {market && (
        <>
          <div className="ui-grid ui-grid-4">
            <StatCard icon={Briefcase} label="Jobs" value={market.totalJobs} hint="matching the filters" />
            <StatCard icon={TrendingUp} label="Top skill" value={market.skills[0]?.skill || "—"} hint={market.skills[0] ? `${market.skills[0].jobCount} jobs` : ""} tone="info" />
            <StatCard icon={MapPin} label="Locations" value={locations.length} hint="in the catalog" tone="warning" />
            <StatCard
              icon={TrendingUp}
              label={isStudent ? "You have / taught" : "Taught in syllabus"}
              value={gaps ? `${(counts.HAVE || 0) + (counts.TAUGHT || 0)}/${gaps.skills.length}` : "—"}
              hint="of the top skills"
              tone="success"
            />
          </div>

          <div className="ui-grid ui-grid-main">
            <Card title="Most requested skills" description="Number of jobs asking for each skill.">
              {market.skills.length === 0 ? (
                <p className="ui-muted">No jobs match these filters.</p>
              ) : (
                <div style={{ height: Math.max(240, market.skills.length * 26) }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={market.skills} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 4 }}>
                      <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                      <XAxis type="number" allowDecimals={false} />
                      <YAxis type="category" dataKey="skill" width={120} tick={{ fontSize: 12 }} />
                      <Tooltip formatter={(value) => [`${value} jobs`, "Demand"]} />
                      <Bar dataKey="jobCount" fill="#6366f1" radius={[0, 4, 4, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Card>

            <Card title="Most common roles">
              <ul className="ui-list">
                {market.roles.map((role) => (
                  <li key={role.title}>
                    <span>{role.title}</span>
                    <Badge tone="neutral">{role.jobCount}</Badge>
                  </li>
                ))}
              </ul>
            </Card>
          </div>

          <Card
            title={isStudent ? "Your gaps vs the market" : "Curriculum vs the market"}
            description={
              isStudent
                ? `Top skills checked against your skills and your course${gaps?.syllabus ? ` (${gaps.syllabus.name})` : ""}.`
                : "Top skills checked against a syllabus: is it taught?"
            }
            actions={
              !isStudent && (
                <select className="ui-select" style={{ width: "auto" }} value={syllabusId} onChange={(e) => setSyllabusId(e.target.value)} aria-label="Syllabus">
                  <option value="">Choose a syllabus</option>
                  {syllabi.map((item) => (
                    <option key={item.id} value={String(item.id)}>
                      {item.name}
                    </option>
                  ))}
                </select>
              )
            }
          >
            {!gaps ? (
              <Skeleton lines={4} />
            ) : (
              <div className="ui-table-scroll">
                <table className="ui-table">
                  <thead>
                    <tr>
                      <th>Skill</th>
                      <th>Jobs</th>
                      <th>{isStudent ? "You" : "Syllabus"}</th>
                      <th>Where</th>
                    </tr>
                  </thead>
                  <tbody>
                    {gaps.skills.map((row) => (
                      <tr key={row.skill}>
                        <td>
                          <strong>{row.skill}</strong>
                        </td>
                        <td>{row.jobCount}</td>
                        <td>
                          <Badge tone={SKILL_STATUS[row.status]?.tone}>{SKILL_STATUS[row.status]?.label || row.status}</Badge>
                        </td>
                        <td className="ui-muted">{row.topic || "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      )}
    </Page>
  );
}

export default MarketInsights;
