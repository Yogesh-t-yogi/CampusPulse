import math
from functools import lru_cache
from pathlib import Path

from fastembed import TextEmbedding
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(
    title="CampusPulse AI Embedding Service",
    version="1.3.0",
)

MODEL_ID = "sentence-transformers/all-MiniLM-L6-v2"


@lru_cache(maxsize=1)
def get_embedding_model() -> TextEmbedding:
    cache_dir = Path.home() / ".cache" / "fastembed"
    return TextEmbedding(
        model_name=MODEL_ID,
        cache_dir=str(cache_dir),
    )


class EmbeddingRequest(BaseModel):
    text: str = Field(min_length=1)


MAX_BATCH = 128


class BatchEmbeddingRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=MAX_BATCH)


class BatchEmbeddingResponse(BaseModel):
    dimension: int
    embeddings: list[list[float]]


class EmbeddingResponse(BaseModel):
    text: str
    dimension: int
    embedding: list[float]


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "CampusPulse local AI embedding service",
        "model": MODEL_ID,
    }


@app.post("/embed", response_model=EmbeddingResponse)
def generate_embedding(request: EmbeddingRequest):
    text = request.text.strip()

    if not text:
        raise HTTPException(
            status_code=400,
            detail="Text cannot be empty",
        )

    embedding = normalize(next(get_embedding_model().embed([text])))
    return EmbeddingResponse(
        text=text,
        dimension=len(embedding),
        embedding=embedding,
    )


@app.post("/embed/batch", response_model=BatchEmbeddingResponse)
def generate_embeddings(request: BatchEmbeddingRequest):
    texts = [text.strip() for text in request.texts]
    if any(not text for text in texts):
        raise HTTPException(status_code=400, detail="Texts cannot be empty")

    embeddings = [normalize(data) for data in get_embedding_model().embed(texts)]
    return BatchEmbeddingResponse(dimension=384, embeddings=embeddings)


def normalize(data) -> list[float]:
    if len(data) != 384:
        raise HTTPException(
            status_code=500,
            detail=f"Local embedding model returned {len(data)} values; expected 384",
        )

    embedding = [float(value) for value in data]
    if not all(math.isfinite(value) for value in embedding):
        raise HTTPException(
            status_code=500,
            detail="Local embedding model returned invalid values",
        )

    norm = math.sqrt(sum(value * value for value in embedding))
    if norm == 0:
        raise HTTPException(
            status_code=500,
            detail="Local embedding model returned a zero vector",
        )

    return [value / norm for value in embedding]
