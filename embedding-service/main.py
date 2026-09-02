from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
import faiss
import numpy as np

app = FastAPI(title="Embedding and FAISS Service")
model = SentenceTransformer("all-MiniLM-L6-v2")
DIMENSION = 384
indexes = {}
metadata = {}


class EmbedRequest(BaseModel):
    text: str


class AddRequest(BaseModel):
    peer_id: str
    cid: str
    filename: str
    embedding: list[float]


class SearchRequest(BaseModel):
    peer_id: str
    query: str
    top_k: int = 3


@app.post("/embed")
def embed(request: EmbedRequest):
    vector = model.encode(request.text, normalize_embeddings=True)
    return {
        "dimension": int(vector.shape[0]),
        "embedding": vector.astype(np.float32).tolist()
    }


@app.post("/faiss/add")
def add_to_faiss(request: AddRequest):
    vector = np.asarray([request.embedding], dtype=np.float32)

    if vector.shape[1] != DIMENSION:
        raise HTTPException(
            status_code=400,
            detail=f"Embedding deve ter dimensão {DIMENSION}"
        )

    if request.peer_id not in indexes:
        indexes[request.peer_id] = faiss.IndexFlatIP(DIMENSION)
        metadata[request.peer_id] = []

    already_exists = any(
        item["cid"] == request.cid
        for item in metadata[request.peer_id]
    )

    if already_exists:
        return {
            "status": "already-indexed",
            "size": indexes[request.peer_id].ntotal
        }

    indexes[request.peer_id].add(vector)
    metadata[request.peer_id].append({
        "cid": request.cid,
        "filename": request.filename
    })

    return {
        "status": "added",
        "size": indexes[request.peer_id].ntotal
    }


@app.post("/faiss/search")
def search_faiss(request: SearchRequest):
    if request.peer_id not in indexes or indexes[request.peer_id].ntotal == 0:
        return {"results": []}

    query_vector = model.encode(
        request.query,
        normalize_embeddings=True
    )
    query_vector = np.asarray([query_vector], dtype=np.float32)

    amount = min(request.top_k, indexes[request.peer_id].ntotal)
    distances, ids = indexes[request.peer_id].search(query_vector, amount)

    results = []
    for score, index in zip(distances[0], ids[0]):
        if index >= 0:
            results.append({
                **metadata[request.peer_id][int(index)],
                "score": float(score)
            })

    return {"results": results}