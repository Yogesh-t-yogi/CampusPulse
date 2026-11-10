import { useCallback, useEffect, useState } from "react";
import { Pencil, Plus, Sparkles, Trash2 } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Alert, Badge, Button, Card, EmptyState, Field, IconButton } from "../ui";
import { useToast } from "../ui/toast";

const BLOOM = ["Remember", "Understand", "Apply", "Analyze", "Evaluate", "Create"];
const LEVEL_LABEL = { 1: "1 low", 2: "2 medium", 3: "3 high" };

// Course outcomes per subject + CO–PO mapping grid (NBA). Edit only when canManage.
function CourseOutcomes({ syllabusId, canManage }) {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(null); // {subjectId, id?, code, description, bloomLevel}
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const response = await api.get(`/api/outcomes/syllabus/${syllabusId}`);
      setData(response.data);
    } catch (err) {
      toast(errorMessage(err, "Couldn't load course outcomes."), "danger");
    } finally {
      setLoading(false);
    }
  }, [syllabusId, toast]);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const extract = async (replace) => {
    if (replace && !window.confirm("Replace all course outcomes and their mappings with a new AI extraction?")) return;
    setBusy(true);
    try {
      const response = await api.post(`/api/outcomes/syllabus/${syllabusId}/extract`, null, { params: { replace } });
      toast(`${response.data.created} course outcomes extracted${response.data.mappingSuggested ? " with suggested PO levels" : ""}`);
      await load();
    } catch (err) {
      toast(errorMessage(err, "Couldn't extract course outcomes."), "danger");
    } finally {
      setBusy(false);
    }
  };

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    const body = { code: editing.code, description: editing.description, bloomLevel: editing.bloomLevel || "" };
    try {
      if (editing.id) await api.put(`/api/outcomes/cos/${editing.id}`, body);
      else await api.post(`/api/outcomes/subjects/${editing.subjectId}`, body);
      setEditing(null);
      await load();
    } catch (err) {
      setError(errorMessage(err, "Couldn't save."));
    } finally {
      setBusy(false);
    }
  };

  const remove = async (co) => {
    if (!window.confirm(`Delete ${co.code}?`)) return;
    try {
      await api.delete(`/api/outcomes/cos/${co.id}`);
      await load();
    } catch (err) {
      toast(errorMessage(err, "Couldn't delete."), "danger");
    }
  };

  const setLevel = async (co, poId, level) => {
    const mapping = { ...co.mapping, [poId]: Number(level) };
    try {
      await api.put(`/api/outcomes/cos/${co.id}/mapping`, mapping);
      setData((current) => ({
        ...current,
        subjects: current.subjects.map((subject) => ({
          ...subject,
          outcomes: subject.outcomes.map((item) => (item.id === co.id ? { ...item, mapping } : item)),
        })),
      }));
    } catch (err) {
      toast(errorMessage(err, "Couldn't save the mapping."), "danger");
    }
  };

  if (loading || !data) return null;

  const pos = data.programOutcomes || [];
  const total = data.subjects.reduce((sum, subject) => sum + subject.outcomes.length, 0);

  return (
    <Card
      title="Course outcomes (CO) and CO–PO mapping"
      description="NBA: what students can do after each subject, and how strongly each CO supports each program outcome (1 low, 2 medium, 3 high)."
      actions={
        canManage && (
          <Button icon={Sparkles} onClick={() => extract(total > 0)} disabled={busy}>
            {busy ? "Working…" : total > 0 ? "Re-extract with AI" : "Extract with AI"}
          </Button>
        )
      }
    >
      <div className="ui-form">
        {!data.programId && (
          <Alert tone="info">
            This syllabus has no program, so COs can't be mapped to POs. Set it in Syllabi → Edit details.
          </Alert>
        )}
        {data.programId && pos.length === 0 && (
          <Alert tone="info">
            The program has no POs yet. An HOD or admin adds them in Organisation → Programs → Outcomes.
          </Alert>
        )}
        {total === 0 && (
          <EmptyState
            title="No course outcomes yet"
            description={canManage ? "Extract them from the syllabus with AI, or add them by hand." : "The syllabus owner hasn't added them yet."}
          />
        )}

        {editing && (
          <form className="ui-form cp-edit-form" onSubmit={save}>
            {error && <Alert tone="danger">{error}</Alert>}
            <div className="ui-form-row">
              <Field label="Code">
                <input value={editing.code} onChange={(e) => setEditing({ ...editing, code: e.target.value })} required maxLength={20} />
              </Field>
              <Field label="Bloom level">
                <select value={editing.bloomLevel || ""} onChange={(e) => setEditing({ ...editing, bloomLevel: e.target.value })}>
                  <option value="">Not set</option>
                  {BLOOM.map((level) => (
                    <option key={level} value={level}>
                      {level}
                    </option>
                  ))}
                </select>
              </Field>
            </div>
            <Field label="Outcome statement" hint='Start with an action verb, e.g. "Design a relational schema for…"'>
              <textarea value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} required rows={2} />
            </Field>
            <div className="cp-edit-buttons">
              <Button variant="primary" type="submit" disabled={busy}>
                Save
              </Button>
              <Button onClick={() => setEditing(null)}>Cancel</Button>
            </div>
          </form>
        )}

        {data.subjects.map((subject) => (
          <section key={subject.id} className="cp-co-subject">
            <div className="cp-co-subject-head">
              <strong>
                {subject.name}
                {subject.code ? <span className="ui-muted"> · {subject.code}</span> : null}
              </strong>
              {canManage && (
                <Button
                  variant="ghost"
                  icon={Plus}
                  onClick={() => setEditing({ subjectId: subject.id, code: `CO${subject.outcomes.length + 1}`, description: "", bloomLevel: "" })}
                >
                  Add CO
                </Button>
              )}
            </div>

            {subject.outcomes.length > 0 && (
              <div className="ui-table-scroll">
                <table className="ui-table cp-co-table">
                  <thead>
                    <tr>
                      <th>CO</th>
                      <th>Outcome</th>
                      {pos.map((po) => (
                        <th key={po.id} title={po.title} className="cp-co-po">
                          {po.code}
                        </th>
                      ))}
                      {canManage && <th aria-label="Actions" />}
                    </tr>
                  </thead>
                  <tbody>
                    {subject.outcomes.map((co) => (
                      <tr key={co.id}>
                        <td>
                          <Badge tone="accent">{co.code}</Badge>
                        </td>
                        <td>
                          <span className="ui-cell-title">
                            <span>{co.description}</span>
                            {co.bloomLevel && <small>{co.bloomLevel}</small>}
                          </span>
                        </td>
                        {pos.map((po) => {
                          const level = co.mapping?.[String(po.id)] || 0;
                          return (
                            <td key={po.id} className="cp-co-po">
                              {canManage ? (
                                <select
                                  className={`cp-co-level cp-co-level-${level}`}
                                  value={level}
                                  aria-label={`${co.code} to ${po.code}`}
                                  onChange={(e) => setLevel(co, String(po.id), e.target.value)}
                                >
                                  <option value={0}>–</option>
                                  {[1, 2, 3].map((value) => (
                                    <option key={value} value={value}>
                                      {LEVEL_LABEL[value]}
                                    </option>
                                  ))}
                                </select>
                              ) : (
                                <span className={`cp-co-level cp-co-level-${level}`}>{level || "–"}</span>
                              )}
                            </td>
                          );
                        })}
                        {canManage && (
                          <td>
                            <span className="ui-row-actions">
                              <IconButton icon={Pencil} label={`Edit ${co.code}`} onClick={() => setEditing({ ...co, subjectId: subject.id })} />
                              <IconButton icon={Trash2} label={`Delete ${co.code}`} variant="ghost-danger" onClick={() => remove(co)} />
                            </span>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        ))}
      </div>
    </Card>
  );
}

export default CourseOutcomes;
