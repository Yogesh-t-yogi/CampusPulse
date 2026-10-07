from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

app = FastAPI(
    title="CampusPulse AI Embedding Service",
    version="1.0.0"
)

print("Loading embedding model...")

model = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")

print("Embedding model loaded successfully.")


class EmbeddingRequest(BaseModel):
    text: str


class EmbeddingResponse(BaseModel):
    text: str
    dimension: int
    embedding: list[float]


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "CampusPulse AI Embedding Service"
    }


@app.post("/embed", response_model=EmbeddingResponse)
def generate_embedding(request: EmbeddingRequest):

    if not request.text.strip():
        raise ValueError("Text cannot be empty")

    vector = model.encode(
        request.text,
        normalize_embeddings=True
    )

    embedding = vector.tolist()

    return EmbeddingResponse(
        text=request.text,
        dimension=len(embedding),
        embedding=embedding
    )