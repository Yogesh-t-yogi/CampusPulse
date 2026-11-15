import { Component } from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";

// Catches a crash inside one page so the app shows a message instead of a blank screen.
// Give it a `resetKey` (e.g. the page path) so moving to another page clears the error.
class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null, resetKey: props.resetKey };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  static getDerivedStateFromProps(props, state) {
    if (props.resetKey !== state.resetKey) return { error: null, resetKey: props.resetKey };
    return null;
  }

  componentDidCatch(error, info) {
    console.error("Page crashed:", error, info?.componentStack);
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="ui-page">
        <div className="ui-alert ui-tone-danger" role="alert">
          <AlertTriangle size={18} aria-hidden="true" />
          <div>
            <strong>Something went wrong on this page.</strong>
            <div>Reload to try again. If it keeps happening, note what you clicked and tell the admin.</div>
            <button type="button" className="ui-btn ui-btn-secondary" style={{ marginTop: 10 }} onClick={() => window.location.reload()}>
              <RefreshCw size={16} aria-hidden="true" /> Reload
            </button>
          </div>
        </div>
      </div>
    );
  }
}

export default ErrorBoundary;
