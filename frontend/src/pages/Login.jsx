
import { useState } from "react";

const API_URL = "http://localhost:8081/api/auth";

function Login({ onLogin, onShowRegister }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
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
        body: JSON.stringify({
          email: email.trim(),
          password,
        }),
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.message || "Invalid email or password.");
      }

      if (!data.token) {
        throw new Error("The server did not return an authentication token.");
      }

      onLogin(data.token);
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
      <section className="ca-shell">
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
              INTELLIGENCE FOR EDUCATION
            </span>

            <h1>
              Bridge the gap.
              <br />
              <span>Build the future.</span>
            </h1>

            <p>
              Connect academic curriculum with real-world industry skills
              through AI-powered insights and recommendations.
            </p>

            <div className="ca-feature-list">
              <div className="ca-feature">
                <span className="ca-feature-icon">01</span>
                <div>
                  <strong>Curriculum intelligence</strong>
                  <small>Understand what your syllabus covers.</small>
                </div>
              </div>

              <div className="ca-feature">
                <span className="ca-feature-icon">02</span>
                <div>
                  <strong>Industry skill analysis</strong>
                  <small>Discover the skills employers need.</small>
                </div>
              </div>

              <div className="ca-feature">
                <span className="ca-feature-icon">03</span>
                <div>
                  <strong>AI-powered guidance</strong>
                  <small>Turn skill gaps into actionable steps.</small>
                </div>
              </div>
            </div>
          </div>

          <div className="ca-showcase-footer">
            <span>✦</span> Smarter learning starts here.
          </div>
          <div className="ca-orb ca-orb-one" />
          <div className="ca-orb ca-orb-two" />
        </aside>

        <section className="ca-form-side">
          <div className="ca-mobile-brand">
            <span className="ca-brand-icon">✦</span>
            <strong>CampusPulse</strong>
          </div>

          <div className="ca-form-wrap">
            <div className="ca-welcome-icon">↗</div>
            <span className="ca-form-eyebrow">WELCOME BACK</span>
            <h2>Sign in to your account</h2>
            <p className="ca-form-description">
              Continue your journey with CampusPulse.
            </p>

            <form className="ca-form" onSubmit={handleSubmit}>
              <div className="ca-field">
                <label htmlFor="login-email">Email address</label>
                <div className="ca-input-wrap">
                  <span className="ca-input-icon" aria-hidden="true">@</span>
                  <input
                    id="login-email"
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
                <div className="ca-label-row">
                  <label htmlFor="login-password">Password</label>
                  <span className="ca-secure-label">Secure sign-in</span>
                </div>

                <div className="ca-input-wrap">
                  <span className="ca-input-icon" aria-hidden="true">◆</span>
                  <input
                    id="login-password"
                    type={showPassword ? "text" : "password"}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="Enter your password"
                    autoComplete="current-password"
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
              </div>

              {error && (
                <div className="ca-message ca-error" role="alert">
                  <span>!</span>
                  <p>{error}</p>
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
                    Signing in...
                  </>
                ) : (
                  <>
                    Sign in to CampusPulse <span>→</span>
                  </>
                )}
              </button>
            </form>

            <div className="ca-form-divider">
              <span />
              <small>YOUR LEARNING JOURNEY</small>
              <span />
            </div>

            <p className="ca-switch-text">
              New to CampusPulse?
              <button type="button" onClick={onShowRegister}>
                Create an account
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

export default Login;
