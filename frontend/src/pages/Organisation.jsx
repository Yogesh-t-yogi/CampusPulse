import { useCallback, useEffect, useState } from "react";
import { Building2, GraduationCap, Layers, Pencil, Plus, Trash2 } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { can } from "../permissions";
import {
  Alert,
  Button,
  ConfirmDialog,
  DataTable,
  Drawer,
  EmptyState,
  Field,
  IconButton,
  Page,
  PageHeader,
  SearchInput,
} from "../ui";
import { useToast } from "../ui/toast";

// One CRUD section: table + drawer form + delete confirm.
// fields: [{key, label, required?, type?, options?, hint?, placeholder?}]
function CrudSection({ title, icon, endpoint, query, columns, fields, canEdit, emptyText, makeBody, onChanged }) {
  const toast = useToast();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({});
  const [error, setError] = useState("");
  const [deleting, setDeleting] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const { data } = await api.get(endpoint, { params: query });
      setRows(Array.isArray(data) ? data : []);
    } catch (err) {
      toast(errorMessage(err, `Couldn't load ${title.toLowerCase()}.`), "danger");
    } finally {
      setLoading(false);
    }
  }, [endpoint, query, title, toast]);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const open = (row) => {
    setError("");
    setEditing(row || "new");
    setForm(Object.fromEntries(fields.map((field) => [field.key, row?.[field.key] ?? field.default ?? ""])));
  };

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    const body = makeBody ? makeBody(form) : form;
    try {
      if (editing === "new") await api.post(endpoint, body);
      else await api.put(`${endpoint}/${editing.id}`, body);
      setEditing(null);
      toast(editing === "new" ? `${title.replace(/s$/, "")} added` : "Saved");
      await load();
      onChanged?.();
    } catch (err) {
      setError(errorMessage(err, "Couldn't save."));
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setBusy(true);
    try {
      await api.delete(`${endpoint}/${deleting.id}`);
      setDeleting(null);
      toast("Deleted");
      await load();
      onChanged?.();
    } catch (err) {
      toast(errorMessage(err, "Couldn't delete."), "danger");
    } finally {
      setBusy(false);
    }
  };

  const visible = rows.filter((row) =>
    `${row.name} ${row.code}`.toLowerCase().includes(search.trim().toLowerCase())
  );

  const allColumns = canEdit
    ? [
        ...columns,
        {
          key: "actions",
          label: "",
          width: "1%",
          render: (row) => (
            <div className="ui-row-actions">
              <IconButton icon={Pencil} label={`Edit ${row.name}`} onClick={() => open(row)} />
              <IconButton icon={Trash2} label={`Delete ${row.name}`} variant="ghost-danger" onClick={() => setDeleting(row)} />
            </div>
          ),
        },
      ]
    : columns;

  return (
    <>
      <DataTable
        loading={loading}
        columns={allColumns}
        rows={visible}
        toolbar={
          <>
            <SearchInput value={search} onChange={setSearch} placeholder={`Search ${title.toLowerCase()}`} />
            {canEdit && (
              <Button variant="primary" icon={Plus} onClick={() => open(null)}>
                Add {title.replace(/s$/, "").toLowerCase()}
              </Button>
            )}
          </>
        }
        empty={<EmptyState icon={icon} title={rows.length ? "Nothing matches" : `No ${title.toLowerCase()} yet`} description={rows.length ? "Try another search." : emptyText} />}
      />

      <Drawer
        open={Boolean(editing)}
        title={`${editing === "new" ? "Add" : "Edit"} ${title.replace(/s$/, "").toLowerCase()}`}
        onClose={() => setEditing(null)}
        footer={
          <>
            <Button onClick={() => setEditing(null)}>Cancel</Button>
            <Button variant="primary" type="submit" form={`form-${title}`} disabled={busy}>
              {busy ? "Saving…" : "Save"}
            </Button>
          </>
        }
      >
        <form id={`form-${title}`} className="ui-form" onSubmit={save}>
          {error && <Alert tone="danger">{error}</Alert>}
          {fields.map((field) => (
            <Field key={field.key} label={field.label} hint={field.hint}>
              {field.options ? (
                <select
                  value={form[field.key] ?? ""}
                  required={field.required}
                  onChange={(event) => setForm({ ...form, [field.key]: event.target.value })}
                >
                  <option value="">Choose…</option>
                  {field.options.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  type={field.type || "text"}
                  value={form[field.key] ?? ""}
                  required={field.required}
                  placeholder={field.placeholder}
                  onChange={(event) => setForm({ ...form, [field.key]: event.target.value })}
                />
              )}
            </Field>
          ))}
        </form>
      </Drawer>

      <ConfirmDialog
        open={Boolean(deleting)}
        title={`Delete ${deleting?.name}?`}
        message="This can't be undone. Departments delete their programs; people and syllabi stay in the college."
        busy={busy}
        onConfirm={remove}
        onClose={() => setDeleting(null)}
      />
    </>
  );
}

const TABS = [
  { id: "colleges", label: "Colleges", icon: Building2 },
  { id: "departments", label: "Departments", icon: Layers },
  { id: "programs", label: "Programs", icon: GraduationCap },
];

const NO_QUERY = {};

// College structure: colleges (platform admin), departments (college admin), programs (admin or HOD).
function Organisation({ user, onChanged }) {
  const role = user?.role;
  const tabs = TABS.filter((tab) => tab.id !== "colleges" || can(role, "manageColleges"));
  const [tab, setTab] = useState(tabs[0].id);
  const [departments, setDepartments] = useState([]);
  const [version, setVersion] = useState(0);

  // Department list feeds the program form's department picker.
  useEffect(() => {
    api
      .get("/api/org/departments")
      .then(({ data }) => setDepartments(Array.isArray(data) ? data : []))
      .catch(() => setDepartments([]));
  }, [version]);

  const changed = () => {
    setVersion((value) => value + 1);
    onChanged?.();
  };

  const departmentName = (id) => departments.find((item) => item.id === id)?.name || "—";
  const manageableDepartments =
    role === "HOD" ? departments.filter((item) => item.id === user?.department?.id) : departments;

  return (
    <Page>
      <PageHeader
        eyebrow="Admin"
        title="Organisation"
        description={
          role === "SUPER_ADMIN"
            ? "Colleges on the platform, and the departments and programs of the college you're working in."
            : `Departments and programs of ${user?.institution?.name || "your college"}.`
        }
      />

      <div className="cp-tabs" role="tablist" aria-label="Organisation sections" style={{ margin: 0 }}>
        {tabs.map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            className="cp-tab"
            aria-selected={tab === item.id}
            onClick={() => setTab(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tab === "colleges" && (
        <CrudSection
          key="colleges"
          title="Colleges"
          icon={Building2}
          endpoint="/api/org/institutions"
          query={NO_QUERY}
          canEdit
          emptyText="Add the first college."
          onChanged={changed}
          columns={[
            { key: "name", label: "College", render: (row) => <span className="ui-cell-title"><strong>{row.name}</strong><small>{row.city || "—"}</small></span> },
            { key: "code", label: "Code" },
            { key: "departmentCount", label: "Departments" },
            { key: "userCount", label: "People" },
          ]}
          fields={[
            { key: "name", label: "College name", required: true, placeholder: "ABC College of Engineering" },
            { key: "code", label: "College code", required: true, hint: "Short and unique. People type it when they register.", placeholder: "ABC" },
            { key: "city", label: "City", placeholder: "Chennai" },
          ]}
        />
      )}

      {tab === "departments" && (
        <CrudSection
          key={`departments-${version}`}
          title="Departments"
          icon={Layers}
          endpoint="/api/org/departments"
          query={NO_QUERY}
          canEdit={can(role, "manageDepartments")}
          emptyText="Add departments such as CSE, IT, ECE or MCA."
          onChanged={changed}
          columns={[
            { key: "name", label: "Department", render: (row) => <strong>{row.name}</strong> },
            { key: "code", label: "Code" },
            { key: "programCount", label: "Programs" },
            { key: "userCount", label: "People" },
          ]}
          fields={[
            { key: "name", label: "Department name", required: true, placeholder: "Computer Science and Engineering" },
            { key: "code", label: "Code", required: true, placeholder: "CSE" },
          ]}
        />
      )}

      {tab === "programs" && (
        <CrudSection
          key={`programs-${version}`}
          title="Programs"
          icon={GraduationCap}
          endpoint="/api/org/programs"
          query={NO_QUERY}
          canEdit={can(role, "managePrograms")}
          emptyText="Add programs such as B.E. CSE (2021 regulation) or MCA."
          onChanged={changed}
          columns={[
            { key: "name", label: "Program", render: (row) => <span className="ui-cell-title"><strong>{row.name}</strong><small>{departmentName(row.departmentId)}</small></span> },
            { key: "degree", label: "Degree" },
            { key: "code", label: "Code" },
            { key: "regulation", label: "Regulation", render: (row) => row.regulation || "—" },
            { key: "durationSemesters", label: "Semesters" },
            { key: "studentCount", label: "Students" },
          ]}
          fields={[
            { key: "departmentId", label: "Department", required: true, options: manageableDepartments.map((item) => ({ value: String(item.id), label: item.name })) },
            { key: "name", label: "Program name", required: true, placeholder: "B.E. Computer Science and Engineering" },
            { key: "degree", label: "Degree", required: true, placeholder: "BE", hint: "BE, BTECH, MCA, MBA, BSC…" },
            { key: "code", label: "Code", required: true, placeholder: "CSE" },
            { key: "regulation", label: "Regulation", placeholder: "2021" },
            { key: "durationSemesters", label: "Semesters", type: "number", default: "8" },
          ]}
          makeBody={(form) => ({ ...form, departmentId: String(form.departmentId), durationSemesters: String(form.durationSemesters || "8") })}
        />
      )}
    </Page>
  );
}

export default Organisation;
