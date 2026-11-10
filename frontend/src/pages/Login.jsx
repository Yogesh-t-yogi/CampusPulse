import { useState } from "react";
import { LogIn } from "lucide-react";
import { API_BASE_URL } from "../api";
import { Alert, Button, Field } from "../ui";
import { AuthLayout, PasswordInput } from "./auth/AuthLayout";

const API_URL = `${API_BASE_URL}/api/auth`;

function Login({ onLogin, onShowRegister, notice }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setLoading(true);
    try {
      const response = await fetch(`${API_URL}/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.trim(), password }),
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || "Invalid email or password.");
      if (!data.token) throw new Error("The server did not return a sign-in token.");
      onLogin(data.token, Boolean(data.mustChangePassword));
    } catch (err) {
      setError(err.message === "Failed to fetch" ? "Can't reach CampusPulse. Is the backend running?" : err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      eyebrow="Welcome back"
      title="Sign in"
      description="Use the account your college gave you."
      footer={
        <>
          New to CampusPulse?
          <button type="button" className="au-link" onClick={onShowRegister}>
            Create an account
          </button>
        </>
      }
    >
      <form className="au-form" onSubmit={handleSubmit}>
        {notice && !error && <Alert tone="warning">{notice}</Alert>}
        {error && <Alert tone="danger">{error}</Alert>}
        <Field label="Email">
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@college.edu"
            autoComplete="email"
            required
          />
        </Field>
        <Field label="Password">
          <PasswordInput
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Your password"
            autoComplete="current-password"
            required
          />
        </Field>
        <Button type="submit" variant="primary" icon={LogIn} disabled={loading}>
          {loading ? "Signing in…" : "Sign in"}
        </Button>
      </form>
    </AuthLayout>
  );
}

export default Login;
