import { useState } from "react";
import { BookOpen, CheckCircle2, FileUp, Pencil, RefreshCw, Trash2 } from "lucide-react";
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

// Index of the user's syllabi with open / use / rename / re-process / delete.
function Syllabi({ syllabi, loading, selectedSyllabusId, onSelect, onChanged, setActivePage }) {
  const toast = useToast();
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [renaming, setRenaming] = useState(null);
  const [newName, setNewName] = useState("");
  const [deleting, setDeleting] = useState(null);
  const [busy, setBusy] = useState(false);

  const rows = syllabi.filter((item) => {
    const text = `${item.name || ""} ${item.fileName || ""}`.toLowerCase();
    const status = statusOf(item);
    const statusMatch =
      statusFilter === "ALL" ||
      status === statusFilter ||
      (statusFilter === "READY" && status === "READY_WITH_WARNINGS");
    return statusMatch && text.includes(query.trim().toLowerCase());
  });

  const open = (item) => {
    onSelect(item.id);
    setActivePage("Curriculum");
  };

  const saveName = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      await api.put(`/api/syllabus/${renaming.id}`, { name: newName });
      toast("Syllabus renamed");
      setRenaming(null);
      onChanged();
    } catch (err) {
      toast(errorMessage(err, "Couldn't rename."), "danger");
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
          <small>{item.fileName}</small>
        </span>
      ),
    },
    { key: "status", label: "Status", render: (item) => <Badge status={statusOf(item)} /> },
    { key: "subjectCount", label: "Subjects", render: (item) => Number(item.subjectCount || 0) },
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
          <IconButton
            icon={Pencil}
            label="Rename"
            onClick={() => {
              setRenaming(item);
              setNewName(item.name || "");
            }}
          />
          <IconButton
            icon={RefreshCw}
            label="Re-process with AI"
            onClick={() => reprocess(item)}
            disabled={statusOf(item) === "PROCESSING"}
          />
          <IconButton icon={Trash2} label="Delete" variant="ghost-danger" onClick={() => setDeleting(item)} />
        </div>
      ),
    },
  ];

  return (
    <Page>
      <PageHeader
        eyebrow="Curriculum"
        title="Syllabi"
        description="Every syllabus you uploaded. Open one to review its subjects, units and topics."
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
              <SearchInput value={query} onChange={setQuery} placeholder="Search syllabi" />
              <select
                className="ui-select"
                style={{ width: "auto" }}
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value)}
                aria-label="Filter by status"
              >
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
              title="Upload your first syllabus"
              description="A PDF is enough. Scanned pages are read with OCR."
              action={
                <Button variant="primary" icon={FileUp} onClick={() => setActivePage("Upload Syllabus")}>
                  Upload syllabus
                </Button>
              }
            />
          ) : (
            <EmptyState title="No syllabi match" description="Try another search or status." />
          )
        }
      />

      <Modal
        open={Boolean(renaming)}
        title="Rename syllabus"
        onClose={() => setRenaming(null)}
        size="sm"
        footer={
          <>
            <Button onClick={() => setRenaming(null)}>Cancel</Button>
            <Button variant="primary" type="submit" form="rename-syllabus" disabled={busy || !newName.trim()}>
              Save
            </Button>
          </>
        }
      >
        <form id="rename-syllabus" className="ui-form" onSubmit={saveName}>
          <Field label="Name">
            <input value={newName} onChange={(event) => setNewName(event.target.value)} maxLength={255} autoFocus />
          </Field>
        </form>
      </Modal>

      <ConfirmDialog
        open={Boolean(deleting)}
        title="Delete syllabus?"
        message={`"${deleting?.name}" and its curriculum, topic index and saved reports will be deleted. This can't be undone.`}
        busy={busy}
        onConfirm={remove}
        onClose={() => setDeleting(null)}
      />
    </Page>
  );
}

export default Syllabi;
