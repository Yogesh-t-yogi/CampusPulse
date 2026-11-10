import { useCallback, useEffect, useState } from "react";
import { BookOpen, ChevronDown, ChevronUp, ExternalLink, Route, Target } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Alert, Badge, Button, Card, EmptyState, Page, PageHeader, Skeleton } from "../ui";
import { useToast } from "../ui/toast";

const STATUS_OPTIONS = [
  ["TODO", "Not started"],
  ["LEARNING", "Learning"],
  ["DONE", "Done"],
];
const FILTERS = [
  ["OPEN", "To do and learning"],
  ["ALL", "All"],
  ["DONE", "Done"],
];

// One skill: why (roles, demand), where (resources), how (roadmap on demand), progress.
function SkillCard({ item, onStatus }) {
  const [open, setOpen] = useState(false);
  const [roadmap, setRoadmap] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const toggle = async () => {
    const next = !open;
    setOpen(next);
    if (next && !roadmap && !loading) {
      setLoading(true);
      setError("");
      try {
        const { data } = await api.get("/api/student/roadmap", { params: { skill: item.skill } });
        setRoadmap(data);
      } catch (err) {
        setError(errorMessage(err, "Couldn't load the roadmap."));
      } finally {
        setLoading(false);
      }
    }
  };

  return (
    <Card
      title={item.skill}
      description={`Needed for ${item.roles.join(", ")}${item.demand ? ` · in ${item.demand} catalog jobs` : ""}`}
      actions={
        <select
          className={`ui-select cp-learn-status cp-learn-${item.status.toLowerCase()}`}
          value={item.status}
          aria-label={`Progress for ${item.skill}`}
          onChange={(event) => onStatus(item.skill, event.target.value)}
        >
          {STATUS_OPTIONS.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      }
    >
      <p className="ui-muted" style={{ marginTop: 0 }}>
        {item.courseStatus === "PARTLY"
          ? `Your course touches this in "${item.courseTopic}" — go deeper.`
          : "Not covered by your course — learn it on your own."}
      </p>

      <p className="ui-field-label">Where to learn</p>
      <div className="ui-chip-list" style={{ marginBottom: 12 }}>
        {item.resources.map((resource) => (
          <a key={resource.url} className="cp-resource" href={resource.url} target="_blank" rel="noopener noreferrer">
            {resource.label} <ExternalLink size={12} aria-hidden="true" />
          </a>
        ))}
      </div>

      <Button variant="ghost" icon={open ? ChevronUp : ChevronDown} onClick={toggle} aria-expanded={open}>
        How to learn it
      </Button>
      {open && (
        <div className="cp-roadmap">
          {loading && <Skeleton lines={4} />}
          {error && <Alert tone="danger">{error}</Alert>}
          {roadmap && (
            <>
              {roadmap.summary && <p>{roadmap.summary}</p>}
              <ol className="ui-steps">
                {roadmap.steps.map((step, index) => (
                  <li key={`${step.title}-${index}`}>
                    <span className="ui-step-dot">{index + 1}</span>
                    <span className="ui-cell-title">
                      <strong>
                        {step.title}
                        {step.hours ? <small> · about {step.hours} h</small> : null}
                      </strong>
                      <small>{step.action}</small>
                    </span>
                  </li>
                ))}
              </ol>
              {roadmap.project && (
                <Alert tone="info" title="Prove it with a project">
                  {roadmap.project}
                </Alert>
              )}
            </>
          )}
        </div>
      )}
    </Card>
  );
}

// What to learn for the target roles, where and how, with progress.
function LearningPlan({ setActivePage }) {
  const toast = useToast();
  const [plan, setPlan] = useState(null);
  const [filter, setFilter] = useState("OPEN");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const { data } = await api.get("/api/student/learning-plan");
      setPlan(data);
    } catch (err) {
      setError(errorMessage(err, "Couldn't load your learning plan."));
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const setStatus = async (skill, status) => {
    try {
      await api.put("/api/student/learning", { skill, status });
      setPlan((current) => ({
        ...current,
        items: current.items.map((item) => (item.skill === skill ? { ...item, status } : item)),
      }));
      if (status === "DONE") toast(`${skill} added to your skills`);
    } catch (err) {
      toast(errorMessage(err, "Couldn't save progress."), "danger");
    }
  };

  const items = (plan?.items || []).filter((item) =>
    filter === "ALL" ? true : filter === "DONE" ? item.status === "DONE" : item.status !== "DONE"
  );
  const done = (plan?.items || []).filter((item) => item.status === "DONE").length;
  const total = plan?.items?.length || 0;

  return (
    <Page>
      <PageHeader
        eyebrow="My growth"
        title="Learning plan"
        description="Skills your target roles need that you don't have yet — most needed first — with free places to learn them and a step-by-step plan."
        actions={
          <select className="ui-select" style={{ width: "auto" }} value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Show">
            {FILTERS.map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        }
      />

      {error && <Alert tone="danger">{error}</Alert>}
      {!plan && !error && <Skeleton lines={6} />}

      {plan && total > 0 && (
        <Card>
          <div className="cp-learn-progress">
            <strong>
              {done} of {total} skills done
            </strong>
            <div className="cp-readiness" aria-hidden="true">
              <span style={{ width: `${total ? (done / total) * 100 : 0}%` }} />
            </div>
          </div>
        </Card>
      )}

      {plan && total === 0 && (
        <Card>
          <EmptyState
            icon={Route}
            title="Nothing to learn yet"
            description="Choose target roles in your profile. If you already have every skill they need — well done!"
            action={
              <Button variant="primary" icon={Target} onClick={() => setActivePage("My profile")}>
                Choose target roles
              </Button>
            }
          />
        </Card>
      )}

      {plan && total > 0 && items.length === 0 && (
        <p className="ui-muted">
          <BookOpen size={14} aria-hidden="true" /> Nothing in this filter.
        </p>
      )}

      <div className="ui-grid ui-grid-2">
        {items.map((item) => (
          <SkillCard key={item.skill} item={item} onStatus={setStatus} />
        ))}
      </div>
      {plan && total > 0 && (
        <p className="ui-muted">
          <Badge tone="neutral">Tip</Badge> Marking a skill Done adds it to your skills and raises your readiness on the dashboard.
        </p>
      )}
    </Page>
  );
}

export default LearningPlan;
