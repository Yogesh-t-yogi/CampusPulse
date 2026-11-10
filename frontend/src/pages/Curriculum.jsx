
import { useEffect, useState } from "react";
import api from "../apiClient";
import {
  SubjectEditor,
  TopicEditor,
  UnitEditor,
} from "../components/CurriculumEditors";
import {
  getProcessingStatus,
  processSyllabus as runProcessing,
  waitForProcessing,
} from "../syllabusProcessing";

function Curriculum({ syllabusId }) {
  const [syllabus, setSyllabus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [expandedSubjects, setExpandedSubjects] = useState({});
  const [expandedUnits, setExpandedUnits] = useState({});
  const [searchTerm, setSearchTerm] = useState("");
  const [processing, setProcessing] = useState(false);
  const [processingError, setProcessingError] = useState("");
  const [reloadVersion, setReloadVersion] = useState(0);
  const [editMode, setEditMode] = useState(false);
  const reload = () => setReloadVersion((version) => version + 1);

  useEffect(() => {
    let cancelled = false;

    async function fetchCurriculum() {
      setSyllabus(null);
      setError("");
      setExpandedSubjects({});
      setExpandedUnits({});
      setSearchTerm("");
      setProcessingError("");

      if (!syllabusId) {
        setError("Select a syllabus to explore its curriculum.");
        setLoading(false);
        return;
      }

      const token = sessionStorage.getItem("campuspulse_token");

      if (!token) {
        setError("Your login session is missing. Please log in again.");
        setLoading(false);
        return;
      }

      setLoading(true);

      try {
        const response = await api.get(
          `/api/syllabus/${syllabusId}/full`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          }
        );

        if (cancelled) return;
        setSyllabus(response.data);

        // If processing is still running, wait for it and then reload.
        const status = await getProcessingStatus(syllabusId, token).catch(
          () => ({})
        );
        if (!cancelled && status.status === "PROCESSING") {
          setProcessing(true);
          waitForProcessing(syllabusId, token)
            .catch((waitError) => {
              if (!cancelled) setProcessingError(waitError.message);
            })
            .finally(() => {
              if (!cancelled) {
                setProcessing(false);
                setReloadVersion((version) => version + 1);
              }
            });
        } else if (!cancelled && status.status === "FAILED" && status.message) {
          setProcessingError(status.message);
        }
      } catch (err) {
        if (cancelled) return;

        console.error("Failed to load curriculum:", {
          status: err.response?.status,
          url: err.config?.url,
          message: err.message,
        });

        if (err.response?.status === 401) {
          setError("Your session has expired. Please log in again.");
        } else if (err.response?.status === 403) {
          setError("You are not authorized to access this syllabus.");
        } else if (err.response?.status === 404) {
          setError("This syllabus could not be found.");
        } else if (!err.response) {
          setError("Cannot connect to the backend on port 8081.");
        } else {
          setError("Failed to load curriculum. Please try again.");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    fetchCurriculum();

    return () => {
      cancelled = true;
    };
  }, [syllabusId, reloadVersion]);

  const processSyllabus = async () => {
    const token = sessionStorage.getItem("campuspulse_token");
    if (!token) {
      setProcessingError("Your login session is missing. Please log in again.");
      return;
    }

    setProcessing(true);
    setProcessingError("");
    try {
      // Runs in the background on the server; wait until it finishes.
      await runProcessing(syllabusId, token);

      setReloadVersion((version) => version + 1);
    } catch (err) {
      console.error("Failed to process syllabus:", {
        status: err.response?.status,
        response: err.response?.data,
        url: err.config?.url,
        message: err.message,
      });
      setProcessingError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          err.response?.data?.detail ||
          err.message ||
          "Could not extract curriculum from this syllabus."
      );
    } finally {
      setProcessing(false);
    }
  };

  const subjects = Array.isArray(syllabus?.subjects)
    ? syllabus.subjects
    : [];

  const totalUnits = subjects.reduce(
    (sum, subject) =>
      sum + (Array.isArray(subject.units) ? subject.units.length : 0),
    0
  );

  const totalTopics = subjects.reduce(
    (sum, subject) =>
      sum +
      (subject.units || []).reduce(
        (unitSum, unit) =>
          unitSum + (Array.isArray(unit.topics) ? unit.topics.length : 0),
        0
      ),
    0
  );

  // Every unit of the syllabus, for "Move to unit" in the topic editor.
  const allUnits = subjects.flatMap((subject) =>
    (subject.units || []).map((unit, index) => ({
      id: unit.id,
      label: `${subject.name} · Unit ${unit.unitNumber ?? index + 1}${unit.name ? ` — ${unit.name}` : ""}`,
    }))
  );

  const normalizedSearch = searchTerm.trim().toLowerCase();

  const filteredSubjects = subjects
    .map((subject) => {
      const units = (subject.units || [])
        .map((unit) => {
          const topics = (unit.topics || []).filter((topic) => {
            if (!normalizedSearch) return true;

            return (
              topic.name?.toLowerCase().includes(normalizedSearch) ||
              topic.description?.toLowerCase().includes(normalizedSearch) ||
              unit.name?.toLowerCase().includes(normalizedSearch) ||
              subject.name?.toLowerCase().includes(normalizedSearch)
            );
          });

          const unitMatches =
            unit.name?.toLowerCase().includes(normalizedSearch) ||
            String(unit.unitNumber ?? "").includes(normalizedSearch);

          return {
            ...unit,
            topics: normalizedSearch && unitMatches
              ? unit.topics || []
              : topics,
            matches:
              !normalizedSearch ||
              unitMatches ||
              topics.length > 0,
          };
        })
        .filter((unit) => unit.matches);

      const subjectMatches =
        subject.name?.toLowerCase().includes(normalizedSearch) ||
        subject.code?.toLowerCase().includes(normalizedSearch);

      return {
        ...subject,
        units: normalizedSearch && subjectMatches
          ? subject.units || []
          : units,
        matches:
          !normalizedSearch ||
          subjectMatches ||
          units.length > 0,
      };
    })
    .filter((subject) => subject.matches);

  const toggleSubject = (id) => {
    setExpandedSubjects((previous) => ({
      ...previous,
      [id]: !(previous[id] ?? true),
    }));
  };

  const toggleUnit = (id) => {
    setExpandedUnits((previous) => ({
      ...previous,
      [id]: !(previous[id] ?? true),
    }));
  };

  const expandAll = () => {
    const subjectState = {};
    const unitState = {};

    subjects.forEach((subject) => {
      subjectState[subject.id] = true;

      (subject.units || []).forEach((unit) => {
        unitState[unit.id] = true;
      });
    });

    setExpandedSubjects(subjectState);
    setExpandedUnits(unitState);
  };

  const collapseAll = () => {
    const subjectState = {};
    const unitState = {};

    subjects.forEach((subject) => {
      subjectState[subject.id] = false;

      (subject.units || []).forEach((unit) => {
        unitState[unit.id] = false;
      });
    });

    setExpandedSubjects(subjectState);
    setExpandedUnits(unitState);
  };

  if (loading) {
    return (
      <main className="page-content cp-curriculum">
        <div className="cc-state">
          <div className="cc-spinner" />
          <h2>Loading curriculum</h2>
          <p>Organizing your syllabus into subjects and topics...</p>
        </div>
      </main>
    );
  }

  if (error || !syllabus) {
    return (
      <main className="page-content cp-curriculum">
        <div className="cc-state cc-state-card">
          <span className="cc-state-icon">!</span>
          <h1>Curriculum Explorer</h1>
          <p>{error || "Curriculum data is unavailable."}</p>
          <button
            className="cc-button cc-button-primary"
            onClick={() => setReloadVersion((version) => version + 1)}
          >
            Try again
          </button>
        </div>
      </main>
    );
  }

  return (
    <main className="page-content cp-curriculum">
      <section className="cc-hero">
        <div>
          <span className="cc-eyebrow">LEARNING CONTENT · CURRICULUM</span>
          <h1>Curriculum Explorer</h1>
          <p>
            Explore your syllabus, navigate units, and discover
            the topics covered by your academic curriculum.
          </p>
        </div>

        <div className="cc-hero-icon" aria-hidden="true">
          <span>▤</span>
        </div>
      </section>

      <section className="cc-syllabus-banner">
        <div className="cc-document-icon" aria-hidden="true">
          PDF
        </div>

        <div className="cc-syllabus-info">
          <span className="cc-eyebrow">SELECTED SYLLABUS</span>
          <h2>{syllabus.name || "Untitled syllabus"}</h2>
          <p>{syllabus.fileName || "Curriculum document"}</p>
        </div>

        <span className="cc-ready-badge">
          <span /> Available
        </span>
      </section>

      <section className="cc-summary-grid">
        <article className="cc-summary-card">
          <span className="cc-summary-icon cc-purple">▤</span>
          <div>
            <span>Subjects</span>
            <strong>{subjects.length}</strong>
          </div>
        </article>

        <article className="cc-summary-card">
          <span className="cc-summary-icon cc-blue">▦</span>
          <div>
            <span>Units</span>
            <strong>{totalUnits}</strong>
          </div>
        </article>

        <article className="cc-summary-card">
          <span className="cc-summary-icon cc-green">✓</span>
          <div>
            <span>Topics</span>
            <strong>{totalTopics}</strong>
          </div>
        </article>
      </section>

      {subjects.length === 0 && (
        <section className="cc-empty" aria-live="polite">
          <h2>No curriculum has been extracted yet</h2>
          <p>
            The PDF is saved. Process it to extract its subjects, units, and
            topics.
          </p>
          {processingError && <p role="alert">{processingError}</p>}
          <button
            type="button"
            className="cc-button cc-button-primary"
            onClick={processSyllabus}
            disabled={processing}
          >
            {processing ? "Extracting curriculum..." : "Extract syllabus content"}
          </button>
        </section>
      )}

      <section className="cc-explorer">
        <div className="cc-explorer-header">
          <div>
            <span className="cc-eyebrow">SYLLABUS CONTENT</span>
            <h2>Subjects and units</h2>
            <p>
              Select any subject or unit to explore its topics.
            </p>
          </div>
          <span className="cc-subject-count">
            {subjects.length} subjects
          </span>
        </div>

        <div className="cc-toolbar">
          <div className="cc-search">
            <span aria-hidden="true">⌕</span>
            <input
              type="search"
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              placeholder="Search subjects, units, or topics..."
              aria-label="Search curriculum"
            />
            {searchTerm && (
              <button
                type="button"
                onClick={() => setSearchTerm("")}
                aria-label="Clear search"
              >
                ×
              </button>
            )}
          </div>

          <div className="cc-toolbar-actions">
            <button
              type="button"
              className="cc-button"
              onClick={expandAll}
            >
              Expand all
            </button>
            <button
              type="button"
              className="cc-button"
              onClick={collapseAll}
            >
              Collapse all
            </button>
            <button
              type="button"
              className={editMode ? "cp-button cp-button-primary" : "cp-button"}
              onClick={() => setEditMode((value) => !value)}
              aria-pressed={editMode}
            >
              {editMode ? "Done editing" : "Edit curriculum"}
            </button>
          </div>
        </div>

        {filteredSubjects.length === 0 ? (
          <div className="cc-empty">
            <span>⌕</span>
            <h3>No matching curriculum content</h3>
            <p>Try another search term.</p>
          </div>
        ) : (
          <div className="cc-subject-list">
            {filteredSubjects.map((subject, subjectIndex) => {
              const subjectOpen =
                normalizedSearch ||
                (expandedSubjects[subject.id] ?? true);

              return (
                <article className="cc-subject" key={subject.id}>
                  <button
                    type="button"
                    className="cc-subject-heading"
                    onClick={() => toggleSubject(subject.id)}
                    aria-expanded={Boolean(subjectOpen)}
                  >
                    <span className="cc-subject-number">
                      {String(subjectIndex + 1).padStart(2, "0")}
                    </span>

                    <span className="cc-subject-title">
                      <strong>{subject.name}</strong>
                      <span>
                        {subject.code
                          ? `Code: ${subject.code} · `
                          : ""}
                        {(subject.units || []).length} units
                      </span>
                    </span>

                    <span className="cc-chevron">
                      {subjectOpen ? "−" : "+"}
                    </span>
                  </button>

                  {editMode && (
                    <SubjectEditor
                      syllabusId={syllabusId}
                      subject={subject}
                      onChanged={reload}
                    />
                  )}

                  {subjectOpen && (
                    <div className="cc-unit-list">
                      {(subject.units || []).map((unit, unitIndex) => {
                        const unitOpen =
                          normalizedSearch ||
                          (expandedUnits[unit.id] ?? true);

                        return (
                          <article className="cc-unit" key={unit.id}>
                            <button
                              type="button"
                              className="cc-unit-heading"
                              onClick={() => toggleUnit(unit.id)}
                              aria-expanded={Boolean(unitOpen)}
                            >
                              <span className="cc-unit-icon">▦</span>
                              <span className="cc-unit-title">
                                <strong>
                                  {unit.unitNumber != null
                                    ? `Unit ${unit.unitNumber}`
                                    : `Unit ${unitIndex + 1}`}
                                  {unit.name ? ` — ${unit.name}` : ""}
                                </strong>
                                <span>
                                  {(unit.topics || []).length} topics
                                </span>
                              </span>
                              <span className="cc-chevron">
                                {unitOpen ? "−" : "+"}
                              </span>
                            </button>

                            {editMode && (
                              <UnitEditor
                                syllabusId={syllabusId}
                                unit={unit}
                                onChanged={reload}
                              />
                            )}

                            {unitOpen && (
                              <div className="cc-topic-list">
                                {(unit.topics || []).length === 0 ? (
                                  <p className="cc-topic-empty">
                                    No topics available for this unit.
                                  </p>
                                ) : (
                                  (unit.topics || []).map((topic, topicIndex) => (
                                    <div
                                      className="cc-topic"
                                      key={topic.id ?? topicIndex}
                                    >
                                      <span className="cc-topic-dot" />
                                      <div>
                                        <strong>{topic.name}</strong>
                                        {topic.description && (
                                          <p>{topic.description}</p>
                                        )}
                                        {editMode && (
                                          <TopicEditor
                                            syllabusId={syllabusId}
                                            topic={topic}
                                            units={allUnits.filter(
                                              (item) => item.id !== unit.id
                                            )}
                                            onChanged={reload}
                                          />
                                        )}
                                      </div>
                                    </div>
                                  ))
                                )}
                              </div>
                            )}
                          </article>
                        );
                      })}
                    </div>
                  )}
                </article>
              );
            })}
          </div>
        )}
      </section>

      <footer className="cc-footer">
        CampusPulse · Curriculum intelligence
      </footer>
    </main>
  );
}

export default Curriculum;
