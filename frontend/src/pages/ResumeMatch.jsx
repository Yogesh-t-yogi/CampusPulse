import { useEffect, useState } from "react";
import { FileUp, Route, Save, Target } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Alert, Badge, Button, Card, EmptyState, Field, Page, PageHeader } from "../ui";
import { useToast } from "../ui/toast";

// Resume → skills → save to profile; matches with target roles first; add roles / learn gaps.
function ResumeMatch({ setActivePage }) {
  const toast = useToast();
  const [file, setFile] = useState(null);
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);
  const [saved, setSaved] = useState(false);
  const [targets, setTargets] = useState([]);

  useEffect(() => {
    api
      .get("/api/student/profile")
      .then(({ data }) => setTargets(data.targetRoles.map((role) => role.jobId)))
      .catch(() => setTargets([]));
  }, []);

  const match = async (event) => {
    event.preventDefault();
    if (!file || !consent) return;
    setBusy(true);
    setError("");
    setResult(null);
    setSaved(false);
    const data = new FormData();
    data.append("file", file);
    try {
      const response = await api.post("/api/resume/match", data);
      setResult(response.data);
    } catch (err) {
      setError(errorMessage(err, "Couldn't read skills from this resume. Use a text PDF (not a photo)."));
    } finally {
      setBusy(false);
    }
  };

  const saveSkills = async () => {
    try {
      const { data } = await api.post("/api/student/skills", { skills: result.extractedSkills, source: "RESUME" });
      setSaved(true);
      toast(`${data.added} new skill${data.added === 1 ? "" : "s"} saved to your profile`);
    } catch (err) {
      toast(errorMessage(err, "Couldn't save the skills."), "danger");
    }
  };

  const addTarget = async (jobId) => {
    if (targets.length >= 5) {
      toast("You already have 5 target roles. Remove one in your profile first.", "danger");
      return;
    }
    const next = [...targets, jobId];
    try {
      await api.put("/api/student/target-roles", { jobIds: next });
      setTargets(next);
      toast("Added to your target roles");
    } catch (err) {
      toast(errorMessage(err, "Couldn't add the role."), "danger");
    }
  };

  const matches = result
    ? [...result.matches].sort((a, b) => Number(targets.includes(b.jobId)) - Number(targets.includes(a.jobId)))
    : [];

  return (
    <Page>
      <PageHeader
        eyebrow="My growth"
        title="Resume match"
        description="Read the skills from your resume, keep them in your profile, and see which jobs fit you and what's missing."
      />

      <div className="ui-grid ui-grid-main">
        <Card title="Upload your resume" description="Text-based PDF, up to 5 MB.">
          <form className="ui-form" onSubmit={match}>
            {error && <Alert tone="danger">{error}</Alert>}
            <Field label="Resume (PDF)">
              <input type="file" accept=".pdf,application/pdf" onChange={(e) => setFile(e.target.files?.[0] || null)} />
            </Field>
            <label className="cp-consent">
              <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
              <span>
                I agree that the resume text is sent to the AI provider (Groq) only to find skills. CampusPulse doesn't store
                the resume — only the skills if you choose to save them.
              </span>
            </label>
            <Button variant="primary" icon={FileUp} type="submit" disabled={!file || !consent || busy}>
              {busy ? "Reading resume…" : "Find my skills and matches"}
            </Button>
          </form>
        </Card>

        <Card title="Skills found" description={result ? `${result.extractedSkills.length} skills` : "Upload a resume to see them."}>
          {result && (
            <>
              <div className="ui-chip-list" style={{ marginBottom: 12 }}>
                {result.extractedSkills.map((skill) => (
                  <span key={skill} className="ui-chip">
                    {skill}
                  </span>
                ))}
              </div>
              <Button icon={Save} onClick={saveSkills} disabled={saved || result.extractedSkills.length === 0}>
                {saved ? "Saved to profile" : "Save these skills to my profile"}
              </Button>
            </>
          )}
        </Card>
      </div>

      {result && (
        <Card title="Jobs that fit you" description="Your target roles first, then the best other matches.">
          {matches.length === 0 ? (
            <EmptyState title="No matching jobs" description="None of the catalog jobs share skills with your resume yet." />
          ) : (
            <div className="ui-grid">
              {matches.map((job) => {
                const isTarget = targets.includes(job.jobId);
                return (
                  <section key={job.jobId} className="cp-match">
                    <div className="cp-match-head">
                      <span className="ui-cell-title">
                        <strong>
                          {job.title} {isTarget && <Badge tone="accent">Target role</Badge>}
                        </strong>
                        <small>{[job.company, job.location].filter(Boolean).join(" · ")}</small>
                      </span>
                      <Badge tone={job.matchPercentage >= 70 ? "success" : job.matchPercentage >= 40 ? "warning" : "danger"}>
                        {job.matchPercentage}% match
                      </Badge>
                    </div>
                    <div className="ui-chip-list">
                      {job.matchedSkills.map((skill) => (
                        <Badge key={`m-${skill}`} tone="success">
                          {skill}
                        </Badge>
                      ))}
                      {job.missingSkills.map((skill) => (
                        <Badge key={`x-${skill}`} tone="danger">
                          {skill}
                        </Badge>
                      ))}
                    </div>
                    <div className="ui-row-actions" style={{ justifyContent: "flex-start", marginTop: 8 }}>
                      {!isTarget && (
                        <Button variant="ghost" icon={Target} onClick={() => addTarget(job.jobId)}>
                          Add as target role
                        </Button>
                      )}
                      {isTarget && job.missingSkills.length > 0 && (
                        <Button variant="ghost" icon={Route} onClick={() => setActivePage("Learning plan")}>
                          Learn missing skills
                        </Button>
                      )}
                    </div>
                  </section>
                );
              })}
            </div>
          )}
        </Card>
      )}
    </Page>
  );
}

export default ResumeMatch;
