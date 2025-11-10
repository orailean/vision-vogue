# Vision Vogue Models API

FastAPI server exposing endpoints:
- `/analyze`: Combined category + attribute extraction + dominant colors
- `/embed`: Text embeddings from all-MiniLM-L6-v2

Dependencies are in `requirements.txt`. The app entry is `app:app` in `app.py`.

## Run locally (virtualenv)

Prerequisites: Python 3.10+ and internet access (to download models on first run).

```
bash scripts/start.sh           # creates .venv, installs deps, runs server
# Optional:
PORT=9000 RELOAD=1 bash scripts/start.sh
```

Open http://127.0.0.1:8000/docs (or chosen port) for Swagger UI.

## Run with Docker

Build and run:

```
docker build -t vogue-api .
docker run --rm -p 8000:8000 vogue-api
```

Or using docker-compose:

```
docker compose up --build
```

Swagger UI: http://127.0.0.1:8000/docs

## Publish to Docker Hub

Build and push with your own repository name:

```
docker login
docker build -t <username>/vision-vogue-models:latest .
docker push <username>/vision-vogue-models:latest
```

Replace `<username>` (and add a custom tag if desired) before pushing.

## Notes

- The first request triggers model downloads from Hugging Face; subsequent runs are cached.
- If you want to persist the HF cache across container runs, uncomment the `volumes` section in `docker-compose.yml`.
 - Attribute groups: color, pattern, sleeve, neckline, fit, length, material, style, rise, waist, closure, gender (men's/women's/unisex/boys'/girls').

## New: /analyze endpoint

POST `/analyze` (multipart/form-data)
- `file`: image file
- `top_k_category` (int, default 3): top category predictions from ViT
- `top_per_attribute` (int, default 1): number of options returned per attribute group
- `n_colors` (int, default 5): number of dominant colors extracted

Response example:
```
{
  "category": [ {"label": "T_SHIRT_TOP", "confidence": 0.91}, ... ],
  "attributes": {
    "color": [{"label": "black", "confidence": 0.62}],
    "gender": [{"label": "women's", "confidence": 0.68}],
    "pattern": [{"label": "solid", "confidence": 0.71}],
    ...
  },
  "colors": [ {"hex": "#101010", "percent": 0.42}, ... ]
}
```

## New: /embed endpoint

POST `/embed` (application/json)
- `texts`: array of strings to embed
- `normalize` (bool, default true): L2-normalize vectors
- `batch_size` (int, default 32): encode batch size

Response example:
```
{
  "embeddings": [[0.01, -0.02, ...], [0.03, 0.14, ...]],
  "model": "sentence-transformers/all-MiniLM-L6-v2",
  "dim": 384
}
```
