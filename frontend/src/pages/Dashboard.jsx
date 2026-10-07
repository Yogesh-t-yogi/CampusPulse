import { useEffect, useState } from "react";
import axios from "axios";

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
  CartesianGrid
} from "recharts";


function Dashboard() {

  const [report, setReport] = useState(null);
  const [recommendations, setRecommendations] = useState([]);
  const [loading, setLoading] = useState(true);


  useEffect(() => {
    fetchDashboardData();
  }, []);


  const fetchDashboardData = async () => {

    try {

      const reportResponse = await axios.get(
        "http://localhost:8081/api/matching/semantic/report/jobs/1/syllabus/4"
      );

      const recommendationResponse = await axios.get(
        "http://localhost:8081/api/recommendations/jobs/1/syllabus/4"
      );

      setReport(reportResponse.data);
      setRecommendations(recommendationResponse.data);

    } catch (error) {

      console.error("Dashboard error:", error);

    } finally {

      setLoading(false);

    }
  };


  /* Loading */

  if (loading) {

    return (
      <div className="page-content">

        <h1>Dashboard</h1>

        <p>
          Loading CampusPulse analytics...
        </p>

      </div>
    );

  }


  /* Error */

  if (!report) {

    return (
      <div className="page-content">

        <h1>Dashboard</h1>

        <p className="error-message">
          Unable to load dashboard data.
        </p>

      </div>
    );

  }


  /* Pie Chart Data */

  const pieData = [

    {
      name: "Matched",
      value: report.matchedSkills
    },

    {
      name: "Partial",
      value: report.partialSkills
    },

    {
      name: "Gap",
      value: report.gapSkills
    }

  ];


  /* Bar Chart Data */

  const barData = report.skillDetails.map((item) => ({

    skill: item.skill,

    similarity: Number(
      (item.similarity * 100).toFixed(2)
    )

  }));


  return (

    <div className="page-content">


      {/* ============================= */}
      {/* HEADER */}
      {/* ============================= */}

      <div className="page-header">

        <h1>
          CampusPulse Dashboard
        </h1>

        <p>
          AI-driven curriculum-to-industry
          gap analysis.
        </p>

      </div>


      {/* ============================= */}
      {/* STATISTICS */}
      {/* ============================= */}

      <div className="stats-grid">


        {/* Total Skills */}

        <div className="stat-card dashboard-stat">

          <div className="stat-header">

            <span>
              Total Skills
            </span>

            <span className="stat-icon">
              ◎
            </span>

          </div>

          <strong>
            {report.totalSkills}
          </strong>

          <p>
            Industry requirements analyzed
          </p>

        </div>


        {/* Matched Skills */}

        <div className="stat-card dashboard-stat">

          <div className="stat-header">

            <span>
              Matched Skills
            </span>

            <span className="stat-icon">
              ✓
            </span>

          </div>

          <strong>
            {report.matchedSkills}
          </strong>

          <p>
            Skills covered by curriculum
          </p>

        </div>


        {/* Skill Gaps */}

        <div className="stat-card dashboard-stat">

          <div className="stat-header">

            <span>
              Skill Gaps
            </span>

            <span className="stat-icon">
              !
            </span>

          </div>

          <strong>
            {report.gapSkills}
          </strong>

          <p>
            Skills requiring attention
          </p>

        </div>


        {/* Industry Coverage */}

        <div className="stat-card dashboard-stat">

          <div className="stat-header">

            <span>
              Industry Coverage
            </span>

            <span className="stat-icon">
              %
            </span>

          </div>

          <strong>
            {report.coveragePercentage}%
          </strong>

          <p>
            Overall curriculum alignment
          </p>

        </div>


      </div>


      {/* ============================= */}
      {/* ANALYTICS */}
      {/* ============================= */}

      <div className="analytics-grid">


        {/* ============================= */}
        {/* PIE CHART */}
        {/* ============================= */}

        <div className="card">

          <h2>
            Skill Distribution
          </h2>

          <div className="chart-container">

            <ResponsiveContainer
              width="100%"
              height={300}
            >

              <PieChart>

                <Pie
                  data={pieData}
                  dataKey="value"
                  nameKey="name"
                  cx="50%"
                  cy="50%"
                  outerRadius={100}
                  label
                >

                  {pieData.map(
                    (entry, index) => (

                      <Cell
                        key={`cell-${index}`}
                      />

                    )
                  )}

                </Pie>

                <Tooltip />

              </PieChart>

            </ResponsiveContainer>

          </div>

        </div>


        {/* ============================= */}
        {/* BAR CHART */}
        {/* ============================= */}

        <div className="card">

          <h2>
            Semantic Skill Similarity
          </h2>

          <div className="chart-container">

            <ResponsiveContainer
              width="100%"
              height={300}
            >

              <BarChart
                data={barData}
              >

                <CartesianGrid />

                <XAxis
                  dataKey="skill"
                  angle={-20}
                  textAnchor="end"
                  height={70}
                />

                <YAxis
                  domain={[0, 100]}
                />

                <Tooltip />

                <Bar
                  dataKey="similarity"
                  name="Similarity %"
                />

              </BarChart>

            </ResponsiveContainer>

          </div>

        </div>


      </div>


      {/* ============================= */}
      {/* AI RECOMMENDATIONS */}
      {/* ============================= */}

      <div className="card">

        <h2>
          AI Recommendations
        </h2>

        <p>

          {recommendations.length}

          {" "}

          AI-generated recommendations
          available.

        </p>


        {recommendations
          .slice(0, 3)
          .map((item, index) => (

            <div
              key={index}
              className="recommendation-card"
            >

              <h3>
                {item.skill}
              </h3>

              <p>

                <strong>
                  Priority:
                </strong>

                {" "}

                {item.priority}

              </p>

              <p>
                {item.explanation}
              </p>

            </div>

          ))}


      </div>


    </div>

  );

}


export default Dashboard;