# 053 — Working default AI models, clearer provider errors, retry unreadable pages

## Found (owner's log, 2026-11-15, scanned 6-page PDF failed)
- llama: HTTP 401 — the key is not accepted by Meta's API (api.llama.com: "invalid_api_key").
- gemini: HTTP 404 — `gemini-2.5-flash` "is no longer available to new users".
- groq: HTTP 404 — Groq no longer serves `meta-llama/llama-4-scout-17b-16e-instruct` (not in the key's model list).
Checked each key against its own provider's model list (keys never printed or sent elsewhere).

## What changed
- Defaults: `GEMINI_MODEL` / `GEMINI_VISION_MODEL` = `gemini-3.5-flash` (tested: text + image OK);
  `GROQ_VISION_MODEL` = `qwen/qwen3.8-27b` (accepts images). `.env.example` updated.
- Logs show the model actually used (vision model for page reading) and the provider's own short error
  message (e.g. "This model … is no longer available").
- A provider/model that returns 401/403/404 is skipped for 10 minutes instead of being retried on every
  page (log says to check its key/model in `.env`).
- Pages that could not be read now stay queued: "Re-process" retries them after keys are fixed
  (before, the queue was cleared even when every page failed).
- Tests: pause after a bad key; provider message extraction. 76 backend tests pass.

## Owner action
- Llama: get a valid key from the Llama/Meta Model API console, or if the key is from another host
  (OpenRouter, Together…) set `LLAMA_API_URL` and `LLAMA_MODEL`/`LLAMA_VISION_MODEL` for that host.
  Until then the chain uses Gemini → Groq.
- Syllabus #14 failed under the old behaviour (its queue was cleared): delete it and upload again.

## How to test
Restart backend → upload the scanned PDF → log shows `gemini(gemini-3.5-flash)` reading pages
(llama skipped after the first 401) → topics extracted.
