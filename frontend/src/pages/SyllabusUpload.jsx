
import { useRef, useState } from "react";
import api from "../apiClient";
import { Upload, FileText, X, CheckCircle, AlertCircle } from "lucide-react";
import { processSyllabus } from "../syllabusProcessing";

function SyllabusUpload({ token, onUploaded }) {
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
    <div className="syllabus-upload-page">
      <div className="upload-card">
        <div className="upload-header">
          <h2>Upload Syllabus</h2>
          <p>
            Upload your syllabus PDF to extract and organize subjects,
            units, and topics using AI.
          </p>
        </div>

        <form onSubmit={handleUpload}>
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
          Your syllabus will be uploaded to your CampusPulse backend.
        </p>
      </div>
    </div>
  );
}

export default SyllabusUpload;
