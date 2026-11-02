const steps = [
  { page: "Upload Syllabus", label: "Upload syllabus" },
  { page: "Job Market", label: "Choose job" },
  { page: "Curriculum", label: "Review curriculum" },
  { page: "Gap Analysis", label: "Analyze gaps" },
  { page: "AI Recommendations", label: "Get recommendations" },
  { page: "Dashboard", label: "Dashboard" },
];

function WorkflowSteps({ activePage, setActivePage, hasSyllabus, hasJob }) {
  const isAvailable = (page) => {
    if (page === "Upload Syllabus") return true;
    if (page === "Job Market") return hasSyllabus;
    if (page === "Curriculum") return hasSyllabus;
    return hasSyllabus && hasJob;
  };

  return (
    <nav className="cp-workflow-steps" aria-label="Analysis workflow">
      {steps.map((step, index) => {
        const available = isAvailable(step.page);

        return (
          <button
            key={step.page}
            type="button"
            className={`cp-workflow-step${activePage === step.page ? " active" : ""}`}
            onClick={() => setActivePage(step.page)}
            disabled={!available}
            aria-current={activePage === step.page ? "step" : undefined}
            title={!available ? "Complete the previous setup steps first" : ""}
          >
            <span className="cp-workflow-number">{index + 1}</span>
            <span>{step.label}</span>
          </button>
        );
      })}
    </nav>
  );
}

export default WorkflowSteps;
