import os
from contextlib import asynccontextmanager

import torch
from fastapi import FastAPI
from FlagEmbedding import FlagReranker
from pydantic import BaseModel, Field


MODEL_NAME = os.getenv("RERANKER_MODEL", "BAAI/bge-reranker-v2-m3")
MAX_LENGTH = int(os.getenv("RERANKER_MAX_LENGTH", "1024"))
model: FlagReranker | None = None


class RerankRequest(BaseModel):
    query: str = Field(min_length=1)
    documents: list[str] = Field(min_length=1, max_length=64)
    top_k: int | None = Field(default=None, ge=1, le=64)


class RerankItem(BaseModel):
    index: int
    score: float


class RerankResponse(BaseModel):
    model: str
    device: str
    results: list[RerankItem]


@asynccontextmanager
async def lifespan(_: FastAPI):
    global model
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = FlagReranker(
        MODEL_NAME,
        use_fp16=device == "cuda",
        devices=[device],
    )
    yield
    model = None


app = FastAPI(title="SmartKB BGE Reranker", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health():
    return {
        "status": "UP" if model is not None else "STARTING",
        "model": MODEL_NAME,
        "device": "cuda" if torch.cuda.is_available() else "cpu",
    }


@app.post("/rerank", response_model=RerankResponse)
def rerank(request: RerankRequest):
    if model is None:
        raise RuntimeError("reranker model is not ready")

    pairs = [(request.query, document) for document in request.documents]
    scores = model.compute_score(
        pairs,
        batch_size=min(16, len(pairs)),
        max_length=MAX_LENGTH,
        normalize=True,
    )
    if not isinstance(scores, list):
        scores = [scores]
    ranked = sorted(
        (RerankItem(index=index, score=float(score)) for index, score in enumerate(scores)),
        key=lambda item: item.score,
        reverse=True,
    )
    if request.top_k is not None:
        ranked = ranked[: request.top_k]

    return RerankResponse(
        model=MODEL_NAME,
        device="cuda" if torch.cuda.is_available() else "cpu",
        results=ranked,
    )
