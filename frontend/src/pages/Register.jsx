
import { useState } from "react";
import { API_BASE_URL } from "../api";

const API_URL = `${API_BASE_URL}/api/auth`;

function Register({ onRegistered, onShowLogin }) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setMessage("");

    if (password.length < 8) {
      setError("Password must contain at least 8 characters.");
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
        }),
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.message || "Registration failed.");
      }

      setMessage("Account created successfully! You can now sign in.");
      setName("");
      setEmail("");
      setPassword("");
      setShowPassword(false);

      if (onRegistered) {
        onRegistered();
      }
    } catch (err) {
      setError(
        err.message || "Unable to connect to CampusPulse. Check your backend."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="cp-auth">
      <section className="ca-shell ca-register-shell">
        <aside className="ca-showcase">
          <div className="ca-brand">
            <span className="ca-brand-icon">✦</span>
            <span>
              <strong>CampusPulse</strong>
              <small>AI Intelligence</small>
            </span>
          </div>

          <div className="ca-showcase-content">
            <span className="ca-pill">
              <span className="ca-live-dot" />
              YOUR NEXT CHAPTER STARTS HERE
            </span>

            <h1>
              Learn smarter.
              <br />
              <span>Grow further.</span>
            </h1>

            <p>
              Create your CampusPulse account and explore a smarter way to
              understand curriculum, analyze industry demands, and discover
              your next learning opportunity.
            </p>

            <div className="ca-register-highlight">
              <span className="ca-highlight-symbol">✧</span>
              <div>
                <strong>Your skills. Your future.</strong>
                <p>
                  Make informed learning decisions with AI-powered insights
                  that connect education to industry.
                </p>
              </div>
            </div>

            <div className="ca-register-points">
              <span><b>✓</b> Curriculum insights</span>
              <span><b>✓</b> Industry skill analysis</span>
              <span><b>✓</b> Personalized recommendations</span>
            </div>
          </div>

          <div className="ca-showcase-footer">
            <span>✦</span> Start building your future today.
          </div>
          <div className="ca-orb ca-orb-one" />
          <div className="ca-orb ca-orb-two" />
        </aside>

        <section className="ca-form-side">
          <div className="ca-mobile-brand">
            <span className="ca-brand-icon">✦</span>
            <strong>CampusPulse</strong>
          </div>

          <div className="ca-form-wrap ca-register-form-wrap">
            <div className="ca-welcome-icon">✧</div>
            <span className="ca-form-eyebrow">GET STARTED FOR FREE</span>
            <h2>Create your account</h2>
            <p className="ca-form-description">
              Join CampusPulse and explore AI-powered learning insights.
            </p>

            <form className="ca-form" onSubmit={handleSubmit}>
              <div className="ca-field">
                <label htmlFor="register-name">Full name</label>
                <div className="ca-input-wrap">
                  <span className="ca-input-icon" aria-hidden="true">♙</span>
                  <input
                    id="register-name"
                    type="text"
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                    placeholder="Enter your full name"
                    autoComplete="name"
                    maxLength={100}
                    required
                  />
                </div>
              </div>

              <div className="ca-field">
                <label htmlFor="register-email">Email address</label>
                <div className="ca-input-wrap">
                  <span className="ca-input-icon" aria-hidden="true">@</span>
                  <input
                    id="register-email"
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="you@example.com"
                    autoComplete="email"
                    required
                  />
                </div>
              </div>

              <div className="ca-field">
                <label htmlFor="register-password">Create password</label>
                <div className="ca-input-wrap">
                  <span className="ca-input-icon" aria-hidden="true">◆</span>
                  <input
                    id="register-password"
                    type={showPassword ? "text" : "password"}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="At least 8 characters"
                    autoComplete="new-password"
                    minLength={8}
                    required
                  />
                  <button
                    type="button"
                    className="ca-password-toggle"
                    onClick={() => setShowPassword((value) => !value)}
                    aria-label={showPassword ? "Hide password" : "Show password"}
                  >
                    {showPassword ? "Hide" : "Show"}
                  </button>
                </div>
                <small className="ca-field-hint">
                  Use at least 8 characters.
                </small>
              </div>

              {error && (
                <div className="ca-message ca-error" role="alert">
                  <span>!</span>
                  <p>{error}</p>
                </div>
              )}

              {message && (
                <div className="ca-message ca-success" role="status">
                  <span>✓</span>
                  <p>{message}</p>
                </div>
              )}

              <button
                type="submit"
                className="ca-submit"
                disabled={loading}
              >
                {loading ? (
                  <>
                    <span className="ca-button-spinner" />
                    Creating account...
                  </>
                ) : (
                  <>
                    Create your account <span>→</span>
                  </>
                )}
              </button>
            </form>

            <div className="ca-form-divider">
              <span />
              <small>START YOUR JOURNEY</small>
              <span />
            </div>

            <p className="ca-switch-text">
              Already have an account?
              <button type="button" onClick={onShowLogin}>
                Sign in
              </button>
            </p>

            <div className="ca-security-note">
              <span>◆</span>
              Your account is protected by secure authentication.
            </div>
          </div>

          <footer className="ca-bottom-footer">
            CampusPulse · AI-powered curriculum intelligence
          </footer>
        </section>
      </section>
    </main>
  );
}

export default Register;
