import { useState } from "react";
import { Download, FileSpreadsheet, Upload } from "lucide-react";
import api, { errorMessage } from "../apiClient";
import { Alert, Badge, Button, Field, Modal } from "../ui";

const ACTION_TONE = { CREATE: "success", UPDATE: "info", ERROR: "danger" };

function saveBlob(data, filename) {
  const url = URL.createObjectURL(new Blob([data]));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

// Bulk onboarding: template → choose file → preview → import → download credentials.
function StudentImport({ open, onClose, onDone }) {
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const reset = () => {
    setFile(null);
    setPreview(null);
    setResult(null);
    setError("");
  };

  const close = () => {
    if (result) onDone?.();
    reset();
    onClose();
  };

  const downloadTemplate = async () => {
    try {
      const response = await api.get("/api/admin/users/import/template", { responseType: "blob" });
      saveBlob(response.data, "campuspulse-students-template.xlsx");
    } catch (err) {
      setError(errorMessage(err, "Couldn't download the template."));
    }
  };

  const send = async (apply) => {
    if (!file) return;
    setBusy(true);
    setError("");
    const data = new FormData();
    data.append("file", file);
    try {
      const response = await api.post("/api/admin/users/import", data, { params: { apply } });
      if (apply) setResult(response.data);
      else setPreview(response.data);
    } catch (err) {
      setError(errorMessage(err, "Couldn't read this file."));
    } finally {
      setBusy(false);
    }
  };

  const credentials = (result?.rows || []).filter((row) => row.temporaryPassword);

  const downloadCredentials = async () => {
    try {
      const response = await api.post(
        "/api/admin/users/credentials-sheet",
        credentials.map(({ name, email, rollNumber, temporaryPassword }) => ({
          name,
          email,
          rollNumber: rollNumber || "",
          temporaryPassword,
        })),
        { responseType: "blob" }
      );
      saveBlob(response.data, "campuspulse-credentials.xlsx");
    } catch (err) {
      setError(errorMessage(err, "Couldn't create the credentials sheet."));
    }
  };

  const shown = result || preview;

  return (
    <Modal
      open={open}
      size="lg"
      title="Import students from Excel"
      description="Add or update many people at once. New accounts get a one-time password."
      onClose={close}
      footer={
        result ? (
          <>
            <Button icon={Download} variant="primary" onClick={downloadCredentials} disabled={credentials.length === 0}>
              Download logins ({credentials.length})
            </Button>
            <Button onClick={close}>Done</Button>
          </>
        ) : (
          <>
            <Button onClick={close}>Cancel</Button>
            {preview ? (
              <Button
                variant="primary"
                icon={Upload}
                onClick={() => send(true)}
                disabled={busy || preview.created + preview.updated === 0}
              >
                {busy ? "Importing…" : `Import ${preview.created + preview.updated} rows`}
              </Button>
            ) : (
              <Button variant="primary" onClick={() => send(false)} disabled={!file || busy}>
                {busy ? "Checking…" : "Check file"}
              </Button>
            )}
          </>
        )
      }
    >
      <div className="ui-form">
        {error && <Alert tone="danger">{error}</Alert>}

        {!shown && (
          <>
            <ol className="ui-steps">
              <li>
                <span className="ui-step-dot">1</span>
                <span>
                  Download the template. Its <strong>Codes</strong> sheet lists your department and program codes.{" "}
                  <Button variant="ghost" icon={FileSpreadsheet} onClick={downloadTemplate}>
                    Download template
                  </Button>
                </span>
              </li>
              <li>
                <span className="ui-step-dot">2</span>Fill one person per row (name, email, department code; roll number,
                program code and batch year optional).
              </li>
              <li>
                <span className="ui-step-dot">3</span>Upload it here and check the preview before importing.
              </li>
            </ol>
            <Field label="Excel (.xlsx) or CSV file" hint="Up to 2,000 rows, 5 MB.">
              <input
                type="file"
                accept=".xlsx,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv"
                onChange={(event) => setFile(event.target.files?.[0] || null)}
              />
            </Field>
          </>
        )}

        {shown && (
          <>
            <Alert tone={shown.errors ? "warning" : "success"} title={result ? "Import finished" : "Preview — nothing saved yet"}>
              {shown.created} new · {shown.updated} updated · {shown.errors} with problems
              {shown.errors > 0 && (result ? " (skipped)" : ". Rows with problems are skipped; fix and re-upload to include them.")}
            </Alert>
            {result && credentials.length > 0 && (
              <Alert tone="info">
                Download the logins now — one-time passwords are not shown again. Everyone sets their own password at first sign-in.
              </Alert>
            )}
            <div className="ui-table-card ui-import-table">
              <div className="ui-table-scroll">
                <table className="ui-table">
                  <thead>
                    <tr>
                      <th>Row</th>
                      <th>Person</th>
                      <th>Department · Program</th>
                      <th>Result</th>
                    </tr>
                  </thead>
                  <tbody>
                    {shown.rows.map((row) => (
                      <tr key={row.row}>
                        <td>{row.row}</td>
                        <td>
                          <span className="ui-cell-title">
                            <strong>{row.name || "—"}</strong>
                            <small>
                              {row.email}
                              {row.rollNumber ? ` · ${row.rollNumber}` : ""}
                            </small>
                          </span>
                        </td>
                        <td>
                          <span className="ui-cell-title">
                            <strong>{row.department || "—"}</strong>
                            <small>{row.program || ""}</small>
                          </span>
                        </td>
                        <td>
                          <span className="ui-cell-title">
                            <Badge tone={ACTION_TONE[row.action]}>
                              {row.action === "CREATE" ? "New" : row.action === "UPDATE" ? "Update" : "Problem"}
                            </Badge>
                            <small>{row.message}</small>
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
            {!result && (
              <Button variant="ghost" onClick={reset}>
                Choose another file
              </Button>
            )}
          </>
        )}
      </div>
    </Modal>
  );
}

export default StudentImport;
