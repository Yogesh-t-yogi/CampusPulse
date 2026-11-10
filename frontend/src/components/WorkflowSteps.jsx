import { Check } from "lucide-react";
import { isPageAvailable, isStepDone, WORKFLOW_STEPS } from "../workflow";

function WorkflowSteps({ activePage, setActivePage, hasSyllabus, hasJob }) {
  const state = { hasSyllabus, hasJob };

  return (
    <nav className="cp-workflow-steps" aria-label="Analysis workflow">
      {WORKFLOW_STEPS.map((step, index) => {
        const available = isPageAvailable(step.page, state);
        const done = isStepDone(step.page, state);

        return (
          <button
            key={step.page}
            type="button"
            className={`cp-workflow-step${activePage === step.page ? " active" : ""}${done ? " done" : ""}`}
            onClick={() => setActivePage(step.page)}
            disabled={!available}
            aria-current={activePage === step.page ? "step" : undefined}
            title={!available ? "Finish the earlier steps first" : ""}
          >
            <span className="cp-workflow-number">
              {done ? <Check size={14} aria-label="Done" /> : index + 1}
            </span>
            <span>{step.label}</span>
          </button>
        );
      })}
    </nav>
  );
}

export default WorkflowSteps;
