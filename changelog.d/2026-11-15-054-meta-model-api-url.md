# 054 — Default Llama address → Meta Model API (api.meta.ai)

Meta's docs now use `https://api.meta.ai/v1` (OpenAI-compatible chat completions); the old
`https://api.llama.com/compat/v1` address appears retired. Default `LLAMA_API_URL` changed in
application.properties, .env.example and docker-compose.

Owner's key was rejected by both addresses ("invalid_api_key", checked with the model-list call only),
so the key itself must be replaced in the Meta developer console. Model names (`LLAMA_MODEL`,
`LLAMA_VISION_MODEL`) must match what that console lists for the key.
