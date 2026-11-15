# 050 — AI provider fallback: Llama → Gemini → Groq, with image support (plan 14, Phase B)

## What changed
- `LlmService` now tries several OpenAI-compatible providers in order and moves to the next one on
  rate limit (429), server error, timeout, rejected key (401/403), model/URL not found, empty answer, or —
  for JSON calls — an answer with no readable JSON. Providers without a key are skipped.
  JSON mode is turned off per provider if that provider rejects it.
- New `LlmProvider` / `LlmProviders`: order from `LLM_ORDER` (default `llama,gemini,groq`); a provider left
  out of the order still comes last.
- New `generateFromImages(prompt, jpegs)`: sends page images (base64) to providers that have a vision model
  — the base for reading scanned PDFs in Phase C.
- Settings (`application.properties`, `.env.example`, docker-compose):
  `LLAMA_API_KEY`, `LLAMA_API_URL` (default Meta's `https://api.llama.com/compat/v1/chat/completions`),
  `LLAMA_MODEL`, `LLAMA_VISION_MODEL`; `GEMINI_API_KEY`, `GEMINI_MODEL`, `GEMINI_VISION_MODEL`
  (OpenAI-compatible Gemini endpoint); `GROQ_API_KEY`, `GROQ_MODEL`, `GROQ_VISION_MODEL`; `LLM_ORDER`.
  Old `LLM_API_KEY` still works as the Groq key. Default model names should be checked against each
  provider's current docs.
- `GET /api/health/ai-providers`: order, configured yes/no, model names (never keys).
  Sidebar status: "No AI key set" when none of the three keys exists.
- `start-dev.ps1` warns only when all three keys are empty. Your local `.env` got empty
  `LLAMA_API_KEY=` / `GEMINI_API_KEY=` lines to fill in.
- Tests: `LlmServiceFallbackTest` (10). 65 backend tests pass.

## How to test
1. Put only `GROQ_API_KEY` in `.env` → everything works as before.
2. Add `GEMINI_API_KEY`, set `GROQ_API_KEY` to a wrong value → AI features still work (Gemini answers);
   the backend log shows "AI provider groq failed: key rejected".
3. Open /api/health/ai-providers (signed in) to see which providers are set up.

## Risk
Medium: all AI calls go through the new chain. With only a Groq key, behaviour matches before.
