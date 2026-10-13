
import math
import os

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(
    title="CampusPulse AI Embedding Service",
    version="1.1.0",
)

HF_TOKEN = os.getenv("HF_TOKEN")
MODEL_ID = "sentence-transformers/all-MiniLM-L6-v2"
HF_API_URL = (
    "https://router.huggingface.co/hf-inference/models/"
    + MODEL_ID
)


class EmbeddingRequest(BaseModel):
    text: str = Field(min_length=1)


class EmbeddingResponse(BaseModel):
    text: str
    dimension: int
    embedding: list[float]


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "CampusPulse AI Embedding Service",
    }


@app.post("/embed", response_model=EmbeddingResponse)
def generate_embedding(request: EmbeddingRequest):
    text = request.text.strip()

    if not text:
        raise HTTPException(
            status_code=400,
            detail="Text cannot be empty",
        )

    if not HF_TOKEN:
        raise HTTPException(
            status_code=500,
            detail="HF_TOKEN is not configured",
        )

    headers = {
        "Authorization": f"Bearer {HF_TOKEN}",
        "Content-Type": "application/json",
    }

    payload = {
        "inputs": text,
        "normalize": True,
    }

    try:
        with httpx.Client(timeout=60.0) as client:
            response = client.post(
                HF_API_URL,
                headers=headers,
                json=payload,
            )
            response.raise_for_status()
            data = response.json()

    except httpx.HTTPStatusError as exc:
        # Do not return provider response bodies or secrets to clients.
        status = exc.response.status_code
        if status in (401, 403):
            detail = "Hugging Face authentication or access failed"
        elif status == 429:
            detail = "Hugging Face rate limit reached"
        else:
            detail = f"Hugging Face inference failed ({status})"

        raise HTTPException(
            status_code=502,
            detail=detail,
        ) from exc

    except (httpx.RequestError, ValueError) as exc:
        raise HTTPException(
            status_code=502,
            detail="Could not obtain embeddings from Hugging Face",
        ) from exc

    # Accept a vector [384] or a single-item batch [[384]].
    if (
        isinstance(data, list)
        and len(data) == 1
        and isinstance(data[0], list)
        and len(data[0]) == 384
        and all(isinstance(x, (int, float)) for x in data[0])
    ):
        data = data[0]

    if (
        not isinstance(data, list)
        or len(data) != 384
        or not all(isinstance(x, (int, float)) for x in data)
    ):
        raise HTTPException(
            status_code=502,
            detail="Embedding provider did not return a 384-value vector",
        )

    embedding = [float(x) for x in data]

    if not all(math.isfinite(x) for x in embedding):
        raise HTTPException(
            status_code=502,
            detail="Embedding provider returned invalid values",
        )

    # Ensure the output is normalized, as in the original service.
    norm = math.sqrt(sum(x * x for x in embedding))
    if norm == 0:
        raise HTTPException(
            status_code=502,
            detail="Embedding provider returned a zero vector",
        )

    embedding = [x / norm for x in embedding]

    return EmbeddingResponse(
        text=text,
        dimension=len(embedding),
        embedding=embedding,
    )
