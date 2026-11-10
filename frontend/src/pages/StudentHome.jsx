import { useEffect, useState } from "react";
import { ArrowRight, BookOpen, FileUser, GraduationCap, Route, Target, UserRound } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Alert, Badge, Button, Card, EmptyState, Page, PageHeader, Skeleton, StatCard } from "../ui";
import { SKILL_STATUS } from "../studentStatus";


// Student dashboard: readiness per target role, what to learn next, shortcuts.
function StudentHome({ user, setActivePage }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const firstName = (user?.name || "").split(" ")[0];

  useEffect(() => {
    let cancelled = false;
    api
      .get("/api/student/dashboard")
      .then((response) => !cancelled && setData(response.data))
      .catch((err) => !cancelled && setError(errorMessage(err, "Couldn't load your dashboard.")));
    return () => {
      cancelled = true;
    };
  }, []);

  const roles = data?.roles || [];
  const best = roles.reduce((top, role) => (role.readiness > (top?.readiness ?? -1) ? role : top), null);

  return (
    <Page>
      <PageHeader
        eyebrow={user?.program?.name || "Student"}
        title={firstName ? `Hi ${firstName}, here's where you stand` : "Your career readiness"}
        description="How ready you are for the roles you want, what your course already teaches, and what to learn next."
        actions={
          <>
            <Button icon={UserRound} onClick={() => setActivePage("My profile")}>
              Edit profile
            </Button>
            <Button variant="primary" icon={Route} onClick={() => setActivePage("Learning plan")}>
              Open learning plan
            </Button>
          </>
        }
      />

      {error && <Alert tone="danger">{error}</Alert>}
      {!data && !error && <Skeleton lines={6} />}

      {data && (
        <>
          <div className="ui-grid ui-grid-4">
            <StatCard icon={Target} label="Target roles" value={roles.length} hint="up to 5" />
            <StatCard
              icon={GraduationCap}
              label="Best readiness"
              value={best ? `${Math.round(best.readiness)}%` : "—"}
              hint={best ? best.title : "add a target role"}
              tone="success"
            />
            <StatCard icon={FileUser} label="Your skills" value={data.skillCount} hint="from resume, typed or learned" tone="info" />
            <StatCard
              icon={Route}
              label="Learning progress"
              value={`${data.learningDone}/${data.learningTotal}`}
              hint="skills done"
              tone="warning"
            />
          </div>

          {!data.course && (
            <Alert tone="info" title="No course syllabus yet">
              Your department hasn't shared a syllabus for your program. Pick one in your profile, or ask your faculty.
            </Alert>
          )}

          {roles.length === 0 ? (
            <Card>
              <EmptyState
                icon={Target}
                title="Choose the roles you want"
                description="Pick up to 5 target roles. CampusPulse shows how ready you are and what to learn for each."
                action={
                  <Button variant="primary" icon={ArrowRight} onClick={() => setActivePage("My profile")}>
                    Choose target roles
                  </Button>
                }
              />
            </Card>
          ) : (
            <div className="ui-grid ui-grid-main">
              <div className="ui-grid">
                {roles.map((role) => (
                  <Card
                    key={role.jobId}
                    title={role.title}
                    description={role.company || undefined}
                    actions={<Badge tone={role.readiness >= 70 ? "success" : role.readiness >= 40 ? "warning" : "danger"}>{Math.round(role.readiness)}% ready</Badge>}
                  >
                    <div className="cp-readiness" aria-hidden="true">
                      <span style={{ width: `${role.readiness}%` }} />
                    </div>
                    {role.note && <p className="ui-muted">{role.note}</p>}
                    <div className="ui-chip-list" style={{ marginTop: 12 }}>
                      {role.skills.map((skill) => (
                        <Badge key={skill.skill} tone={SKILL_STATUS[skill.status].tone}>
                          {skill.skill}
                        </Badge>
                      ))}
                    </div>
                  </Card>
                ))}
                <p className="ui-muted">
                  Colours:{" "}
                  {Object.entries(SKILL_STATUS).slice(0, 4).map(([key, value]) => (
                    <span key={key} style={{ marginRight: 8 }}>
                      <Badge tone={value.tone}>{value.label}</Badge>
                    </span>
                  ))}
                </p>
              </div>

              <div className="ui-grid">
                <Card title="Learn next" description="Needed by most of your target roles.">
                  {data.nextToLearn.length === 0 ? (
                    <p className="ui-muted">Nothing left to learn for your target roles.</p>
                  ) : (
                    <ul className="ui-list">
                      {data.nextToLearn.map((item) => (
                        <li key={item.skill}>
                          <span className="ui-cell-title">
                            <strong>{item.skill}</strong>
                            <small>For {item.roles.join(", ")}</small>
                          </span>
                          <Badge tone={item.status === "LEARNING" ? "info" : "neutral"}>
                            {item.status === "LEARNING" ? "Learning" : "Not started"}
                          </Badge>
                        </li>
                      ))}
                    </ul>
                  )}
                  <div style={{ marginTop: 12 }}>
                    <Button icon={Route} onClick={() => setActivePage("Learning plan")}>
                      See how to learn them
                    </Button>
                  </div>
                </Card>
                <Card title="Your course" description={data.course ? data.course.name : "Not set"}>
                  <Button icon={BookOpen} onClick={() => setActivePage("My profile")}>
                    Change course
                  </Button>
                </Card>
                <Card title="Check your resume" description="Add skills from your resume in one step.">
                  <Button icon={FileUser} onClick={() => setActivePage("Resume match")}>
                    Open resume match
                  </Button>
                </Card>
              </div>
            </div>
          )}
        </>
      )}
    </Page>
  );
}

export default StudentHome;
