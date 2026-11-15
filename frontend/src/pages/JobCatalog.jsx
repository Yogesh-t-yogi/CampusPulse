import { useCallback, useEffect, useState } from "react";
import { Database, Pencil, Plus, Trash2, Upload } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import {
  Alert,
  Badge,
  Button,
  ConfirmDialog,
  DataTable,
  Drawer,
  EmptyState,
  Field,
  IconButton,
  Modal,
  Page,
  PageHeader,
  SearchInput,
} from "../ui";
import { useToast } from "../ui/toast";

const EMPTY_FORM = { title: "", company: "", location: "", description: "", skills: "" };

// Admin: manage the shared job catalog (index, create, edit, delete, CSV import).
function JobCatalog({ onChanged }) {
  const toast = useToast();
  const [jobs, setJobs] = useState([]);
  const [skills, setSkills] = useState({});
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [source, setSource] = useState("ALL");
  const [editing, setEditing] = useState(null); // null | "new" | job
  const [form, setForm] = useState(EMPTY_FORM);
  const [deleting, setDeleting] = useState(null);
  const [importOpen, setImportOpen] = useState(false);
  const [csvFile, setCsvFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");

  const load = useCallback(async () => {
    try {
      const [jobsResponse, skillsResponse] = await Promise.all([
        api.get("/api/jobs"),
        api.get("/api/jobs/skills"),
      ]);
      setJobs(Array.isArray(jobsResponse.data) ? jobsResponse.data : []);
      setSkills(skillsResponse.data || {});
    } catch (err) {
      toast(errorMessage(err, "Couldn't load the catalog."), "danger");
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const afterChange = async (message) => {
    toast(message);
    await load();
    onChanged?.();
  };

  const openEditor = (job) => {
    setFormError("");
    setEditing(job || "new");
    setForm(
      job
        ? {
            title: job.title || "",
            company: job.company || "",
            location: job.location || "",
            description: job.description || "",
            skills: (skills[job.id] || []).join(", "),
          }
        : EMPTY_FORM
    );
  };

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setFormError("");
    const body = {
      ...form,
      skills: form.skills
        .split(/[,;\n]/)
        .map((skill) => skill.trim())
        .filter(Boolean),
    };
    try {
      if (editing === "new") {
        await api.post("/api/jobs", body);
      } else {
        await api.put(`/api/jobs/${editing.id}`, body);
      }
      setEditing(null);
      await afterChange(editing === "new" ? "Job added to the catalog" : "Job updated");
    } catch (err) {
      setFormError(errorMessage(err, "Couldn't save the job."));
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setBusy(true);
    try {
      await api.delete(`/api/jobs/${deleting.id}`);
      setDeleting(null);
      await afterChange("Job deleted");
    } catch (err) {
      toast(errorMessage(err, "Couldn't delete."), "danger");
    } finally {
      setBusy(false);
    }
  };

  const importCsv = async (event) => {
    event.preventDefault();
    if (!csvFile) return;
    setBusy(true);
    const data = new FormData();
    data.append("file", csvFile);
    try {
      const response = await api.post("/api/job-catalog/import", data);
      setImportOpen(false);
      setCsvFile(null);
      await afterChange(response.data.message || `${response.data.imported} jobs imported`);
    } catch (err) {
      toast(errorMessage(err, "Couldn't import this CSV."), "danger");
    } finally {
      setBusy(false);
    }
  };

  const rows = jobs.filter((job) => {
    const text = `${job.title} ${job.company || ""} ${job.location || ""}`.toLowerCase();
    return (source === "ALL" || job.source === source) && text.includes(query.trim().toLowerCase());
  });

  const columns = [
    {
      key: "title",
      label: "Job",
      render: (job) => (
        <span className="ui-cell-title">
          <strong>{job.title}</strong>
          <small>{[job.company, job.location].filter(Boolean).join(" · ") || "—"}</small>
        </span>
      ),
    },
    { key: "source", label: "Type", render: (job) => <Badge status={job.source} /> },
    {
      key: "skills",
      label: "Skills",
      render: (job) => {
        const list = skills[job.id] || [];
        return (
          <span className="ui-chip-list">
            {list.slice(0, 4).map((skill) => (
              <span key={skill} className="ui-chip">
                {skill}
              </span>
            ))}
            {list.length > 4 && <span className="ui-muted">+{list.length - 4}</span>}
            {list.length === 0 && <span className="ui-muted">No skills</span>}
          </span>
        );
      },
    },
    {
      key: "actions",
      label: "",
      width: "1%",
      render: (job) => (
        <div className="ui-row-actions">
          <IconButton icon={Pencil} label={`Edit ${job.title}`} onClick={() => openEditor(job)} />
          <IconButton icon={Trash2} label={`Delete ${job.title}`} variant="ghost-danger" onClick={() => setDeleting(job)} />
        </div>
      ),
    },
  ];

  const setField = (key) => (event) => setForm((value) => ({ ...value, [key]: event.target.value }));

  return (
    <Page>
      <PageHeader
        eyebrow="Admin"
        title="Job catalog"
        description="Shared job roles every user can compare a syllabus against. Changing skills refreshes saved reports."
        actions={
          <>
            <Button icon={Upload} onClick={() => setImportOpen(true)}>
              Import CSV
            </Button>
            <Button variant="primary" icon={Plus} onClick={() => openEditor(null)}>
              Add job
            </Button>
          </>
        }
      />

      <DataTable
        loading={loading}
        columns={columns}
        rows={rows}
        toolbar={
          <>
            <div className="ui-table-toolbar-group">
              <SearchInput value={query} onChange={setQuery} placeholder="Search jobs" />
              <select
                className="ui-select"
                style={{ width: "auto" }}
                value={source}
                onChange={(event) => setSource(event.target.value)}
                aria-label="Filter by type"
              >
                <option value="ALL">All types</option>
                <option value="DATASET">Catalog</option>
                <option value="USER_PROVIDED">Private (users)</option>
              </select>
            </div>
            <span className="ui-muted">
              {rows.length} of {jobs.length}
            </span>
          </>
        }
        empty={
          <EmptyState
            icon={Database}
            title={jobs.length ? "No jobs match" : "The catalog is empty"}
            description={jobs.length ? "Try another search." : "Import data/jobs/india-tech-roles-starter.csv or add a job."}
          />
        }
      />

      <Drawer
        open={Boolean(editing)}
        title={editing === "new" ? "Add job" : "Edit job"}
        description="Skills are compared with syllabus topics. Separate them with commas."
        onClose={() => setEditing(null)}
        footer={
          <>
            <Button onClick={() => setEditing(null)}>Cancel</Button>
            <Button variant="primary" type="submit" form="job-form" disabled={busy || !form.title.trim()}>
              {busy ? "Saving…" : "Save job"}
            </Button>
          </>
        }
      >
        <form id="job-form" className="ui-form" onSubmit={save}>
          {formError && <Alert tone="danger">{formError}</Alert>}
          <Field label="Job title">
            <input value={form.title} onChange={setField("title")} required maxLength={200} autoFocus />
          </Field>
          <div className="ui-form-row">
            <Field label="Company">
              <input value={form.company} onChange={setField("company")} />
            </Field>
            <Field label="Location">
              <input value={form.location} onChange={setField("location")} placeholder="Bengaluru" />
            </Field>
          </div>
          <Field label="Description">
            <textarea value={form.description} onChange={setField("description")} rows={4} />
          </Field>
          <Field label="Required skills" hint="Example: Java, Spring Boot, SQL, Git">
            <textarea value={form.skills} onChange={setField("skills")} rows={3} />
          </Field>
        </form>
      </Drawer>

      <Modal
        open={importOpen}
        title="Import jobs from CSV"
        description="Columns: title, company, location, description, skills (separated by ; , or |). Max 10 MB, 10,000 rows."
        onClose={() => setImportOpen(false)}
        footer={
          <>
            <Button onClick={() => setImportOpen(false)}>Cancel</Button>
            <Button variant="primary" type="submit" form="csv-form" disabled={!csvFile || busy}>
              {busy ? "Importing…" : "Import"}
            </Button>
          </>
        }
      >
        <form id="csv-form" className="ui-form" onSubmit={importCsv}>
          <Field label="CSV file" hint="Check the dataset's licence before importing.">
            <input type="file" accept=".csv,text/csv" onChange={(event) => setCsvFile(event.target.files?.[0] || null)} />
          </Field>
        </form>
      </Modal>

      <ConfirmDialog
        open={Boolean(deleting)}
        title="Delete job?"
        message={`"${deleting?.title}" and its saved reports will be deleted for everyone. This can't be undone.`}
        busy={busy}
        onConfirm={remove}
        onClose={() => setDeleting(null)}
      />
    </Page>
  );
}

export default JobCatalog;
