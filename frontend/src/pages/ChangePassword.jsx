import { useState } from "react";
import { KeyRound, LogOut } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Alert, Button, Field } from "../ui";

// Shown after signing in with a one-time password (admin-created, imported or reset accounts).
// The backend refuses other requests until this succeeds.
function ChangePassword({ user, onChanged, onLogout }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    if (next.length < 8) {
      setError("The new password needs at least 8 characters.");
      return;
    }
    if (next !== confirm) {
      setError("The two new passwords don't match.");
      return;
    }
    setBusy(true);
    try {
      await api.post("/api/auth/change-password", { currentPassword: current, newPassword: next });
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Couldn't change the password."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="ui-center-screen">
      <section className="ui-card ui-center-card">
        <div className="ui-card-body">
          <span className="ui-empty-icon" aria-hidden="true">
            <KeyRound size={22} />
          </span>
          <h1 className="ui-center-title">Set your own password</h1>
          <p className="ui-muted">
            {user?.name ? `Welcome, ${user.name}. ` : ""}You signed in with a one-time password. Choose a new
            password to continue.
          </p>

          <form className="ui-form" onSubmit={submit} style={{ marginTop: 20 }}>
            {error && <Alert tone="danger">{error}</Alert>}
            <Field label="One-time password">
              <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} required autoComplete="current-password" autoFocus />
            </Field>
            <Field label="New password" hint="At least 8 characters.">
              <input type="password" value={next} onChange={(e) => setNext(e.target.value)} required autoComplete="new-password" minLength={8} />
            </Field>
            <Field label="Repeat new password">
              <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required autoComplete="new-password" />
            </Field>
            <Button variant="primary" type="submit" disabled={busy}>
              {busy ? "Saving…" : "Save password and continue"}
            </Button>
            <Button variant="ghost" icon={LogOut} onClick={onLogout}>
              Sign out
            </Button>
          </form>
        </div>
      </section>
    </main>
  );
}

export default ChangePassword;
