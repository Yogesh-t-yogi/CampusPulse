

import { useEffect, useState } from "react";

import api from "../apiClient";

import {

  PieChart,

  Pie,

  Cell,

  Tooltip,

  ResponsiveContainer,

  BarChart,

  Bar,

  XAxis,

  YAxis,

  CartesianGrid,

  Legend,

} from "recharts";

function Dashboard({ token, syllabusId, jobId }) {

  const [report, setReport] = useState(null);

  const [recommendations, setRecommendations] = useState([]);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState("");
  const [recommendationsLoading, setRecommendationsLoading] = useState(false);
  const [recommendationsError, setRecommendationsError] = useState("");

  useEffect(() => {

    let cancelled = false;

    async function fetchDashboardData() {

      if (!syllabusId) {

        setReport(null);

        setRecommendations([]);

        setError("Select a syllabus to view your analytics.");

        setLoading(false);

        return;
      }

      if (!jobId) {
        setReport(null);
        setRecommendations([]);
        setError("Choose a job profile to view your analysis.");
        setLoading(false);
        return;
      }

      if (!token) {

        setReport(null);

        setRecommendations([]);

        setError("Your session has expired. Please log in again.");

        setLoading(false);

        return;

      }

      setLoading(true);

      setError("");
      setRecommendationsError("");
      setRecommendationsLoading(false);

      const config = {

        headers: { Authorization: `Bearer ${token}` },

      };

      try {

        const reportResponse = await api.get(
          `/api/matching/semantic/report/jobs/${jobId}/syllabus/${syllabusId}`,
          config
        );

        if (cancelled) return;

        setReport(reportResponse.data);
        setLoading(false);

        setRecommendationsLoading(true);
        try {
          const recommendationResponse = await api.get(
            `/api/recommendations/jobs/${jobId}/syllabus/${syllabusId}`,
            config
          );
          if (cancelled) return;
          setRecommendations(
            Array.isArray(recommendationResponse.data)
              ? recommendationResponse.data
              : []
          );
        } catch (recommendationError) {
          if (cancelled) return;
          console.error("Dashboard recommendations error:", {
            status: recommendationError.response?.status,
            url: recommendationError.config?.url,
            message: recommendationError.message,
          });
          setRecommendationsError(
            recommendationError.response?.status === 404
              ? "No recommendations are available for this job and syllabus."
              : "Recommendations are temporarily unavailable."
          );
        } finally {
          if (!cancelled) setRecommendationsLoading(false);
        }

      } catch (err) {

        if (cancelled) return;

        console.error("Dashboard data error:", {

          status: err.response?.status,

          url: err.config?.url,

          message: err.message,

        });

        setReport(null);

        setRecommendations([]);

        if (err.response?.status === 401) {

          setError("Your session has expired. Please log in again.");

        } else if (err.response?.status === 403) {

          setError(

            "Access denied. Check your account permissions and selected syllabus."

          );

        } else if (err.response?.status === 404) {

          setError(

            "The selected syllabus or job was not found."

          );

        } else if (!err.response) {

          setError("Cannot connect to the CampusPulse backend. Please try again later.");

        } else {

          setError("Unable to load analytics. Please try again.");

        }

      } finally {

        if (!cancelled) setLoading(false);

      }

    }

    fetchDashboardData();

    return () => {

      cancelled = true;

    };

  }, [syllabusId, jobId, token]);

  if (loading) {

    return (

      <main className="page-content cp-dashboard">

        <div className="cp-loading">

          <div className="cp-spinner" />

          <h2>Preparing your insights</h2>

          <p>Analyzing curriculum alignment and industry skills...</p>

        </div>

      </main>

    );

  }

  if (error || !report) {

    return (

      <main className="page-content cp-dashboard">

        <div className="cp-state-card">

          <div className="cp-state-icon">!</div>

          <h1>Dashboard</h1>

          <p>{error || "Dashboard data is unavailable."}</p>

          <button

            className="cp-primary-button"

            onClick={() => window.location.reload()}

          >

            Try again

          </button>

        </div>

      </main>

    );

  }

  const totalSkills = report.totalSkills ?? 0;

  const matchedSkills = report.matchedSkills ?? 0;

  const partialSkills = report.partialSkills ?? 0;

  const gapSkills = report.gapSkills ?? 0;

  const coverage = report.coveragePercentage ?? 0;

  const skillDetails = Array.isArray(report.skillDetails)

    ? report.skillDetails

    : [];

  const pieData = [

    { name: "Matched", value: matchedSkills },

    { name: "Partial match", value: partialSkills },

    { name: "Skill gap", value: gapSkills },

  ];

  const pieColors = ["#16a34a", "#d97706", "#dc2626"];

  const barData = skillDetails.map((item) => ({

    skill: item.skill,

    similarity: Number(((item.similarity ?? 0) * 100).toFixed(1)),

    status: item.status,

  }));

  const getPriorityClass = (priority) =>

    String(priority || "MEDIUM").toLowerCase();

  return (

    <main className="page-content cp-dashboard">

      <section className="cp-hero">

        <div>

          <span className="cp-eyebrow">

            <span className="cp-live-dot" />

            AI-POWERED CURRICULUM INTELLIGENCE

          </span>

          <h1>CampusPulse Dashboard</h1>

          <p>

            Discover how well your curriculum aligns with

            real-world industry requirements.

          </p>

        </div>

        <div className="cp-hero-badge">

          <span className="cp-hero-badge-icon">✦</span>

          <div>

            <strong>AI Insights</strong>

            <span>Semantic skill analysis</span>

          </div>

        </div>

      </section>

      <section className="cp-stat-grid" aria-label="Skill analysis summary">

        <article className="cp-stat-card cp-stat-blue">

          <div className="cp-stat-top">

            <span>Total skills</span>

            <span className="cp-stat-icon">◎</span>

          </div>

          <strong className="cp-stat-number">{totalSkills}</strong>

          <p>Industry requirements analyzed</p>

          <div className="cp-stat-foot">Across the selected job profile</div>

        </article>

        <article className="cp-stat-card cp-stat-green">

          <div className="cp-stat-top">

            <span>Matched skills</span>

            <span className="cp-stat-icon">✓</span>

          </div>

          <strong className="cp-stat-number">{matchedSkills}</strong>

          <p>Skills covered by curriculum</p>

          <div className="cp-stat-foot">Strong curriculum alignment</div>

        </article>

        <article className="cp-stat-card cp-stat-amber">

          <div className="cp-stat-top">

            <span>Partial matches</span>

            <span className="cp-stat-icon">◐</span>

          </div>

          <strong className="cp-stat-number">{partialSkills}</strong>

          <p>Skills needing deeper coverage</p>

          <div className="cp-stat-foot">Opportunities to improve</div>

        </article>

        <article className="cp-stat-card cp-stat-red">

          <div className="cp-stat-top">

            <span>Skill gaps</span>

            <span className="cp-stat-icon">!</span>

          </div>

          <strong className="cp-stat-number">{gapSkills}</strong>

          <p>Skills requiring attention</p>

          <div className="cp-stat-foot">Prioritize learning resources</div>

        </article>

      </section>

      <section className="cp-coverage-panel">

        <div className="cp-coverage-copy">

          <span className="cp-section-kicker">CURRICULUM ALIGNMENT</span>

          <h2>Industry coverage</h2>

          <p>

            Your curriculum's overall semantic alignment with the

            selected industry's skill requirements.

          </p>

        </div>

        <div

          className="cp-coverage-ring"

          style={{

            "--coverage-angle": `${Math.max(0, Math.min(100, coverage)) * 3.6}deg`,

          }}

          role="img"

          aria-label={`Industry coverage ${coverage}%`}

        >

          <div className="cp-coverage-ring-inner">

            <strong>{coverage}%</strong>

            <span>Coverage</span>

          </div>

        </div>

      </section>

      <section className="cp-chart-grid">

        <article className="cp-panel">

          <div className="cp-panel-heading">

            <div>

              <span className="cp-section-kicker">OVERVIEW</span>

              <h2>Skill distribution</h2>

              <p>How industry skills are classified</p>

            </div>

          </div>

          <div className="cp-chart">

            {totalSkills === 0 ? (

              <p className="cp-empty">No skill data is available.</p>

            ) : (

              <ResponsiveContainer width="100%" height={290}>

                <PieChart>

                  <Pie

                    data={pieData}

                    dataKey="value"

                    nameKey="name"

                    cx="50%"

                    cy="46%"

                    innerRadius={58}

                    outerRadius={91}

                    paddingAngle={4}

                    stroke="none"

                  >

                    {pieData.map((entry, index) => (

                      <Cell

                        key={entry.name}

                        fill={pieColors[index]}

                      />

                    ))}

                  </Pie>

                  <Tooltip

                    formatter={(value, name) => [value, name]}

                    contentStyle={{

                      borderRadius: 12,

                      border: "1px solid #e2e8f0",

                      boxShadow: "0 8px 24px rgba(15, 23, 42, 0.08)",

                    }}

                  />

                  <Legend

                    verticalAlign="bottom"

                    iconType="circle"

                    wrapperStyle={{ fontSize: 12, color: "#475569" }}

                  />

                </PieChart>

              </ResponsiveContainer>

            )}

          </div>

        </article>

        <article className="cp-panel">

          <div className="cp-panel-heading">

            <div>

              <span className="cp-section-kicker">SEMANTIC ANALYSIS</span>

              <h2>Skill similarity</h2>

              <p>How closely each skill matches the curriculum</p>

            </div>

          </div>

          <div className="cp-chart">

            {barData.length === 0 ? (

              <p className="cp-empty">No similarity data is available.</p>

            ) : (

              <ResponsiveContainer width="100%" height={290}>

                <BarChart

                  data={barData}

                  margin={{ top: 12, right: 10, left: -12, bottom: 35 }}

                >

                  <CartesianGrid

                    stroke="#e9edf5"

                    strokeDasharray="4 4"

                    vertical={false}

                  />

                  <XAxis

                    dataKey="skill"

                    tick={{ fill: "#64748b", fontSize: 11 }}

                    angle={-20}

                    textAnchor="end"

                    interval={0}

                    height={58}

                    axisLine={{ stroke: "#dbe3ef" }}

                    tickLine={false}

                  />

                  <YAxis

                    domain={[0, 100]}

                    ticks={[0, 25, 50, 75, 100]}

                    tick={{ fill: "#64748b", fontSize: 11 }}

                    axisLine={false}

                    tickLine={false}

                    tickFormatter={(value) => `${value}%`}

                  />

                  <Tooltip

                    formatter={(value) => [`${value}%`, "Similarity"]}

                    contentStyle={{

                      borderRadius: 12,

                      border: "1px solid #e2e8f0",

                      boxShadow: "0 8px 24px rgba(15, 23, 42, 0.08)",

                    }}

                  />

                  <Bar

                    dataKey="similarity"

                    name="Similarity"

                    fill="#6366f1"

                    radius={[6, 6, 0, 0]}

                    maxBarSize={42}

                  />

                </BarChart>

              </ResponsiveContainer>

            )}

          </div>

          <p className="cp-chart-note">

            Similarity scores are semantic model estimates, not

            guarantees of complete skill proficiency.

          </p>

        </article>

      </section>

      <section className="cp-panel cp-recommendations-panel">

        <div className="cp-panel-heading cp-recommendations-heading">

          <div>

            <span className="cp-section-kicker">NEXT STEPS</span>

            <h2>AI recommendations</h2>

            <p>Suggested actions based on the identified skill gaps</p>

          </div>

          <span className="cp-count-badge">

            {recommendations.length} insights

          </span>

        </div>

        {recommendationsLoading ? (
          <div className="cp-empty">Generating AI recommendations...</div>
        ) : recommendationsError ? (
          <div className="cp-empty" role="alert">
            {recommendationsError}
          </div>
        ) : recommendations.length === 0 ? (

          <div className="cp-empty">

            No recommendations are available for this job and syllabus.

          </div>

        ) : (

          <div className="cp-recommendation-grid">

            {recommendations.slice(0, 3).map((item, index) => (

              <article

                className="cp-recommendation-item"

                key={item.id ?? `${item.skill}-${index}`}

              >

                <div className="cp-recommendation-top">

                  <span className="cp-recommendation-number">

                    {String(index + 1).padStart(2, "0")}

                  </span>

                  <span

                    className={`cp-priority cp-priority-${getPriorityClass(

                      item.priority

                    )}`}

                  >

                    {item.priority || "MEDIUM"} priority

                  </span>

                </div>

                <h3>{item.skill || "Skill recommendation"}</h3>

                <span className="cp-status-label">

                  {item.status || "REVIEW"}

                </span>

                <p>

                  {item.explanation ||

                    "Review this skill's alignment with industry requirements."}

                </p>

              </article>

            ))}

          </div>

        )}

      </section>

      <footer className="cp-dashboard-footer">

        <span>CampusPulse · Curriculum intelligence</span>

        <span>Insights based on the currently selected syllabus</span>

      </footer>

    </main>

  );

}

export default Dashboard;
