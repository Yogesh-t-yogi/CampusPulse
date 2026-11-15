import { useCallback, useEffect, useState } from "react";
import { Pencil, Plus, Sparkles, Trash2 } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Alert, Badge, Button, Drawer, EmptyState, Field, IconButton } from "../ui";
import { useToast } from "../ui/toast";

const EMPTY = { kind: "PSO", code: "", title: "", description: "" };

// Program outcomes (NBA PO1–PO12 + PSOs) of one program, in a drawer.
function ProgramOutcomes({ program, canEdit, onClose }) {
  const toast = useToast();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState(null); // null | {id?, ...}
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!program) return;
    try {
      const { data } = await api.get(`/api/outcomes/programs/${program.id}`);
      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      toast(errorMessage(err, "Couldn't load outcomes."), "danger");
    } finally {
      setLoading(false);
    }
  }, [program, toast]);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const addNba = async () => {
    setBusy(true);
    try {
      const { data } = await api.post(`/api/outcomes/programs/${program.id}/nba`);
      toast(data.added ? `${data.added} NBA program outcomes added` : "All NBA outcomes are already there");
      await load();
    } catch (err) {
      toast(errorMessage(err, "Couldn't add NBA outcomes."), "danger");
    } finally {
      setBusy(false);
    }
  };

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (form.id) await api.put(`/api/outcomes/programs/${program.id}/${form.id}`, form);
      else await api.post(`/api/outcomes/programs/${program.id}`, form);
      setForm(null);
      await load();
    } catch (err) {
      setError(errorMessage(err, "Couldn't save."));
    } finally {
      setBusy(false);
    }
  };

  const remove = async (item) => {
    if (!window.confirm(`Delete ${item.code}? Its CO mappings are removed too.`)) return;
    try {
      await api.delete(`/api/outcomes/programs/${program.id}/${item.id}`);
      await load();
    } catch (err) {
      toast(errorMessage(err, "Couldn't delete."), "danger");
    }
  };

  return (
    <Drawer
      open={Boolean(program)}
      title={`Outcomes · ${program?.name || ""}`}
      description="NBA program outcomes (PO1–PO12) and program-specific outcomes (PSO)."
      onClose={onClose}
      footer={<Button onClick={onClose}>Close</Button>}
    >
      <div className="ui-form">
        {canEdit && (
          <div className="ui-page-header-actions">
            <Button icon={Sparkles} onClick={addNba} disabled={busy}>
              Add NBA POs
            </Button>
            <Button icon={Plus} onClick={() => setForm({ ...EMPTY, code: `PSO${items.filter((i) => i.kind === "PSO").length + 1}` })}>
              Add PSO
            </Button>
          </div>
        )}

        {form && (
          <form className="ui-form cp-edit-form" onSubmit={save}>
            {error && <Alert tone="danger">{error}</Alert>}
            <div className="ui-form-row">
              <Field label="Type">
                <select value={form.kind} onChange={(e) => setForm({ ...form, kind: e.target.value })}>
                  <option value="PO">PO</option>
                  <option value="PSO">PSO</option>
                </select>
              </Field>
              <Field label="Code">
                <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} required maxLength={20} />
              </Field>
            </div>
            <Field label="Title">
              <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
            </Field>
            <Field label="Description">
              <textarea value={form.description || ""} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={3} />
            </Field>
            <div className="cp-edit-buttons">
              <Button variant="primary" type="submit" disabled={busy}>
                Save
              </Button>
              <Button onClick={() => setForm(null)}>Cancel</Button>
            </div>
          </form>
        )}

        {!loading && items.length === 0 ? (
          <EmptyState title="No outcomes yet" description={canEdit ? "Add the 12 NBA program outcomes to start." : "Ask your HOD or admin to add them."} />
        ) : (
          <ul className="ui-list">
            {items.map((item) => (
              <li key={item.id}>
                <span className="ui-cell-title">
                  <strong>
                    <Badge tone={item.kind === "PSO" ? "info" : "accent"}>{item.code}</Badge> {item.title}
                  </strong>
                  {item.description && <small>{item.description}</small>}
                </span>
                {canEdit && (
                  <span className="ui-row-actions">
                    <IconButton icon={Pencil} label={`Edit ${item.code}`} onClick={() => setForm({ ...item })} />
                    <IconButton icon={Trash2} label={`Delete ${item.code}`} variant="ghost-danger" onClick={() => remove(item)} />
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Drawer>
  );
}

export default ProgramOutcomes;
