import { useState } from "react";
import { KeyRound, X } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Alert, Button, Card, Field } from "../ui";
import { useToast } from "../ui/toast";

const ROLE_LABEL = { STUDENT: "Student", FACULTY: "Faculty", HOD: "HOD", ADMIN: "College admin", SUPER_ADMIN: "Platform admin" };

// Account details (set by the HOD / college admin) and a change-password form.
function AccountCard({ user }) {
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const rows = [
    ["Name", user?.name],
    ["Email", user?.email],
    ["Role", ROLE_LABEL[user?.role] || user?.role],
    ["Roll number / USN", user?.rollNumber],
    ["College", user?.institution?.name],
    ["Department", user?.department?.name],
    ["Programme", user?.program?.name],
  ];

  const reset = () => {
    setOpen(false);
    setCurrent("");
    setNext("");
    setConfirm("");
    setError("");
  };

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    if (next.length < 8) return setError("The new password needs at least 8 characters.");
    if (next !== confirm) return setError("The two new passwords don't match.");
    setBusy(true);
    try {
      await api.post("/api/auth/change-password", { currentPassword: current, newPassword: next });
      toast("Password changed.");
      reset();
    } catch (err) {
      setError(errorMessage(err, "Couldn't change the password."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card
      title="Account"
      description="Your details come from your HOD or college admin. Ask them if something is wrong."
      actions={
        !open && (
          <Button icon={KeyRound} onClick={() => setOpen(true)}>
            Change password
          </Button>
        )
      }
    >
      <dl style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "12px 24px", margin: 0 }}>
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt className="ui-muted" style={{ fontSize: 13 }}>{label}</dt>
            <dd style={{ margin: 0, fontWeight: 500 }}>{value || <span className="ui-muted">Not set</span>}</dd>
          </div>
        ))}
      </dl>

      {open && (
        <form className="ui-form" onSubmit={submit} style={{ marginTop: 20, maxWidth: 420 }}>
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
            <Button variant="ghost" icon={X} onClick={reset}>
              Cancel
            </Button>
          </div>
        </form>
      )}
    </Card>
  );
}

export default AccountCard;
