import { useState } from "react";
import { UserPlus } from "lucide-react";
import { API_BASE_URL } from "../api";
import { Alert, Button, Field } from "../ui";
import { AuthLayout, PasswordInput } from "./auth/AuthLayout";

const API_URL = `${API_BASE_URL}/api/auth`;
const ROLES = [
  ["FACULTY", "Faculty"],
  ["STUDENT", "Student"],
];

function Register({ onRegistered, onShowLogin }) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("FACULTY");
  const [institutionCode, setInstitutionCode] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    if (password.length < 8) {
      setError("Password must have at least 8 characters.");
      return;
    }
    setLoading(true);
    try {
      const response = await fetch(`${API_URL}/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: name.trim(),
          email: email.trim(),
          password,
          role,
          institutionCode: institutionCode.trim(),
        }),
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || "Registration failed.");
      onRegistered?.();
    } catch (err) {
      setError(err.message === "Failed to fetch" ? "Can't reach CampusPulse. Is the backend running?" : err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      eyebrow="Get started"
      title="Create an account"
      description="Most accounts are created by your college admin. Register here only if you were asked to."
      footer={
        <>
          Already have an account?
          <button type="button" className="au-link" onClick={onShowLogin}>
            Sign in
          </button>
        </>
      }
    >
      <form className="au-form" onSubmit={handleSubmit}>
        {error && <Alert tone="danger">{error}</Alert>}
        <div className="ui-field">
          <span className="ui-field-label" id="register-role">I am</span>
          <div className="au-roles" role="radiogroup" aria-labelledby="register-role">
            {ROLES.map(([value, label]) => (
              <button key={value} type="button" role="radio" aria-checked={role === value} onClick={() => setRole(value)}>
                {label}
              </button>
            ))}
          </div>
        </div>
        <Field label="College code" hint="Ask your college admin. Leave blank for the default college.">
          <input value={institutionCode} onChange={(e) => setInstitutionCode(e.target.value)} autoComplete="organization" />
        </Field>
        <Field label="Full name">
          <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" maxLength={100} required />
        </Field>
        <Field label="Email">
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        </Field>
        <Field label="Password" hint="At least 8 characters.">
          <PasswordInput
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            minLength={8}
            required
          />
        </Field>
        <Button type="submit" variant="primary" icon={UserPlus} disabled={loading}>
          {loading ? "Creating…" : "Create account"}
        </Button>
      </form>
    </AuthLayout>
  );
}

export default Register;
