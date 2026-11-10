import { useCallback, useEffect, useState } from "react";
import { Copy, KeyRound, Trash2, UserPlus, Users as UsersIcon } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { roleLabel } from "../permissions";
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

const ROLES = ["ADMIN", "FACULTY", "STUDENT"];
const EMPTY_FORM = { name: "", email: "", role: "STUDENT" };

// Admin: accounts index, create (one-time password), change role, reset password, delete.
function Users({ currentUserId }) {
  const toast = useToast();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formError, setFormError] = useState("");
  const [secret, setSecret] = useState(null); // {name, email, temporaryPassword}
  const [resetting, setResetting] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const { data } = await api.get("/api/admin/users");
      setUsers(Array.isArray(data) ? data : []);
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

  const create = async (event) => {
    event.preventDefault();
    setBusy(true);
    setFormError("");
    try {
      const { data } = await api.post("/api/admin/users", form);
      setCreating(false);
      setForm(EMPTY_FORM);
      setSecret(data);
      await load();
    } catch (err) {
      setFormError(errorMessage(err, "Couldn't create the account."));
    } finally {
      setBusy(false);
    }
  };

  const changeRole = async (user, role) => {
    try {
      const { data } = await api.put(`/api/admin/users/${user.id}/role`, { role });
      setUsers((list) => list.map((item) => (item.id === data.id ? data : item)));
      toast(`${user.name} is now ${roleLabel(role)}`);
    } catch (err) {
      toast(errorMessage(err, "Couldn't change the role."), "danger");
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
    return (roleFilter === "ALL" || user.role === roleFilter) && text.includes(query.trim().toLowerCase());
  });

  const columns = [
    {
      key: "name",
      label: "User",
      render: (user) => (
        <span className="ui-cell-title">
          <strong>
            {user.name}
            {user.id === currentUserId && " (you)"}
          </strong>
          <small>{user.email}</small>
        </span>
      ),
    },
    {
      key: "role",
      label: "Role",
      render: (user) =>
        user.id === currentUserId ? (
          <Badge status={user.role}>{roleLabel(user.role)}</Badge>
        ) : (
          <select
            className="ui-select"
            style={{ width: 140 }}
            aria-label={`Role for ${user.name}`}
            value={user.role}
            onChange={(event) => changeRole(user, event.target.value)}
          >
            {ROLES.map((role) => (
              <option key={role} value={role}>
                {roleLabel(role)}
              </option>
            ))}
          </select>
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
        user.id === currentUserId ? null : (
          <div className="ui-row-actions">
            <IconButton icon={KeyRound} label={`Reset password for ${user.name}`} onClick={() => setResetting(user)} />
            <IconButton icon={Trash2} label={`Delete ${user.name}`} variant="ghost-danger" onClick={() => setDeleting(user)} />
          </div>
        ),
    },
  ];

  const counts = ROLES.map((role) => [role, users.filter((user) => user.role === role).length]);

  return (
    <Page>
      <PageHeader
        eyebrow="Admin"
        title="Users"
        description="Create accounts, change roles and reset passwords. Role changes apply at the next sign-in."
        actions={
          <Button variant="primary" icon={UserPlus} onClick={() => setCreating(true)}>
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
              <select
                className="ui-select"
                style={{ width: "auto" }}
                value={roleFilter}
                onChange={(event) => setRoleFilter(event.target.value)}
                aria-label="Filter by role"
              >
                <option value="ALL">All roles</option>
                {ROLES.map((role) => (
                  <option key={role} value={role}>
                    {roleLabel(role)}
                  </option>
                ))}
              </select>
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
        empty={<EmptyState icon={UsersIcon} title="No users match" description="Try another search or role." />}
      />

      <Drawer
        open={creating}
        title="Add user"
        description="A one-time password is shown after saving. Share it with the person securely."
        onClose={() => setCreating(false)}
        footer={
          <>
            <Button onClick={() => setCreating(false)}>Cancel</Button>
            <Button variant="primary" type="submit" form="user-form" disabled={busy}>
              {busy ? "Creating…" : "Create account"}
            </Button>
          </>
        }
      >
        <form id="user-form" className="ui-form" onSubmit={create}>
          {formError && <Alert tone="danger">{formError}</Alert>}
          <Field label="Full name">
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required autoFocus />
          </Field>
          <Field label="Email">
            <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required placeholder="name@college.edu" />
          </Field>
          <Field label="Role">
            <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
              {ROLES.map((role) => (
                <option key={role} value={role}>
                  {roleLabel(role)}
                </option>
              ))}
            </select>
          </Field>
          <Alert tone="info">Bulk import of students from Excel comes with the multi-college update (Phase 6).</Alert>
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
        message={`${deleting?.name}'s account, syllabi and private jobs will be deleted. Shared catalog jobs stay. This can't be undone.`}
        busy={busy}
        onConfirm={remove}
        onClose={() => setDeleting(null)}
      />
    </Page>
  );
}

export default Users;
