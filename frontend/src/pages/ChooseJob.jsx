import { useCallback, useEffect, useState } from "react";
import { Briefcase, Building2, CheckCircle2, ClipboardPaste, MapPin, RefreshCw, Sparkles } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Alert, Badge, Button, Card, Drawer, EmptyState, Field, IconButton, Page, PageHeader, SearchInput, Skeleton } from "../ui";
import { useToast } from "../ui/toast";

const PER_PAGE = 24;
const SHOWN_SKILLS = 8;
const EMPTY_JOB = { title: "", company: "", location: "", description: "" };

// Step 3: pick a catalog role or paste a job description (private custom job).
function ChooseJob({ isAdmin = false, selectedJobId, hasSyllabus, onJobCreated, onJobSelected }) {
  const toast = useToast();
  const [jobs, setJobs] = useState([]);
  const [skills, setSkills] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const [extracting, setExtracting] = useState(null);
  const [pasteOpen, setPasteOpen] = useState(false);
  const [draft, setDraft] = useState(EMPTY_JOB);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");

  const load = useCallback(async () => {
    setError("");
    try {
      const [jobList, skillMap] = await Promise.all([
        api.get("/api/jobs"),
        api.get("/api/jobs/skills").catch(() => ({ data: {} })),
      ]);
      setJobs(Array.isArray(jobList.data) ? jobList.data : []);
      setSkills(skillMap.data || {});
    } catch (err) {
      setError(errorMessage(err, "Couldn't load jobs."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const extract = async (jobId) => {
    setExtracting(jobId);
    try {
      await api.post(`/api/ai/jobs/${jobId}/extract-skills`, {});
      const { data } = await api.get(`/api/jobs/${jobId}/skills`);
      setSkills((prev) => ({ ...prev, [jobId]: (Array.isArray(data) ? data : []).map((s) => s.skill) }));
      toast("Skills re-extracted.");
    } catch (err) {
      toast(errorMessage(err, "Skill extraction failed."), "danger");
    } finally {
      setExtracting(null);
    }
  };

  const createCustom = async (event) => {
    event.preventDefault();
    setCreating(true);
    setCreateError("");
    try {
      const { data } = await api.post("/api/jobs/custom", draft);
      setDraft(EMPTY_JOB);
      setPasteOpen(false);
      await load();
      toast("Job created and skills extracted.");
      onJobCreated?.(data);
    } catch (err) {
      setCreateError(errorMessage(err, "Couldn't create this job. Check the description and try again."));
    } finally {
      setCreating(false);
    }
  };

  const query = search.trim().toLowerCase();
  const filtered = jobs.filter(
    (job) =>
      !query ||
      [job.title, job.company || job.companyName, job.location].some((v) => String(v || "").toLowerCase().includes(query)) ||
      (skills[job.id] || []).some((s) => String(s).toLowerCase().includes(query))
  );
  const pageCount = Math.max(1, Math.ceil(filtered.length / PER_PAGE));
  const current = Math.min(page, pageCount);
  const visible = filtered.slice((current - 1) * PER_PAGE, current * PER_PAGE);
  const set = (key) => (e) => setDraft((d) => ({ ...d, [key]: e.target.value }));

  return (
    <Page>
      <PageHeader
        eyebrow="Step 3 of 4"
        title="Choose a job"
        description="Pick a role from the catalog or paste a job description. Its skills are compared with your syllabus."
        actions={
          <>
            <IconButton icon={RefreshCw} label="Refresh" onClick={() => { setLoading(true); void load(); }} />
            <Button variant="primary" icon={ClipboardPaste} onClick={() => setPasteOpen(true)} disabled={!hasSyllabus}>
              Paste a job description
            </Button>
          </>
        }
      />

      {!hasSyllabus && <Alert tone="warning">Choose or upload a processed syllabus first (Syllabi page).</Alert>}
      {error && <Alert tone="danger">{error}</Alert>}

      <Card
        title="Job catalog"
        description={`${filtered.length} of ${jobs.length} roles`}
        actions={<SearchInput value={search} onChange={(v) => { setSearch(v); setPage(1); }} placeholder="Search role, company, skill" />}
      >
        {loading ? (
          <Skeleton lines={6} />
        ) : visible.length === 0 ? (
          <EmptyState
            icon={Briefcase}
            title={jobs.length ? "No matching roles" : "No jobs yet"}
            description={jobs.length ? "Try another search." : "Ask an admin to import the job catalog, or paste a job description."}
          />
        ) : (
          <div className="cj-grid">
            {visible.map((job) => {
              const list = skills[job.id] || [];
              const selected = String(selectedJobId) === String(job.id);
              const company = job.company || job.companyName;
              return (
                <article key={job.id} className={`cj-card${selected ? " is-selected" : ""}`}>
                  <header>
                    <strong>{job.title || "Untitled role"}</strong>
                    {job.source === "USER_PROVIDED" && <Badge tone="info">Yours</Badge>}
                    {selected && <Badge tone="accent">Selected</Badge>}
                  </header>
                  <div className="cj-meta">
                    {company && (
                      <span>
                        <Building2 size={13} aria-hidden="true" /> {company}
                      </span>
                    )}
                    {job.location && (
                      <span>
                        <MapPin size={13} aria-hidden="true" /> {job.location}
                      </span>
                    )}
                  </div>
                  <div className="ui-chip-list">
                    {list.length === 0 ? (
                      <span className="ui-muted">No skills yet</span>
                    ) : (
                      <>
                        {list.slice(0, SHOWN_SKILLS).map((s) => (
                          <Badge key={s} tone="neutral">{s}</Badge>
                        ))}
                        {list.length > SHOWN_SKILLS && <Badge tone="neutral">+{list.length - SHOWN_SKILLS}</Badge>}
                      </>
                    )}
                  </div>
                  <footer>
                    {(isAdmin || job.source === "USER_PROVIDED") && (
                      <IconButton
                        icon={Sparkles}
                        label={extracting === job.id ? "Extracting…" : "Re-extract skills with AI"}
                        onClick={() => extract(job.id)}
                        disabled={extracting !== null}
                      />
                    )}
                    <Button
                      variant={selected ? "secondary" : "primary"}
                      icon={selected ? CheckCircle2 : undefined}
                      onClick={() => onJobSelected?.(job)}
                      disabled={!hasSyllabus}
                    >
                      {selected ? "Continue" : "Use this role"}
                    </Button>
                  </footer>
                </article>
              );
            })}
          </div>
        )}
        {pageCount > 1 && (
          <nav className="cj-pages" aria-label="Pages">
            <Button onClick={() => setPage(current - 1)} disabled={current === 1}>Previous</Button>
            <span>Page {current} of {pageCount}</span>
            <Button onClick={() => setPage(current + 1)} disabled={current === pageCount}>Next</Button>
          </nav>
        )}
      </Card>

      <Drawer
        open={pasteOpen}
        title="Paste a job description"
        description="AI extracts the required skills. The job is private to you."
        onClose={() => setPasteOpen(false)}
        footer={
          <>
            <Button onClick={() => setPasteOpen(false)}>Cancel</Button>
            <Button
              variant="primary"
              type="submit"
              form="cj-paste-form"
              disabled={creating || !draft.title.trim() || draft.description.trim().length < 40}
            >
              {creating ? "Extracting skills…" : "Create and extract skills"}
            </Button>
          </>
        }
      >
        <form id="cj-paste-form" className="ui-form" onSubmit={createCustom}>
          {createError && <Alert tone="danger">{createError}</Alert>}
          <Field label="Job title">
            <input required maxLength={120} value={draft.title} onChange={set("title")} placeholder="e.g. Junior Backend Developer" />
          </Field>
          <div className="ui-form-row">
            <Field label="Company (optional)">
              <input maxLength={120} value={draft.company} onChange={set("company")} />
            </Field>
            <Field label="Location (optional)">
              <input maxLength={120} value={draft.location} onChange={set("location")} />
            </Field>
          </div>
          <Field label="Job description" hint={`At least 40 characters · ${draft.description.length}/20,000`}>
            <textarea required minLength={40} maxLength={20000} rows={12} value={draft.description} onChange={set("description")} />
          </Field>
        </form>
      </Drawer>
    </Page>
  );
}

export default ChooseJob;
