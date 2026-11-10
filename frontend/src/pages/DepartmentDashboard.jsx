import { useEffect, useState } from "react";
import { BookOpen, Gauge, GraduationCap, SlidersHorizontal, Target, Users as UsersIcon } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Alert, Badge, Button, Card, DataTable, Drawer, EmptyState, Page, PageHeader, SearchInput, Skeleton, StatCard } from "../ui";

function coverageTone(value) {
  if (value === null || value === undefined) return "neutral";
  if (value >= 70) return "success";
  if (value >= 40) return "warning";
  return "danger";
}

const pct = (value) => (value === null || value === undefined || value < 0 ? "—" : `${Math.round(value)}%`);

// Department / college dashboard: curriculum coverage vs benchmark roles, common gaps,
// students' readiness. HOD/faculty see their department; admins pick one or the whole college.
function DepartmentDashboard({ user, jobs }) {
  const isAdmin = user?.role === "ADMIN" || user?.role === "SUPER_ADMIN";
  const [departments, setDepartments] = useState([]);
  const [departmentId, setDepartmentId] = useState("");
  const [jobIds, setJobIds] = useState([]);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [draftJobs, setDraftJobs] = useState([]);
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");

  useEffect(() => {
    if (!isAdmin) return;
    api
      .get("/api/org/departments")
      .then(({ data: list }) => setDepartments(Array.isArray(list) ? list : []))
      .catch(() => setDepartments([]));
  }, [isAdmin]);

  useEffect(() => {
    let cancelled = false;
    api
      .get("/api/dashboard", {
        params: {
          departmentId: departmentId || undefined,
          jobIds: jobIds.length ? jobIds.join(",") : undefined,
        },
      })
      .then((response) => {
        if (cancelled) return;
        setData(response.data);
        setError("");
      })
      .catch((err) => !cancelled && setError(errorMessage(err, "Couldn't load the dashboard.")))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [departmentId, jobIds]);

  const reload = (change) => {
    setLoading(true);
    change();
  };

  const students = (data?.students || []).filter((student) =>
    `${student.name} ${student.rollNumber || ""}`.toLowerCase().includes(search.trim().toLowerCase())
  );
  const bands = data?.readinessBands || {};

  return (
    <Page>
      <PageHeader
        eyebrow={data?.scope === "COLLEGE" ? user?.institution?.name || "College" : "Department"}
        title={data?.departmentName ? `${data.departmentName} dashboard` : "College dashboard"}
        description="How well the curriculum covers the roles students aim for, the most common gaps, and which students need support."
        actions={
          <>
            {isAdmin && (
              <select
                className="ui-select"
                style={{ width: "auto" }}
                value={departmentId}
                aria-label="Department"
                onChange={(e) => reload(() => setDepartmentId(e.target.value))}
              >
                <option value="">Whole college</option>
                {departments.map((item) => (
                  <option key={item.id} value={String(item.id)}>
                    {item.name}
                  </option>
                ))}
              </select>
            )}
            <Button
              icon={SlidersHorizontal}
              onClick={() => {
                setDraftJobs(data?.jobs?.map((job) => job.jobId) || []);
                setPickerOpen(true);
              }}
            >
              Benchmark roles
            </Button>
          </>
        }
      />

      {error && <Alert tone="danger">{error}</Alert>}
      {loading && <Alert tone="info">Comparing syllabi with the benchmark roles. The first time can take a minute; after that it's saved.</Alert>}
      {!data && !error && <Skeleton lines={8} />}

      {data && (
        <>
          <div className="ui-grid ui-grid-4">
            <StatCard icon={BookOpen} label="Syllabi" value={`${data.syllabusProcessed}/${data.syllabusTotal}`} hint="processed / uploaded" />
            <StatCard icon={Gauge} label="Average coverage" value={pct(data.averageCoverage)} hint="syllabi × benchmark roles" tone="info" />
            <StatCard icon={UsersIcon} label="Students" value={data.studentCount} hint={`${bands.noTargets || 0} without target roles`} tone="warning" />
            <StatCard icon={GraduationCap} label="Job-ready students" value={bands.ready || 0} hint="best role ≥ 70%" tone="success" />
          </div>

          <Card
            title="Curriculum coverage"
            description="Each processed syllabus against the benchmark roles (most targeted by students, else catalog roles)."
          >
            {data.coverage.length === 0 || data.jobs.length === 0 ? (
              <EmptyState icon={BookOpen} title="Nothing to compare yet" description="Process a syllabus and make sure jobs exist in the catalog." />
            ) : (
              <div className="ui-table-scroll">
                <table className="ui-table">
                  <thead>
                    <tr>
                      <th>Syllabus</th>
                      {data.jobs.map((job) => (
                        <th key={job.jobId}>{job.title}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {data.coverage.map((row) => (
                      <tr key={row.id}>
                        <td>
                          <span className="ui-cell-title">
                            <strong>{row.name}</strong>
                            <small>
                              {[row.programName, row.semester ? `Sem ${row.semester}` : null, data.scope === "COLLEGE" ? row.departmentName : null]
                                .filter(Boolean)
                                .join(" · ") || "No program"}
                            </small>
                          </span>
                        </td>
                        {data.jobs.map((job) => {
                          const value = row.coverage[String(job.jobId)];
                          return (
                            <td key={job.jobId}>
                              <Badge tone={coverageTone(value)}>{pct(value)}</Badge>
                            </td>
                          );
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>

          <div className="ui-grid ui-grid-2">
            <Card title="Most common gaps" description="Skills missing from the most syllabus × role reports — candidates for new topics or electives.">
              {data.topGaps.length === 0 ? (
                <p className="ui-muted">No gaps found.</p>
              ) : (
                <div className="ui-chip-list">
                  {data.topGaps.map((gap) => (
                    <Badge key={gap.skill} tone="danger">
                      {gap.skill} · {gap.reports}
                    </Badge>
                  ))}
                </div>
              )}
            </Card>
            <Card title="What students aim for" description="Most chosen target roles.">
              {data.popularRoles.length === 0 ? (
                <p className="ui-muted">Students haven't chosen target roles yet.</p>
              ) : (
                <ul className="ui-list">
                  {data.popularRoles.map((role) => (
                    <li key={role.jobId}>
                      <span>
                        <Target size={14} aria-hidden="true" /> {role.title}
                      </span>
                      <Badge tone="neutral">{role.students} students</Badge>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>

          <Card title="Students' readiness" description="Best and average readiness over each student's target roles. Lowest first.">
            <div className="cp-bands">
              <span><Badge tone="success">Ready ≥70%</Badge> {bands.ready || 0}</span>
              <span><Badge tone="warning">Getting there 40–69%</Badge> {bands.growing || 0}</span>
              <span><Badge tone="danger">Need help &lt;40%</Badge> {bands.needHelp || 0}</span>
              <span><Badge tone="neutral">No target roles</Badge> {bands.noTargets || 0}</span>
            </div>
            <DataTable
              columns={[
                {
                  key: "name",
                  label: "Student",
                  render: (s) => (
                    <span className="ui-cell-title">
                      <strong>{s.name}</strong>
                      <small>{[s.rollNumber, s.batchYear ? `Batch ${s.batchYear}` : null].filter(Boolean).join(" · ")}</small>
                    </span>
                  ),
                },
                { key: "targetRoles", label: "Target roles", render: (s) => (s.targetRoles.length ? s.targetRoles.join(", ") : <span className="ui-muted">None yet</span>) },
                { key: "best", label: "Best", render: (s) => <Badge tone={s.best < 0 ? "neutral" : coverageTone(s.best)}>{pct(s.best)}</Badge> },
                { key: "average", label: "Average", render: (s) => pct(s.average) },
                { key: "learningDone", label: "Skills learned" },
              ]}
              rows={students}
              toolbar={<SearchInput value={search} onChange={setSearch} placeholder="Search name or roll number" />}
              empty={<EmptyState icon={UsersIcon} title="No students" description="Add students in Users (Excel import)." />}
            />
          </Card>
        </>
      )}

      <Drawer
        open={pickerOpen}
        title="Benchmark roles"
        description="Pick up to 5 roles to compare every syllabus against. Leave empty for automatic."
        onClose={() => setPickerOpen(false)}
        footer={
          <>
            <Button onClick={() => reload(() => { setJobIds([]); setPickerOpen(false); })}>Automatic</Button>
            <Button variant="primary" onClick={() => reload(() => { setJobIds(draftJobs); setPickerOpen(false); })}>
              Apply
            </Button>
          </>
        }
      >
        <div className="cp-compare-list">
          {jobs.map((job) => (
            <label key={job.id} className="cp-compare-option">
              <input
                type="checkbox"
                checked={draftJobs.includes(job.id)}
                disabled={!draftJobs.includes(job.id) && draftJobs.length >= 5}
                onChange={() =>
                  setDraftJobs((list) => (list.includes(job.id) ? list.filter((id) => id !== job.id) : [...list, job.id]))
                }
              />
              <span>{job.title}</span>
            </label>
          ))}
        </div>
      </Drawer>
    </Page>
  );
}

export default DepartmentDashboard;
