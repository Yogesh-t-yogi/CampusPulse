import { useCallback, useEffect, useState } from "react";
import { Copy, KeyRound, Pencil, Trash2, UserPlus, Users as UsersIcon } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { assignableRoles, roleLabel } from "../permissions";
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

const ALL_ROLES = ["SUPER_ADMIN", "ADMIN", "HOD", "FACULTY", "STUDENT"];
const EMPTY_FORM = { name: "", email: "", role: "STUDENT", departmentId: "", programId: "" };

// Accounts of the current college (HOD: own department).
// Index, create (one-time password), edit role/department/program, reset password, delete.
function Users({ currentUser }) {
  const toast = useToast();
  const actorRole = currentUser?.role;
  const isHod = actorRole === "HOD";
  const roles = assignableRoles(actorRole);

  const [users, setUsers] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [programs, setPrograms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [departmentFilter, setDepartmentFilter] = useState("ALL");
  const [editing, setEditing] = useState(null); // null | "new" | user
  const [form, setForm] = useState(EMPTY_FORM);
  const [formError, setFormError] = useState("");
  const [secret, setSecret] = useState(null);
  const [resetting, setResetting] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [usersResponse, departmentsResponse, programsResponse] = await Promise.all([
        api.get("/api/admin/users"),
        api.get("/api/org/departments"),
        api.get("/api/org/programs"),
      ]);
      setUsers(Array.isArray(usersResponse.data) ? usersResponse.data : []);
      setDepartments(Array.isArray(departmentsResponse.data) ? departmentsResponse.data : []);
      setPrograms(Array.isArray(programsResponse.data) ? programsResponse.data : []);
    } catch (err) {
      toast(errorMessage(err, "Couldn't load users."), "danger");
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const ownDepartmentId = currentUser?.department?.id ? String(currentUser.department.id) : "";
  const departmentOptions = isHod
    ? departments.filter((item) => String(item.id) === ownDepartmentId)
    : departments;
  const programOptions = programs.filter((item) => String(item.departmentId) === String(form.departmentId));

  const openEditor = (user) => {
    setFormError("");
    setEditing(user || "new");
    setForm(
      user
        ? {
            name: user.name,
            email: user.email,
            role: user.role,
            departmentId: user.departmentId ? String(user.departmentId) : "",
            programId: user.programId ? String(user.programId) : "",
          }
        : { ...EMPTY_FORM, role: roles.includes("STUDENT") ? "STUDENT" : roles[0], departmentId: isHod ? ownDepartmentId : "" }
    );
  };

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setFormError("");
    const body = {
      role: form.role,
      departmentId: form.departmentId || null,
      programId: form.role === "STUDENT" ? form.programId || null : null,
    };
    try {
      if (editing === "new") {
        const { data } = await api.post("/api/admin/users", { ...body, name: form.name, email: form.email });
        setSecret(data);
      } else {
        await api.put(`/api/admin/users/${editing.id}`, body);
        toast("Account updated");
      }
      setEditing(null);
      await load();
    } catch (err) {
      setFormError(errorMessage(err, "Couldn't save the account."));
    } finally {
      setBusy(false);
    }
  };

  const resetPassword = async () => {
    setBusy(true);
    try {
      const { data } = await api.post(`/api/admin/users/${resetting.id}/reset-password`);
      setResetting(null);
      setSecret(data);
    } catch (err) {
      toast(errorMessage(err, "Couldn't reset the password."), "danger");
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setBusy(true);
    try {
      await api.delete(`/api/admin/users/${deleting.id}`);
      setDeleting(null);
      toast("Account deleted");
      await load();
    } catch (err) {
      toast(errorMessage(err, "Couldn't delete the account."), "danger");
    } finally {
      setBusy(false);
    }
  };

  const copySecret = async () => {
    try {
      await navigator.clipboard.writeText(secret.temporaryPassword);
      toast("Password copied");
    } catch {
      toast("Copy failed. Select the password and copy it.", "danger");
    }
  };

  const rows = users.filter((user) => {
    const text = `${user.name} ${user.email}`.toLowerCase();
    return (
      (roleFilter === "ALL" || user.role === roleFilter) &&
      (departmentFilter === "ALL" || String(user.departmentId || "") === departmentFilter) &&
      text.includes(query.trim().toLowerCase())
    );
  });

  const columns = [
    {
      key: "name",
      label: "Person",
      render: (user) => (
        <span className="ui-cell-title">
          <strong>
            {user.name}
            {user.id === currentUser?.id && " (you)"}
          </strong>
          <small>{user.email}</small>
        </span>
      ),
    },
    { key: "role", label: "Role", render: (user) => <Badge status={user.role}>{roleLabel(user.role)}</Badge> },
    {
      key: "placement",
      label: "Department · Program",
      render: (user) => (
        <span className="ui-cell-title">
          <strong>{user.departmentName || "No department"}</strong>
          <small>{user.programName || (user.role === "STUDENT" ? "No program" : "")}</small>
        </span>
      ),
    },
    {
      key: "createdAt",
      label: "Joined",
      render: (user) => {
        const date = new Date(user.createdAt);
        return Number.isNaN(date.getTime()) ? "—" : date.toLocaleDateString();
      },
    },
    {
      key: "actions",
      label: "",
      width: "1%",
      render: (user) =>
        user.id === currentUser?.id ? null : (
          <div className="ui-row-actions">
            <IconButton icon={Pencil} label={`Edit ${user.name}`} onClick={() => openEditor(user)} />
            <IconButton icon={KeyRound} label={`Reset password for ${user.name}`} onClick={() => setResetting(user)} />
            <IconButton icon={Trash2} label={`Delete ${user.name}`} variant="ghost-danger" onClick={() => setDeleting(user)} />
          </div>
        ),
    },
  ];

  const counts = ALL_ROLES.map((role) => [role, users.filter((user) => user.role === role).length]).filter(
    ([, count]) => count > 0
  );

  return (
    <Page>
      <PageHeader
        eyebrow={isHod ? "Department" : "Admin"}
        title="Users"
        description={
          isHod
            ? `Faculty and students of ${currentUser?.department?.name || "your department"}.`
            : `People of ${currentUser?.institution?.name || "this college"}. Role and placement changes apply at the next sign-in.`
        }
        actions={
          <Button variant="primary" icon={UserPlus} onClick={() => openEditor(null)} disabled={roles.length === 0}>
            Add user
          </Button>
        }
      />

      <DataTable
        loading={loading}
        columns={columns}
        rows={rows}
        toolbar={
          <>
            <div className="ui-table-toolbar-group">
              <SearchInput value={query} onChange={setQuery} placeholder="Search name or email" />
              <select className="ui-select" style={{ width: "auto" }} value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)} aria-label="Filter by role">
                <option value="ALL">All roles</option>
                {ALL_ROLES.map((role) => (
                  <option key={role} value={role}>
                    {roleLabel(role)}
                  </option>
                ))}
              </select>
              {!isHod && (
                <select className="ui-select" style={{ width: "auto" }} value={departmentFilter} onChange={(e) => setDepartmentFilter(e.target.value)} aria-label="Filter by department">
                  <option value="ALL">All departments</option>
                  <option value="">No department</option>
                  {departments.map((item) => (
                    <option key={item.id} value={String(item.id)}>
                      {item.name}
                    </option>
                  ))}
                </select>
              )}
            </div>
            <span className="ui-chip-list">
              {counts.map(([role, count]) => (
                <Badge key={role} status={role}>
                  {roleLabel(role)}: {count}
                </Badge>
              ))}
            </span>
          </>
        }
        empty={<EmptyState icon={UsersIcon} title="No people match" description="Try another search or filter." />}
      />

      <Drawer
        open={Boolean(editing)}
        title={editing === "new" ? "Add user" : `Edit ${editing?.name}`}
        description={editing === "new" ? "A one-time password is shown after saving. Share it securely." : undefined}
        onClose={() => setEditing(null)}
        footer={
          <>
            <Button onClick={() => setEditing(null)}>Cancel</Button>
            <Button variant="primary" type="submit" form="user-form" disabled={busy}>
              {busy ? "Saving…" : editing === "new" ? "Create account" : "Save changes"}
            </Button>
          </>
        }
      >
        <form id="user-form" className="ui-form" onSubmit={save}>
          {formError && <Alert tone="danger">{formError}</Alert>}
          {editing === "new" && (
            <>
              <Field label="Full name">
                <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required autoFocus />
              </Field>
              <Field label="Email">
                <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required placeholder="name@college.edu" />
              </Field>
            </>
          )}
          <Field label="Role">
            <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
              {roles.concat(editing !== "new" && !roles.includes(form.role) ? [form.role] : []).map((role) => (
                <option key={role} value={role}>
                  {roleLabel(role)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Department" hint={isHod ? "HODs add people to their own department." : undefined}>
            <select
              value={form.departmentId}
              disabled={isHod}
              onChange={(e) => setForm({ ...form, departmentId: e.target.value, programId: "" })}
            >
              <option value="">No department</option>
              {departmentOptions.map((item) => (
                <option key={item.id} value={String(item.id)}>
                  {item.name}
                </option>
              ))}
            </select>
          </Field>
          {form.role === "STUDENT" && (
            <Field label="Program" hint={form.departmentId ? undefined : "Choose a department first."}>
              <select value={form.programId} disabled={!form.departmentId} onChange={(e) => setForm({ ...form, programId: e.target.value })}>
                <option value="">No program</option>
                {programOptions.map((item) => (
                  <option key={item.id} value={String(item.id)}>
                    {item.name}
                    {item.regulation ? ` (${item.regulation})` : ""}
                  </option>
                ))}
              </select>
            </Field>
          )}
          {editing === "new" && (
            <Alert tone="info">Adding many students at once from Excel comes next (Phase 6.5).</Alert>
          )}
        </form>
      </Drawer>

      <Modal
        open={Boolean(secret)}
        title="One-time password"
        description={`For ${secret?.name} (${secret?.email}). It won't be shown again.`}
        onClose={() => setSecret(null)}
        size="sm"
        footer={<Button variant="primary" onClick={() => setSecret(null)}>Done</Button>}
      >
        <div className="ui-secret">
          <span>{secret?.temporaryPassword}</span>
          <IconButton icon={Copy} label="Copy password" onClick={copySecret} />
        </div>
      </Modal>

      <ConfirmDialog
        open={Boolean(resetting)}
        title="Reset password?"
        message={`${resetting?.name}'s current password stops working. You'll get a new one-time password to share.`}
        confirmLabel="Reset password"
        tone="primary"
        busy={busy}
        onConfirm={resetPassword}
        onClose={() => setResetting(null)}
      />

      <ConfirmDialog
        open={Boolean(deleting)}
        title="Delete account?"
        message={`${deleting?.name}'s account, syllabi and private jobs will be deleted. Catalog jobs stay. This can't be undone.`}
        busy={busy}
        onConfirm={remove}
        onClose={() => setDeleting(null)}
      />
    </Page>
  );
}

export default Users;
