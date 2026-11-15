import { useState } from "react";
import { BarChart3, BookOpen, Eye, EyeOff, Lightbulb, Sparkles } from "lucide-react";
import "./auth.css";

const FEATURES = [
  { icon: BookOpen, title: "Curriculum intelligence", text: "See what each syllabus really covers." },
  { icon: BarChart3, title: "Industry skill gaps", text: "Compare with the skills employers ask for." },
  { icon: Lightbulb, title: "Clear next steps", text: "Curriculum changes and learning plans." },
];

// Two-column sign-in frame: brand panel (hidden on phones) + form card. Uses theme tokens.
export function AuthLayout({ eyebrow, title, description, children, footer }) {
  return (
    <main className="au-page">
      <aside className="au-brand-panel" aria-hidden="true">
        <div className="au-brand">
          <span className="au-logo">
            <Sparkles size={18} />
          </span>
          CampusPulse
        </div>
        <div>
          <h1>
            Bridge the gap between
            <br />
            syllabus and industry.
          </h1>
          <ul className="au-features">
            {FEATURES.map(({ icon: Icon, title: name, text }) => (
              <li key={name}>
                <span className="au-feature-icon">
                  <Icon size={16} />
                </span>
                <span>
                  <strong>{name}</strong>
                  <small>{text}</small>
                </span>
              </li>
            ))}
          </ul>
        </div>
        <small className="au-muted-light">Skill gap analysis for colleges · NBA CO/PO ready</small>
      </aside>

      <section className="au-form-side">
        <div className="au-card">
          <div className="au-brand au-brand-mobile">
            <span className="au-logo">
              <Sparkles size={18} />
            </span>
            CampusPulse
          </div>
          {eyebrow && <span className="au-eyebrow">{eyebrow}</span>}
          <h2>{title}</h2>
          {description && <p className="au-description">{description}</p>}
          {children}
          {footer && <div className="au-footer">{footer}</div>}
        </div>
      </section>
    </main>
  );
}

// Password input with show/hide toggle.
export function PasswordInput({ id, value, onChange, ...props }) {
  const [shown, setShown] = useState(false);
  return (
    <span className="au-password">
      <input id={id} type={shown ? "text" : "password"} value={value} onChange={onChange} {...props} />
      <button type="button" onClick={() => setShown((v) => !v)} aria-label={shown ? "Hide password" : "Show password"}>
        {shown ? <EyeOff size={16} /> : <Eye size={16} />}
      </button>
    </span>
  );
}
