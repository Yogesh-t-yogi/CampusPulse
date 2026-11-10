import { useCallback, useEffect, useState } from "react";
import { Plus, Save, Target, X } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Badge, Button, Card, Field, Page, PageHeader, SearchInput, Skeleton } from "../ui";
import { useToast } from "../ui/toast";

const SOURCE_LABEL = { RESUME: "From resume", SELF: "Added by you", LEARNED: "Learned" };
const MAX_ROLES = 5;

// Student profile: target roles (≤5), my skills, my course syllabus.
function StudentProfile({ user, jobs, syllabi, onChanged }) {
  const toast = useToast();
  const [profile, setProfile] = useState(null);
  const [roleIds, setRoleIds] = useState([]);
  const [roleSearch, setRoleSearch] = useState("");
  const [newSkills, setNewSkills] = useState("");
  const [courseId, setCourseId] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const { data } = await api.get("/api/student/profile");
      setProfile(data);
      setRoleIds(data.targetRoles.map((role) => role.jobId));
      setCourseId(data.courseChosen && data.course ? String(data.course.id) : "");
    } catch (err) {
      toast(errorMessage(err, "Couldn't load your profile."), "danger");
    }
  }, [toast]);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const toggleRole = (id) =>
    setRoleIds((list) =>
      list.includes(id) ? list.filter((item) => item !== id) : list.length >= MAX_ROLES ? list : [...list, id]
    );

  const saveRoles = async () => {
    setBusy(true);
    try {
      await api.put("/api/student/target-roles", { jobIds: roleIds });
      toast("Target roles saved");
      await load();
      onChanged?.();
    } catch (err) {
      toast(errorMessage(err, "Couldn't save target roles."), "danger");
    } finally {
      setBusy(false);
    }
  };

  const addSkills = async (event) => {
    event.preventDefault();
    const skills = newSkills.split(/[,;\n]/).map((item) => item.trim()).filter(Boolean);
    if (skills.length === 0) return;
    try {
      const { data } = await api.post("/api/student/skills", { skills, source: "SELF" });
      toast(`${data.added} skill${data.added === 1 ? "" : "s"} added`);
      setNewSkills("");
      await load();
    } catch (err) {
      toast(errorMessage(err, "Couldn't add skills."), "danger");
    }
  };

  const removeSkill = async (skill) => {
    try {
      await api.delete("/api/student/skills", { params: { skill } });
      await load();
    } catch (err) {
      toast(errorMessage(err, "Couldn't remove the skill."), "danger");
    }
  };

  const saveCourse = async (value) => {
    setCourseId(value);
    try {
      await api.put("/api/student/course", { syllabusId: value ? Number(value) : null });
      toast(value ? "Course saved" : "Using the automatic course");
      await load();
    } catch (err) {
      toast(errorMessage(err, "Couldn't save the course."), "danger");
    }
  };

  if (!profile) {
    return (
      <Page>
        <Skeleton lines={6} />
      </Page>
    );
  }

  const visibleJobs = jobs
    .filter((job) => `${job.title} ${job.company || ""} ${job.location || ""}`.toLowerCase().includes(roleSearch.trim().toLowerCase()))
    .slice(0, 60);
  const rolesChanged =
    roleIds.length !== profile.targetRoles.length ||
    roleIds.some((id) => !profile.targetRoles.some((role) => role.jobId === id));

  return (
    <Page>
      <PageHeader
        eyebrow="My growth"
        title="My profile"
        description="The roles you want, the skills you have, and the course you're studying. Your dashboard and learning plan use these."
      />

      <Card
        title="Target roles"
        description={`Pick up to ${MAX_ROLES}. Selected: ${roleIds.length}.`}
        actions={
          <Button variant="primary" icon={Save} onClick={saveRoles} disabled={busy || !rolesChanged}>
            Save roles
          </Button>
        }
      >
        <div className="ui-chip-list" style={{ marginBottom: 12 }}>
          {roleIds.length === 0 && <span className="ui-muted">No target roles yet.</span>}
          {roleIds.map((id) => {
            const job = jobs.find((item) => item.id === id) || profile.targetRoles.find((role) => role.jobId === id);
            return (
              <span key={id} className="ui-chip">
                <Target size={12} aria-hidden="true" /> {job?.title || `Job ${id}`}
                <button type="button" className="cp-chip-remove" aria-label={`Remove ${job?.title}`} onClick={() => toggleRole(id)}>
                  <X size={12} />
                </button>
              </span>
            );
          })}
        </div>
        <SearchInput value={roleSearch} onChange={setRoleSearch} placeholder="Search roles (e.g. data analyst, Bengaluru)" />
        <div className="cp-compare-list" style={{ marginTop: 10 }}>
          {visibleJobs.map((job) => (
            <label key={job.id} className="cp-compare-option">
              <input
                type="checkbox"
                checked={roleIds.includes(job.id)}
                disabled={!roleIds.includes(job.id) && roleIds.length >= MAX_ROLES}
                onChange={() => toggleRole(job.id)}
              />
              <span>
                {job.title}
                {job.company || job.location ? <small> · {[job.company, job.location].filter(Boolean).join(", ")}</small> : null}
              </span>
            </label>
          ))}
        </div>
      </Card>

      <div className="ui-grid ui-grid-2">
        <Card title="My skills" description="Skills you already have. Add them by hand, from your resume, or by finishing them in your learning plan.">
          <div className="ui-chip-list" style={{ marginBottom: 12 }}>
            {profile.skills.length === 0 && <span className="ui-muted">No skills yet.</span>}
            {profile.skills.map((item) => (
              <span key={item.skill} className="ui-chip" title={SOURCE_LABEL[item.source]}>
                {item.skill}
                <small className="ui-muted"> · {SOURCE_LABEL[item.source]}</small>
                <button type="button" className="cp-chip-remove" aria-label={`Remove ${item.skill}`} onClick={() => removeSkill(item.skill)}>
                  <X size={12} />
                </button>
              </span>
            ))}
          </div>
          <form className="ui-form" onSubmit={addSkills}>
            <Field label="Add skills" hint="Separate with commas, e.g. Java, SQL, Git">
              <input value={newSkills} onChange={(e) => setNewSkills(e.target.value)} />
            </Field>
            <Button icon={Plus} type="submit" disabled={!newSkills.trim()}>
              Add
            </Button>
          </form>
        </Card>

        <Card title="My course" description="The syllabus used to show what your course already teaches.">
          <Field label="Course syllabus" hint={`Automatic now: ${profile.course ? profile.course.name : "none found"}`}>
            <select value={courseId} onChange={(e) => saveCourse(e.target.value)}>
              <option value="">Automatic (newest shared with my program)</option>
              {syllabi.map((item) => (
                <option key={item.id} value={String(item.id)}>
                  {item.name}
                  {item.semester ? ` · Semester ${item.semester}` : ""}
                </option>
              ))}
            </select>
          </Field>
          <p className="ui-muted" style={{ marginTop: 8 }}>
            Program: {user?.program?.name || "not set — ask your HOD"} <Badge tone="neutral">{user?.department?.name || "No department"}</Badge>
          </p>
        </Card>
      </div>
    </Page>
  );
}

export default StudentProfile;
