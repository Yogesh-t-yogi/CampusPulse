
import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import api from "../apiClient";
import { Upload, FileText, X, CheckCircle, AlertCircle } from "lucide-react";
import { processSyllabus } from "../syllabusProcessing";
import { Alert, Button, Card, Field, Page, PageHeader } from "../ui";

function SyllabusUpload({ token, onUploaded, currentUser }) {
  const isStudent = currentUser?.role === "STUDENT";
  // ?revisionOf=<id>: this upload is a new version of that syllabus (keeps its name, program, semester).
  const [searchParams, setSearchParams] = useSearchParams();
  const revisionOf = searchParams.get("revisionOf") || "";
  const [revisionName, setRevisionName] = useState("");
  useEffect(() => {
    if (!revisionOf) return;
    let cancelled = false;
    api
      .get(`/api/syllabus/${revisionOf}`)
      .then(({ data }) => !cancelled && setRevisionName(data?.name || `#${revisionOf}`))
      .catch(() => !cancelled && setRevisionName(`#${revisionOf}`));
    return () => {
      cancelled = true;
    };
  }, [revisionOf]);
  const [programs, setPrograms] = useState([]);
  const [programId, setProgramId] = useState("");
  const [semester, setSemester] = useState("");

  // Programs to tag the syllabus with: own department for HOD/faculty, whole college for admins.
  useEffect(() => {
    let cancelled = false;
    api
      .get("/api/org/programs")
      .then(({ data }) => {
        if (cancelled) return;
        const list = Array.isArray(data) ? data : [];
        const ownDepartment = currentUser?.department?.id;
        const scoped =
          ["HOD", "FACULTY"].includes(currentUser?.role) && ownDepartment
            ? list.filter((item) => item.departmentId === ownDepartment)
            : list;
        setPrograms(scoped);
        if (isStudent && currentUser?.program?.id) setProgramId(String(currentUser.program.id));
      })
      .catch(() => !cancelled && setPrograms([]));
    return () => {
      cancelled = true;
    };
  }, [currentUser, isStudent]);

  const selectedProgram = programs.find((item) => String(item.id) === String(programId));
  const semesterCount = selectedProgram?.durationSemesters || 8;
  const inputRef = useRef(null);

  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  // Select a PDF file
  const handleFileChange = (event) => {
    const selectedFile = event.target.files?.[0];

    setMessage("");
    setError("");

    if (!selectedFile) return;

    if (
      selectedFile.type !== "application/pdf" &&
      !selectedFile.name.toLowerCase().endsWith(".pdf")
    ) {
      setError("Please select a PDF file.");
      event.target.value = "";
      return;
    }

    if (selectedFile.size > 10 * 1024 * 1024) {
      setError("File size must not exceed 10 MB.");
      event.target.value = "";
      return;
    }

    setFile(selectedFile);
  };

  // Upload and process the syllabus
  const handleUpload = async (event) => {
    event.preventDefault();

    if (!file) {
      setError("Please select a syllabus PDF first.");
      return;
    }

    if (!token) {
      setError("Your session has expired. Please log in again.");
      return;
    }

    setLoading(true);
    setError("");
    setMessage("");

    try {
      // STEP 1: Upload PDF and save extracted text
      const formData = new FormData();
      formData.append("file", file);
      if (programId) formData.append("programId", programId);
      if (semester) formData.append("semester", semester);
      if (revisionOf) formData.append("revisionOf", revisionOf);

      const uploadResponse = await api.post(
        `/api/syllabus/upload`,
        formData,
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }
      );

      // STEP 2: Get the ID returned by the backend
      const uploadedSyllabusId = uploadResponse.data?.id;

      if (!uploadedSyllabusId) {
        throw new Error(
          "PDF was uploaded, but the backend did not return a syllabus ID."
        );
      }

      const uploadedSyllabus = {
        ...uploadResponse.data,
        id: uploadedSyllabusId,
      };

      if (onUploaded) {
        await onUploaded(uploadedSyllabus, false);
      }

      // STEP 3: Process the saved syllabus using AI. It runs in the
      // background; we poll its status and show each stage.
      setMessage("PDF uploaded. Processing syllabus content...");

      const finalStatus = await processSyllabus(
        uploadedSyllabusId,
        token,
        (status) => status.message && setMessage(status.message)
      );

      const hasWarnings = finalStatus.status === "READY_WITH_WARNINGS";
      const processingResult = {
        embeddingFailures: hasWarnings ? 1 : 0,
        embeddingWarning: hasWarnings ? finalStatus.message : "",
      };

      // STEP 4: Refresh the syllabus list in the parent component
      if (onUploaded) {
        await onUploaded({
          ...uploadedSyllabus,
          processing: processingResult,
        });
      }

      // STEP 5: Show success and clear the selected file
      setMessage(
        processingResult?.embeddingFailures > 0
          ? "Curriculum extracted and saved. Embeddings need attention; see the notice above."
          : "Syllabus uploaded and processed successfully!"
      );
      setFile(null);

      if (inputRef.current) {
        inputRef.current.value = "";
      }
    } catch (err) {
      console.error("Syllabus upload or processing failed:", {
        url: err.config?.url,
        method: err.config?.method,
        status: err.response?.status,
        response: err.response?.data,
        message: err.message,
      });

      setError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          err.message ||
          "Syllabus upload or processing failed."
      );
    } finally {
      setLoading(false);
    }
  };

  // Remove selected file
  const handleRemoveFile = () => {
    setFile(null);
    setError("");
    setMessage("");

    if (inputRef.current) {
      inputRef.current.value = "";
    }
  };

  return (
    <Page>
      <PageHeader
        eyebrow="Step 1 of 4"
        title="Upload syllabus"
        description="Upload a syllabus PDF. CampusPulse extracts its subjects, units and topics with AI and indexes them for skill matching."
      />

      {revisionOf && (
        <Alert tone="info">
          New version of <strong>{revisionName || "…"}</strong>. It keeps that syllabus's name, program and
          semester; the old version stays so you can compare them in the report's History tab.{" "}
          <Button variant="ghost" onClick={() => setSearchParams({}, { replace: true })}>
            Upload as a new syllabus instead
          </Button>
        </Alert>
      )}

      <div className="ui-grid ui-grid-main">
      <div className="upload-card cp-upload-card">
        <form onSubmit={handleUpload}>
          <div className="ui-form-row" style={{ marginBottom: 16 }}>
            <Field
              label="Program"
              hint={isStudent ? "Your program." : "Which course this syllabus belongs to."}
            >
              <select
                value={programId}
                disabled={isStudent || loading}
                onChange={(event) => {
                  setProgramId(event.target.value);
                  setSemester("");
                }}
              >
                <option value="">No program</option>
                {programs.map((item) => (
                  <option key={item.id} value={String(item.id)}>
                    {item.name}
                    {item.regulation ? ` (${item.regulation})` : ""}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Semester">
              <select value={semester} disabled={loading} onChange={(event) => setSemester(event.target.value)}>
                <option value="">Not set</option>
                {Array.from({ length: semesterCount }, (_, index) => index + 1).map((value) => (
                  <option key={value} value={String(value)}>
                    Semester {value}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <p className="ui-muted" style={{ margin: "0 0 16px" }}>
            {isStudent
              ? "Your uploads stay private to you."
              : "Shared with your department and with students of the chosen program. You can change this later in Syllabi."}
          </p>
          <div
            className="upload-dropzone"
            onClick={() => inputRef.current?.click()}
            onDragOver={(event) => event.preventDefault()}
            onDrop={(event) => {
              event.preventDefault();

              const droppedFile = event.dataTransfer.files?.[0];

              if (droppedFile && inputRef.current) {
                const transfer = new DataTransfer();
                transfer.items.add(droppedFile);
                inputRef.current.files = transfer.files;

                handleFileChange({
                  target: inputRef.current,
                });
              }
            }}
            role="button"
            tabIndex={0}
            onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                inputRef.current?.click();
              }
            }}
          >
            <div className="upload-icon">
              <Upload size={28} />
            </div>

            <h3>Drag and drop your PDF here</h3>
            <p>or click to browse files on your computer</p>

            <input
              ref={inputRef}
              type="file"
              accept=".pdf,application/pdf"
              onChange={handleFileChange}
              hidden
            />
          </div>

          {file && (
            <div className="selected-file">
              <div className="file-icon">
                <FileText size={24} />
              </div>

              <div className="file-details">
                <strong>{file.name}</strong>
                <span>
                  {(file.size / (1024 * 1024)).toFixed(2)} MB
                </span>
              </div>

              <button
                type="button"
                onClick={handleRemoveFile}
                disabled={loading}
                aria-label="Remove selected file"
              >
                <X size={20} />
                Remove
              </button>
            </div>
          )}

          {error && (
            <div className="upload-error" role="alert">
              <AlertCircle size={18} />
              <span>{error}</span>
            </div>
          )}

          {message && (
            <div
              className="upload-message"
              role="status"
              aria-live="polite"
            >
              {!loading && <CheckCircle size={18} />}
              <span>{message}</span>
            </div>
          )}

          <button
            type="submit"
            className="upload-button"
            disabled={loading || !file}
          >
            {loading
              ? "Uploading and processing..."
              : "Upload syllabus"}
          </button>
        </form>

        <p className="upload-note">
          PDF up to 10 MB. Scanned pages are read with OCR.
        </p>
      </div>

      <Card title="What happens next" description="Usually under a minute; long syllabi take longer.">
        <ol className="ui-steps">
          <li><span className="ui-step-dot">1</span>Text is read from the PDF (OCR for scans)</li>
          <li><span className="ui-step-dot">2</span>AI extracts subjects, units and topics</li>
          <li><span className="ui-step-dot">3</span>Topics are indexed for skill matching</li>
          <li><span className="ui-step-dot">4</span>You review and edit the curriculum</li>
        </ol>
      </Card>
      </div>
    </Page>
  );
}

export default SyllabusUpload;
