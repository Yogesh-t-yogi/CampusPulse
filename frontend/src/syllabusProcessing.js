import api from "./apiClient";

const POLL_MS = 3000;
const MAX_WAIT_MS = 15 * 60 * 1000;

export function getProcessingStatus(syllabusId, token) {
  return api
    .get(`/api/ai/syllabus/${syllabusId}/status`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    .then((response) => response.data);
}

// Polls until processing is no longer PROCESSING. onProgress gets each status.
// Resolves with the final status object; rejects if it FAILED or took too long.
export async function waitForProcessing(syllabusId, token, onProgress) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < MAX_WAIT_MS) {
    const status = await getProcessingStatus(syllabusId, token);
    onProgress?.(status);

    if (status.status === "FAILED") {
      throw new Error(status.message || "Syllabus processing failed.");
    }
    if (status.status !== "PROCESSING") {
      return status;
    }
    await new Promise((resolve) => setTimeout(resolve, POLL_MS));
  }

  throw new Error(
    "Processing is taking longer than expected. Check the Curriculum page later."
  );
}

// Starts background processing, then waits for it to finish.
export async function processSyllabus(syllabusId, token, onProgress) {
  await api.post(
    `/api/ai/syllabus/${syllabusId}/save`,
    {},
    { headers: { Authorization: `Bearer ${token}` } }
  );
  return waitForProcessing(syllabusId, token, onProgress);
}
