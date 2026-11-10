import { useState } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import api, { errorMessage } from "../apiClient";

// Small inline editors used by the Curriculum page in edit mode.
// Each one calls the API, then onChanged() so the page reloads the tree.

function useSave(onChanged) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const run = async (request, after) => {
    setBusy(true);
    setError("");
    try {
      await request();
      after?.();
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Couldn't save. Try again."));
    } finally {
      setBusy(false);
    }
  };

  return { busy, error, run };
}

function ErrorText({ error }) {
  return error ? (
    <p className="cp-edit-error" role="alert">
      {error}
    </p>
  ) : null;
}

export function TopicEditor({ syllabusId, topic, units, onChanged }) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(topic.name || "");
  const [description, setDescription] = useState(topic.description || "");
  const [unitId, setUnitId] = useState("");
  const { busy, error, run } = useSave(onChanged);

  const save = (event) => {
    event.preventDefault();
    run(
      () =>
        api.put(`/api/syllabus/${syllabusId}/topics/${topic.id}`, {
          name,
          description,
          unitId: unitId || null,
        }),
      () => setEditing(false)
    );
  };

  const remove = () => {
    if (!window.confirm(`Delete topic "${topic.name}"?`)) return;
    run(() => api.delete(`/api/syllabus/${syllabusId}/topics/${topic.id}`));
  };

  if (!editing) {
    return (
      <div className="cp-edit-actions">
        <button type="button" className="cp-icon-button" onClick={() => setEditing(true)} aria-label={`Edit topic ${topic.name}`}>
          <Pencil size={15} />
        </button>
        <button type="button" className="cp-icon-button cp-button-danger" onClick={remove} disabled={busy} aria-label={`Delete topic ${topic.name}`}>
          <Trash2 size={15} />
        </button>
        <ErrorText error={error} />
      </div>
    );
  }

  return (
    <form className="cp-edit-form" onSubmit={save}>
      <label>
        Topic name
        <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={255} />
      </label>
      <label>
        Description
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} />
      </label>
      <label>
        Move to unit
        <select value={unitId} onChange={(e) => setUnitId(e.target.value)}>
          <option value="">Keep in this unit</option>
          {units.map((unit) => (
            <option key={unit.id} value={unit.id}>
              {unit.label}
            </option>
          ))}
        </select>
      </label>
      <ErrorText error={error} />
      <div className="cp-edit-buttons">
        <button type="submit" className="cp-button cp-button-primary" disabled={busy}>
          {busy ? "Saving…" : "Save topic"}
        </button>
        <button type="button" className="cp-button" onClick={() => setEditing(false)}>
          Cancel
        </button>
      </div>
    </form>
  );
}

export function UnitEditor({ syllabusId, unit, onChanged }) {
  const [editing, setEditing] = useState(false);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState(unit.name || "");
  const [unitNumber, setUnitNumber] = useState(unit.unitNumber ?? "");
  const [topicName, setTopicName] = useState("");
  const [topicDescription, setTopicDescription] = useState("");
  const { busy, error, run } = useSave(onChanged);

  const saveUnit = (event) => {
    event.preventDefault();
    run(
      () => api.put(`/api/syllabus/${syllabusId}/units/${unit.id}`, { name, unitNumber }),
      () => setEditing(false)
    );
  };

  const addTopic = (event) => {
    event.preventDefault();
    run(
      () =>
        api.post(`/api/syllabus/${syllabusId}/units/${unit.id}/topics`, {
          name: topicName,
          description: topicDescription,
        }),
      () => {
        setAdding(false);
        setTopicName("");
        setTopicDescription("");
      }
    );
  };

  const remove = () => {
    const count = (unit.topics || []).length;
    if (!window.confirm(`Delete this unit and its ${count} topics?`)) return;
    run(() => api.delete(`/api/syllabus/${syllabusId}/units/${unit.id}`));
  };

  return (
    <div className="cp-edit-bar">
      {!editing && !adding && (
        <div className="cp-edit-actions">
          <button type="button" className="cp-button" onClick={() => setAdding(true)}>
            <Plus size={15} /> Add topic
          </button>
          <button type="button" className="cp-button" onClick={() => setEditing(true)}>
            <Pencil size={15} /> Rename unit
          </button>
          <button type="button" className="cp-button cp-button-danger" onClick={remove} disabled={busy}>
            <Trash2 size={15} /> Delete unit
          </button>
        </div>
      )}

      {editing && (
        <form className="cp-edit-form cp-edit-form-row" onSubmit={saveUnit}>
          <label className="cp-edit-narrow">
            Unit no.
            <input type="number" min="1" value={unitNumber} onChange={(e) => setUnitNumber(e.target.value)} />
          </label>
          <label>
            Unit name
            <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={255} />
          </label>
          <div className="cp-edit-buttons">
            <button type="submit" className="cp-button cp-button-primary" disabled={busy}>
              Save
            </button>
            <button type="button" className="cp-button" onClick={() => setEditing(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}

      {adding && (
        <form className="cp-edit-form" onSubmit={addTopic}>
          <label>
            New topic name
            <input value={topicName} onChange={(e) => setTopicName(e.target.value)} required maxLength={255} autoFocus />
          </label>
          <label>
            Description
            <textarea value={topicDescription} onChange={(e) => setTopicDescription(e.target.value)} rows={2} />
          </label>
          <div className="cp-edit-buttons">
            <button type="submit" className="cp-button cp-button-primary" disabled={busy}>
              {busy ? "Adding…" : "Add topic"}
            </button>
            <button type="button" className="cp-button" onClick={() => setAdding(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}

      <ErrorText error={error} />
    </div>
  );
}

export function SubjectEditor({ syllabusId, subject, onChanged }) {
  const [editing, setEditing] = useState(false);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState(subject.name || "");
  const [code, setCode] = useState(subject.code || "");
  const [unitName, setUnitName] = useState("");
  const { busy, error, run } = useSave(onChanged);

  const save = (event) => {
    event.preventDefault();
    run(
      () => api.put(`/api/syllabus/${syllabusId}/subjects/${subject.id}`, { name, code }),
      () => setEditing(false)
    );
  };

  const addUnit = (event) => {
    event.preventDefault();
    run(
      () => api.post(`/api/syllabus/${syllabusId}/subjects/${subject.id}/units`, { name: unitName }),
      () => {
        setAdding(false);
        setUnitName("");
      }
    );
  };

  return (
    <div className="cp-edit-bar">
      {!editing && !adding && (
        <div className="cp-edit-actions">
          <button type="button" className="cp-button" onClick={() => setEditing(true)}>
            <Pencil size={15} /> Edit subject
          </button>
          <button type="button" className="cp-button" onClick={() => setAdding(true)}>
            <Plus size={15} /> Add unit
          </button>
        </div>
      )}

      {editing && (
        <form className="cp-edit-form cp-edit-form-row" onSubmit={save}>
          <label>
            Subject name
            <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={255} />
          </label>
          <label className="cp-edit-narrow">
            Code
            <input value={code} onChange={(e) => setCode(e.target.value)} maxLength={50} />
          </label>
          <div className="cp-edit-buttons">
            <button type="submit" className="cp-button cp-button-primary" disabled={busy}>
              Save
            </button>
            <button type="button" className="cp-button" onClick={() => setEditing(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}

      {adding && (
        <form className="cp-edit-form cp-edit-form-row" onSubmit={addUnit}>
          <label>
            New unit name
            <input value={unitName} onChange={(e) => setUnitName(e.target.value)} required maxLength={255} autoFocus />
          </label>
          <div className="cp-edit-buttons">
            <button type="submit" className="cp-button cp-button-primary" disabled={busy}>
              Add unit
            </button>
            <button type="button" className="cp-button" onClick={() => setAdding(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}

      <ErrorText error={error} />
    </div>
  );
}
