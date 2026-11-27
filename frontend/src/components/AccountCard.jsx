import { useState } from "react";
import { KeyRound, Mail, Pencil, Save, X } from "lucide-react";
import api, { errorMessage, TOKEN_KEY } from "../apiClient";
import { Alert, Button, Card, Field } from "../ui";
import { useToast } from "../ui/toast";

const ROLE_LABEL = { STUDENT: "Student", FACULTY: "Faculty", HOD: "HOD", ADMIN: "College admin", SUPER_ADMIN: "Platform admin" };
const grid = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "12px 24px" };

// Account details, edit form (students: name, USN, department, programme, batch year) and password change.
function AccountCard({ user, onUserChanged }) {
  const toast = useToast();
  const isStudent = user?.role === "STUDENT";
  const [mode, setMode] = useState("view"); // view | edit | password
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  // edit form
  const [form, setForm] = useState({});
  const [departments, setDepartments] = useState([]);
  const [programs, setPrograms] = useState([]);

  // password form
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");

  const rows = [
    ["Name", user?.name],
    ["Email", user?.email],
    ["Role", ROLE_LABEL[user?.role] || user?.role],
    ["Roll number / USN", user?.rollNumber],
    ["College", user?.institution?.name],
    ["Department", user?.department?.name],
    ["Programme", user?.program?.name],
    ["Batch year", user?.batchYear],
  ];

  const close = () => {
    setMode("view");
    setError("");
    setCurrent("");
    setNext("");
    setConfirm("");
  };

  const loadPrograms = async (departmentId) => {
    if (!departmentId) return setPrograms([]);
    try {
      const { data } = await api.get("/api/org/programs", { params: { departmentId } });
      setPrograms(Array.isArray(data) ? data : []);
    } catch {
      setPrograms([]);
    }
  };

  const startEdit = async () => {
    setError("");
    setForm({
      name: user?.name || "",
      rollNumber: user?.rollNumber || "",
      departmentId: user?.department?.id ? String(user.department.id) : "",
      programId: user?.program?.id ? String(user.program.id) : "",
      batchYear: user?.batchYear ? String(user.batchYear) : "",
    });
    setMode("edit");
    if (!isStudent) return;
    try {
      const { data } = await api.get("/api/org/departments");
      setDepartments(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(errorMessage(err, "Couldn't load the departments."));
    }
    await loadPrograms(user?.department?.id);
  };

  const canTestMail = user?.role === "ADMIN" || user?.role === "SUPER_ADMIN";
  const testMail = async () => {
    setBusy(true);
    try {
      const { data } = await api.post("/api/admin/users/mail-test");
      toast(data.sent ? `Test email sent to ${data.to}.` : data.message, data.sent ? undefined : "danger");
    } catch (err) {
      toast(errorMessage(err, "Couldn't send the test email."), "danger");
    } finally {
      setBusy(false);
    }
  };

  const set = (key) => (event) => setForm((f) => ({ ...f, [key]: event.target.value }));

  const changeDepartment = (event) => {
    const departmentId = event.target.value;
    setForm((f) => ({ ...f, departmentId, programId: "" }));
    loadPrograms(departmentId);
  };

  const saveProfile = async (event) => {
    event.preventDefault();
    setError("");
    if (isStudent && !form.departmentId) return setError("Choose your department.");
    setBusy(true);
    try {
      const body = isStudent ? form : { name: form.name };
      const { data } = await api.put("/api/auth/me", body);
      onUserChanged?.(data);
      toast("Profile saved.");
      close();
    } catch (err) {
      setError(errorMessage(err, "Couldn't save your profile."));
    } finally {
      setBusy(false);
    }
  };

  const savePassword = async (event) => {
    event.preventDefault();
    setError("");
    if (next.length < 8) return setError("The new password needs at least 8 characters.");
    if (next !== confirm) return setError("The two new passwords don't match.");
    setBusy(true);
    try {
      const { data } = await api.post("/api/auth/change-password", { currentPassword: current, newPassword: next });
      // Older logins end with the change; keep this session on the new token.
      if (data?.token) sessionStorage.setItem(TOKEN_KEY, data.token);
      toast("Password changed.");
      close();
    } catch (err) {
      setError(errorMessage(err, "Couldn't change the password."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card
      title="Account"
      description={
        isStudent
          ? "Keep your USN, department and programme correct: your reports and learning plan use them. Email is changed by your HOD or college admin."
          : "Your role and department are managed by your college admin."
      }
      actions={
        mode === "view" && (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <Button icon={Pencil} onClick={startEdit}>
              Edit profile
            </Button>
            <Button icon={KeyRound} onClick={() => setMode("password")}>
              Change password
            </Button>
            {canTestMail && (
              <Button icon={Mail} onClick={testMail} disabled={busy}>
                Send test email
              </Button>
            )}
          </div>
        )
      }
    >
      {mode !== "edit" && (
        <dl style={{ ...grid, margin: 0 }}>
          {rows.map(([label, value]) => (
            <div key={label}>
              <dt className="ui-muted" style={{ fontSize: 13 }}>{label}</dt>
              <dd style={{ margin: 0, fontWeight: 500 }}>{value || <span className="ui-muted">Not set</span>}</dd>
            </div>
          ))}
        </dl>
      )}

      {mode === "edit" && (
        <form className="ui-form" onSubmit={saveProfile}>
          {error && <Alert tone="danger">{error}</Alert>}
          <div style={grid}>
            <Field label="Full name">
              <input value={form.name} onChange={set("name")} required minLength={2} maxLength={100} autoComplete="name" />
            </Field>
            {isStudent && (
              <>
                <Field label="Roll number / USN" hint="e.g. 4XY24CS042">
                  <input value={form.rollNumber} onChange={set("rollNumber")} maxLength={20} style={{ textTransform: "uppercase" }} />
                </Field>
                <Field label="Department">
                  <select value={form.departmentId} onChange={changeDepartment} required>
                    <option value="">Choose department</option>
                    {departments.map((d) => (
                      <option key={d.id} value={d.id}>{d.name}</option>
                    ))}
                  </select>
                </Field>
                <Field label="Programme">
                  <select value={form.programId} onChange={set("programId")} disabled={!form.departmentId}>
                    <option value="">{programs.length ? "Choose programme" : "No programmes in this department"}</option>
                    {programs.map((p) => (
                      <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                  </select>
                </Field>
                <Field label="Batch year" hint="Year you joined, e.g. 2025.">
                  <input type="number" value={form.batchYear} onChange={set("batchYear")} min={2000} max={2100} />
                </Field>
              </>
            )}
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <Button variant="primary" type="submit" icon={Save} disabled={busy}>
              {busy ? "Saving…" : "Save profile"}
            </Button>
            <Button variant="ghost" icon={X} onClick={close}>
              Cancel
            </Button>
          </div>
        </form>
      )}

      {mode === "password" && (
        <form className="ui-form" onSubmit={savePassword} style={{ marginTop: 20, maxWidth: 420 }}>
          {error && <Alert tone="danger">{error}</Alert>}
          <Field label="Current password">
            <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} required autoComplete="current-password" autoFocus />
          </Field>
          <Field label="New password" hint="At least 8 characters.">
            <input type="password" value={next} onChange={(e) => setNext(e.target.value)} required autoComplete="new-password" minLength={8} />
          </Field>
          <Field label="Repeat new password">
            <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required autoComplete="new-password" />
          </Field>
          <div style={{ display: "flex", gap: 8 }}>
            <Button variant="primary" type="submit" disabled={busy}>
              {busy ? "Saving…" : "Save new password"}
            </Button>
            <Button variant="ghost" icon={X} onClick={close}>
              Cancel
            </Button>
          </div>
        </form>
      )}
    </Card>
  );
}

export default AccountCard;
