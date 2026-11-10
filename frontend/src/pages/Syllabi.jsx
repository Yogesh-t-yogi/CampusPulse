import { useEffect, useState } from "react";
import { BookOpen, CheckCircle2, FileUp, Lock, Pencil, RefreshCw, Trash2, Users as UsersIcon } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import {
  Badge,
  Button,
  ConfirmDialog,
  DataTable,
  EmptyState,
  Field,
  IconButton,
  Modal,
  Page,
  PageHeader,
  SearchInput,
} from "../ui";
import { useToast } from "../ui/toast";

const STATUS_FILTERS = [
  ["ALL", "All statuses"],
  ["READY", "Ready"],
  ["PROCESSING", "Processing"],
  ["FAILED", "Failed"],
  ["NOT_PROCESSED", "Not processed"],
];

function statusOf(item) {
  return item.processingStatus || (Number(item.topicCount) > 0 ? "READY" : "NOT_PROCESSED");
}

// Syllabi the user can read: own + shared with their department / program.
// Change actions only where item.canManage (owner, HOD of the department, college admin).
function Syllabi({ syllabi, loading, selectedSyllabusId, onSelect, onChanged, setActivePage, currentUser }) {
  const toast = useToast();
  const isStudent = currentUser?.role === "STUDENT";
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [ownerFilter, setOwnerFilter] = useState("ALL");
  const [programFilter, setProgramFilter] = useState("ALL");
  const [programs, setPrograms] = useState([]);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({ name: "", programId: "", semester: "", visibility: "DEPARTMENT" });
  const [deleting, setDeleting] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api
      .get("/api/org/programs")
      .then(({ data }) => setPrograms(Array.isArray(data) ? data : []))
      .catch(() => setPrograms([]));
  }, []);

  const rows = syllabi.filter((item) => {
    const text = `${item.name || ""} ${item.fileName || ""} ${item.ownerName || ""}`.toLowerCase();
    const status = statusOf(item);
    const statusMatch =
      statusFilter === "ALL" ||
      status === statusFilter ||
      (statusFilter === "READY" && status === "READY_WITH_WARNINGS");
    const ownerMatch = ownerFilter === "ALL" || (ownerFilter === "MINE" ? item.mine : !item.mine);
    const programMatch =
      programFilter === "ALL" || String(item.programId || "") === programFilter;
    return statusMatch && ownerMatch && programMatch && text.includes(query.trim().toLowerCase());
  });

  const open = (item) => {
    onSelect(item.id);
    setActivePage("Curriculum");
  };

  const openEditor = (item) => {
    setEditing(item);
    setForm({
      name: item.name || "",
      programId: item.programId ? String(item.programId) : "",
      semester: item.semester ? String(item.semester) : "",
      visibility: item.visibility || "PRIVATE",
    });
  };

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      await api.put(`/api/syllabus/${editing.id}`, {
        name: form.name,
        programId: form.programId || null,
        semester: form.semester || null,
        visibility: form.visibility,
      });
      toast("Syllabus updated");
      setEditing(null);
      onChanged();
    } catch (err) {
      toast(errorMessage(err, "Couldn't save."), "danger");
    } finally {
      setBusy(false);
    }
  };

  const reprocess = async (item) => {
    try {
      await api.post(`/api/ai/syllabus/${item.id}/save`);
      toast("Processing started. Hand edits will be replaced.");
      onChanged();
    } catch (err) {
      toast(errorMessage(err, "Couldn't start processing."), "danger");
    }
  };

  const remove = async () => {
    setBusy(true);
    try {
      await api.delete(`/api/syllabus/${deleting.id}`);
      if (String(deleting.id) === String(selectedSyllabusId)) onSelect("");
      toast("Syllabus deleted");
      setDeleting(null);
      onChanged();
    } catch (err) {
      toast(errorMessage(err, "Couldn't delete."), "danger");
    } finally {
      setBusy(false);
    }
  };

  const editProgram = programs.find((item) => String(item.id) === form.programId);

  const columns = [
    {
      key: "name",
      label: "Syllabus",
      render: (item) => (
        <span className="ui-cell-title">
          <strong>
            {item.name || item.fileName}
            {String(item.id) === String(selectedSyllabusId) && (
              <>
                {" "}
                <Badge tone="accent">In use</Badge>
              </>
            )}
          </strong>
          <small>{item.mine ? "Uploaded by you" : `By ${item.ownerName || "someone"}`}</small>
        </span>
      ),
    },
    {
      key: "program",
      label: "Program · Semester",
      render: (item) => (
        <span className="ui-cell-title">
          <strong>{item.programName || "No program"}</strong>
          <small>{item.semester ? `Semester ${item.semester}` : "Semester not set"}</small>
        </span>
      ),
    },
    {
      key: "visibility",
      label: "Shared",
      render: (item) =>
        item.visibility === "DEPARTMENT" ? (
          <Badge tone="info">
            <UsersIcon size={12} aria-hidden="true" /> Department
          </Badge>
        ) : (
          <Badge tone="neutral">
            <Lock size={12} aria-hidden="true" /> Private
          </Badge>
        ),
    },
    { key: "status", label: "Status", render: (item) => <Badge status={statusOf(item)} /> },
    { key: "topicCount", label: "Topics", render: (item) => Number(item.topicCount || 0) },
    {
      key: "createdAt",
      label: "Uploaded",
      render: (item) => (item.createdAt ? new Date(item.createdAt).toLocaleDateString() : "—"),
    },
    {
      key: "actions",
      label: "",
      width: "1%",
      render: (item) => (
        <div className="ui-row-actions">
          <Button variant="ghost" icon={BookOpen} onClick={() => open(item)}>
            Open
          </Button>
          <IconButton
            icon={CheckCircle2}
            label="Use for analysis"
            onClick={() => {
              onSelect(item.id);
              toast(`"${item.name}" is now the active syllabus`);
            }}
          />
          {item.canManage && (
            <>
              <IconButton icon={Pencil} label="Edit details" onClick={() => openEditor(item)} />
              <IconButton
                icon={RefreshCw}
                label="Re-process with AI"
                onClick={() => reprocess(item)}
                disabled={statusOf(item) === "PROCESSING"}
              />
              <IconButton icon={Trash2} label="Delete" variant="ghost-danger" onClick={() => setDeleting(item)} />
            </>
          )}
        </div>
      ),
    },
  ];

  return (
    <Page>
      <PageHeader
        eyebrow="Curriculum"
        title="Syllabi"
        description={
          isStudent
            ? "Your own syllabi and the ones your department shares for your program."
            : "Your syllabi and the ones shared in your department. Open one to review its curriculum."
        }
        actions={
          <Button variant="primary" icon={FileUp} onClick={() => setActivePage("Upload Syllabus")}>
            Upload syllabus
          </Button>
        }
      />

      <DataTable
        loading={loading && syllabi.length === 0}
        columns={columns}
        rows={rows}
        toolbar={
          <>
            <div className="ui-table-toolbar-group">
              <SearchInput value={query} onChange={setQuery} placeholder="Search syllabi or owner" />
              <select className="ui-select" style={{ width: "auto" }} value={ownerFilter} onChange={(e) => setOwnerFilter(e.target.value)} aria-label="Filter by owner">
                <option value="ALL">All</option>
                <option value="MINE">Mine</option>
                <option value="SHARED">Shared with me</option>
              </select>
              <select className="ui-select" style={{ width: "auto" }} value={programFilter} onChange={(e) => setProgramFilter(e.target.value)} aria-label="Filter by program">
                <option value="ALL">All programs</option>
                <option value="">No program</option>
                {programs.map((item) => (
                  <option key={item.id} value={String(item.id)}>
                    {item.name}
                  </option>
                ))}
              </select>
              <select className="ui-select" style={{ width: "auto" }} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} aria-label="Filter by status">
                {STATUS_FILTERS.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <span className="ui-muted">
              {rows.length} of {syllabi.length}
            </span>
          </>
        }
        empty={
          syllabi.length === 0 ? (
            <EmptyState
              icon={BookOpen}
              title="No syllabi yet"
              description="Upload a syllabus PDF, or ask your department to share one."
              action={
                <Button variant="primary" icon={FileUp} onClick={() => setActivePage("Upload Syllabus")}>
                  Upload syllabus
                </Button>
              }
            />
          ) : (
            <EmptyState title="No syllabi match" description="Try another search or filter." />
          )
        }
      />

      <Modal
        open={Boolean(editing)}
        title="Edit syllabus details"
        onClose={() => setEditing(null)}
        footer={
          <>
            <Button onClick={() => setEditing(null)}>Cancel</Button>
            <Button variant="primary" type="submit" form="syllabus-details" disabled={busy || !form.name.trim()}>
              Save
            </Button>
          </>
        }
      >
        <form id="syllabus-details" className="ui-form" onSubmit={save}>
          <Field label="Name">
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={255} autoFocus />
          </Field>
          <div className="ui-form-row">
            <Field label="Program">
              <select value={form.programId} onChange={(e) => setForm({ ...form, programId: e.target.value, semester: "" })}>
                <option value="">No program</option>
                {programs.map((item) => (
                  <option key={item.id} value={String(item.id)}>
                    {item.name}
                    {item.regulation ? ` (${item.regulation})` : ""}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Semester">
              <select value={form.semester} onChange={(e) => setForm({ ...form, semester: e.target.value })}>
                <option value="">Not set</option>
                {Array.from({ length: editProgram?.durationSemesters || 8 }, (_, index) => index + 1).map((value) => (
                  <option key={value} value={String(value)}>
                    Semester {value}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <Field
            label="Who can see it"
            hint="Department: HOD and faculty of the department, and students of the program."
          >
            <select value={form.visibility} onChange={(e) => setForm({ ...form, visibility: e.target.value })}>
              <option value="DEPARTMENT">Department (shared)</option>
              <option value="PRIVATE">Private (owner and admins)</option>
            </select>
          </Field>
        </form>
      </Modal>

      <ConfirmDialog
        open={Boolean(deleting)}
        title="Delete syllabus?"
        message={`"${deleting?.name}" and its curriculum, topic index and saved reports will be deleted for everyone it is shared with. This can't be undone.`}
        busy={busy}
        onConfirm={remove}
        onClose={() => setDeleting(null)}
      />
    </Page>
  );
}

export default Syllabi;
