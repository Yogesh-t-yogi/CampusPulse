import { useCallback, useEffect, useRef, useState } from "react";
import { ToastContext } from "./toast";
import { AlertTriangle, CheckCircle2, Inbox, Info, Search, X } from "lucide-react";

// CampusPulse UI kit: small, consistent building blocks on the theme tokens.
// Styles live in ui.css (class prefix "ui-").

export function PageHeader({ eyebrow, title, description, actions, children }) {
  return (
    <header className="ui-page-header">
      <div className="ui-page-header-text">
        {eyebrow && <span className="ui-eyebrow">{eyebrow}</span>}
        <h1>{title}</h1>
        {description && <p>{description}</p>}
        {children}
      </div>
      {actions && <div className="ui-page-header-actions">{actions}</div>}
    </header>
  );
}

export function Page({ children, wide = false }) {
  return <main className={`ui-page${wide ? " ui-page-wide" : ""}`}>{children}</main>;
}

export function Card({ title, description, actions, children, padded = true }) {
  return (
    <section className="ui-card">
      {(title || actions) && (
        <div className="ui-card-head">
          <div>
            {title && <h2>{title}</h2>}
            {description && <p>{description}</p>}
          </div>
          {actions && <div className="ui-card-actions">{actions}</div>}
        </div>
      )}
      <div className={padded ? "ui-card-body" : ""}>{children}</div>
    </section>
  );
}

export function StatCard({ label, value, hint, icon: Icon, tone = "accent" }) {
  return (
    <div className="ui-stat">
      {Icon && (
        <span className={`ui-stat-icon ui-tone-${tone}`} aria-hidden="true">
          <Icon size={18} />
        </span>
      )}
      <span className="ui-stat-label">{label}</span>
      <strong className="ui-stat-value">{value}</strong>
      {hint && <span className="ui-stat-hint">{hint}</span>}
    </div>
  );
}

export function Button({ variant = "secondary", icon: Icon, children, className = "", ...props }) {
  return (
    <button type="button" className={`ui-btn ui-btn-${variant} ${className}`} {...props}>
      {Icon && <Icon size={16} aria-hidden="true" />}
      {children}
    </button>
  );
}

export function IconButton({ icon: Icon, label, variant = "ghost", ...props }) {
  return (
    <button type="button" className={`ui-icon-btn ui-btn-${variant}`} aria-label={label} title={label} {...props}>
      <Icon size={16} aria-hidden="true" />
    </button>
  );
}

const STATUS_TONES = {
  MATCHED: "success", PARTIAL: "warning", GAP: "danger",
  READY: "success", READY_WITH_WARNINGS: "warning", PROCESSING: "info",
  FAILED: "danger", NOT_PROCESSED: "neutral",
  SUPER_ADMIN: "danger", ADMIN: "accent", HOD: "info", FACULTY: "neutral", STUDENT: "success",
  DATASET: "neutral", USER_PROVIDED: "info",
};

const STATUS_LABELS = {
  READY_WITH_WARNINGS: "Ready, with warnings", NOT_PROCESSED: "Not processed",
  USER_PROVIDED: "Private", DATASET: "Catalog",
};

export function Badge({ status, tone, children }) {
  const label = children ?? STATUS_LABELS[status] ??
    (status ? status.charAt(0) + status.slice(1).toLowerCase() : "");
  return <span className={`ui-badge ui-tone-${tone || STATUS_TONES[status] || "neutral"}`}>{label}</span>;
}

export function EmptyState({ icon: Icon = Inbox, title, description, action }) {
  return (
    <div className="ui-empty">
      <span className="ui-empty-icon" aria-hidden="true">
        <Icon size={22} />
      </span>
      <h3>{title}</h3>
      {description && <p>{description}</p>}
      {action}
    </div>
  );
}

export function Alert({ tone = "info", title, children }) {
  const Icon = tone === "danger" || tone === "warning" ? AlertTriangle : tone === "success" ? CheckCircle2 : Info;
  return (
    <div className={`ui-alert ui-tone-${tone}`} role={tone === "danger" ? "alert" : "status"}>
      <Icon size={18} aria-hidden="true" />
      <div>
        {title && <strong>{title}</strong>}
        {children && <div>{children}</div>}
      </div>
    </div>
  );
}

export function Skeleton({ lines = 3 }) {
  return (
    <div className="ui-skeleton" aria-busy="true" aria-label="Loading">
      {Array.from({ length: lines }, (_, index) => (
        <span key={index} style={{ width: `${90 - index * 12}%` }} />
      ))}
    </div>
  );
}

export function SearchInput({ value, onChange, placeholder = "Search", label }) {
  return (
    <label className="ui-search">
      <Search size={16} aria-hidden="true" />
      <input
        type="search"
        value={value}
        placeholder={placeholder}
        aria-label={label || placeholder}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

export function Field({ label, hint, error, children }) {
  return (
    <label className="ui-field">
      <span className="ui-field-label">{label}</span>
      {children}
      {hint && !error && <span className="ui-field-hint">{hint}</span>}
      {error && <span className="ui-field-error">{error}</span>}
    </label>
  );
}

// Table with optional toolbar; columns: [{key, label, render?, width?}]
export function DataTable({ columns, rows, rowKey = "id", empty, toolbar, loading }) {
  return (
    <div className="ui-table-card">
      {toolbar && <div className="ui-table-toolbar">{toolbar}</div>}
      {loading ? (
        <div className="ui-card-body">
          <Skeleton lines={5} />
        </div>
      ) : rows.length === 0 ? (
        empty
      ) : (
        <div className="ui-table-scroll">
          <table className="ui-table">
            <thead>
              <tr>
                {columns.map((column) => (
                  <th key={column.key} style={column.width ? { width: column.width } : undefined}>
                    {column.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row[rowKey]}>
                  {columns.map((column) => (
                    <td key={column.key}>{column.render ? column.render(row) : row[column.key]}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function useEscape(open, onClose) {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (event) => event.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);
}

export function Modal({ open, title, description, onClose, children, footer, size = "md" }) {
  useEscape(open, onClose);
  if (!open) return null;
  return (
    <div className="ui-overlay" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div className={`ui-modal ui-modal-${size}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="ui-modal-head">
          <div>
            <h2>{title}</h2>
            {description && <p>{description}</p>}
          </div>
          <IconButton icon={X} label="Close" onClick={onClose} />
        </div>
        <div className="ui-modal-body">{children}</div>
        {footer && <div className="ui-modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Drawer({ open, title, description, onClose, children, footer }) {
  useEscape(open, onClose);
  if (!open) return null;
  return (
    <div className="ui-overlay ui-overlay-drawer" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <aside className="ui-drawer" role="dialog" aria-modal="true" aria-label={title}>
        <div className="ui-modal-head">
          <div>
            <h2>{title}</h2>
            {description && <p>{description}</p>}
          </div>
          <IconButton icon={X} label="Close" onClick={onClose} />
        </div>
        <div className="ui-drawer-body">{children}</div>
        {footer && <div className="ui-modal-foot">{footer}</div>}
      </aside>
    </div>
  );
}

export function ConfirmDialog({ open, title, message, confirmLabel = "Delete", tone = "danger", busy, onConfirm, onClose }) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      size="sm"
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant={tone === "danger" ? "danger" : "primary"} onClick={onConfirm} disabled={busy}>
            {busy ? "Working…" : confirmLabel}
          </Button>
        </>
      }
    >
      <p className="ui-confirm-text">{message}</p>
    </Modal>
  );
}

// ---- Toasts ----
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const nextId = useRef(1);

  const push = useCallback((message, tone = "success") => {
    const id = nextId.current++;
    setToasts((list) => [...list, { id, message, tone }]);
    window.setTimeout(() => setToasts((list) => list.filter((toast) => toast.id !== id)), 4000);
  }, []);

  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="ui-toasts" aria-live="polite">
        {toasts.map((toast) => (
          <div key={toast.id} className={`ui-toast ui-tone-${toast.tone}`}>
            {toast.tone === "danger" ? <AlertTriangle size={16} /> : <CheckCircle2 size={16} />}
            <span>{toast.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
