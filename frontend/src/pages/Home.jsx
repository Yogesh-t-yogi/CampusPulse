import { useEffect, useState } from "react";
import {
  ArrowRight,
  BookOpen,
  BriefcaseBusiness,
  ChartNoAxesCombined,
  Check,
  FileUp,
  FileUser,
  Files,
  Gauge,
  Layers,
} from "lucide-react";
import api from "../apiClient";
import { Badge, Button, Card, EmptyState, Page, PageHeader, StatCard } from "../ui";

function greeting() {
  const hour = new Date().getHours();
  return hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
}

function Home({ user, syllabi, jobs, selectedSyllabusId, selectedJobId, setActivePage }) {
  const [coverage, setCoverage] = useState(null);
  const isStudent = user?.role === "STUDENT";
  const firstName = (user?.name || "").split(" ")[0];

  // Coverage of the current syllabus + job (saved result; cheap after the first run).
  useEffect(() => {
    if (!selectedSyllabusId || !selectedJobId) return undefined;
    let cancelled = false;
    api
      .get(`/api/matching/semantic/report/jobs/${selectedJobId}/syllabus/${selectedSyllabusId}`)
      .then(({ data }) => !cancelled && setCoverage(Math.round(data.coveragePercentage)))
      .catch(() => !cancelled && setCoverage(null));
    return () => {
      cancelled = true;
    };
  }, [selectedSyllabusId, selectedJobId]);

  const topicCount = syllabi.reduce((sum, item) => sum + Number(item.topicCount || 0), 0);
  const selected = syllabi.find((item) => String(item.id) === String(selectedSyllabusId));
  const processed = Number(selected?.topicCount || 0) > 0;

  const steps = [
    { label: "Upload a syllabus", done: syllabi.length > 0, page: "Upload Syllabus" },
    { label: "Review the extracted curriculum", done: processed, page: "Curriculum" },
    { label: "Choose a job role", done: Boolean(selectedJobId), page: "Job Market" },
    { label: "Read the skill gap report", done: coverage !== null, page: "Report" },
  ];
  const next = steps.find((step) => !step.done);

  return (
    <Page>
      <PageHeader
        eyebrow={greeting()}
        title={firstName ? `Welcome back, ${firstName}` : "Welcome back"}
        description={
          isStudent
            ? "See how your course prepares you for the jobs you want, and what to learn next."
            : "Compare your curriculum with what employers ask for, and find the gaps to close."
        }
        actions={
          <>
            <Button icon={FileUp} onClick={() => setActivePage("Upload Syllabus")}>
              Upload syllabus
            </Button>
            <Button
              variant="primary"
              icon={ChartNoAxesCombined}
              onClick={() => setActivePage(selectedJobId ? "Report" : "Job Market")}
              disabled={!selectedSyllabusId}
            >
              {selectedJobId ? "Open report" : "Choose a job"}
            </Button>
          </>
        }
      />

      <div className="ui-grid ui-grid-4">
        <StatCard icon={Files} label="Syllabi" value={syllabi.length} hint="uploaded by you" />
        <StatCard icon={Layers} label="Topics indexed" value={topicCount} hint="across all syllabi" tone="info" />
        <StatCard icon={BriefcaseBusiness} label="Job roles" value={jobs.length} hint="in the catalog and yours" tone="warning" />
        <StatCard
          icon={Gauge}
          label="Current coverage"
          value={coverage === null ? "—" : `${coverage}%`}
          hint={coverage === null ? "choose a syllabus and a job" : "selected syllabus vs job"}
          tone="success"
        />
      </div>

      <div className="ui-grid ui-grid-main">
        <Card
          title="Your syllabi"
          description="Most recent first."
          actions={
            <Button variant="ghost" icon={ArrowRight} onClick={() => setActivePage("Syllabi")}>
              View all
            </Button>
          }
        >
          {syllabi.length === 0 ? (
            <EmptyState
              icon={BookOpen}
              title="No syllabus yet"
              description="Upload a syllabus PDF. CampusPulse extracts its subjects, units and topics."
              action={
                <Button variant="primary" icon={FileUp} onClick={() => setActivePage("Upload Syllabus")}>
                  Upload syllabus
                </Button>
              }
            />
          ) : (
            <ul className="ui-list">
              {syllabi.slice(0, 5).map((item) => (
                <li key={item.id}>
                  <span className="ui-cell-title">
                    <strong>{item.name || item.fileName}</strong>
                    <small>
                      {Number(item.subjectCount || 0)} subjects · {Number(item.topicCount || 0)} topics
                      {item.createdAt ? ` · ${new Date(item.createdAt).toLocaleDateString()}` : ""}
                    </small>
                  </span>
                  <Badge status={item.processingStatus || (Number(item.topicCount) > 0 ? "READY" : "NOT_PROCESSED")} />
                </li>
              ))}
            </ul>
          )}
        </Card>

        <div className="ui-grid">
          <Card title="Getting started" description={next ? "Your next step is highlighted." : "All steps done."}>
            <ol className="ui-steps">
              {steps.map((step, index) => (
                <li
                  key={step.label}
                  className={step.done ? "ui-step-done" : step === next ? "ui-step-current" : ""}
                >
                  <span className="ui-step-dot">{step.done ? <Check size={14} aria-label="Done" /> : index + 1}</span>
                  {step.label}
                </li>
              ))}
            </ol>
            {next && (
              <div style={{ marginTop: 16 }}>
                <Button variant="primary" icon={ArrowRight} onClick={() => setActivePage(next.page)}>
                  {next.label}
                </Button>
              </div>
            )}
          </Card>

          {isStudent && (
            <Card title="Check your resume" description="See which jobs fit the skills on your resume.">
              <Button icon={FileUser} onClick={() => setActivePage("Resume match")}>
                Open resume match
              </Button>
            </Card>
          )}
        </div>
      </div>
    </Page>
  );
}

export default Home;
