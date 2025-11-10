# Vision Vogue AI

Unified project combining:
- `vision-vogue-models` (Python FastAPI for image analysis and embeddings)
- `vision-vogue-taxonomy` (Spring Boot app with semantic search, widget, and PostgreSQL persistence)

This repo-level project now contains both services' source code and wires them with Postgres for a single bring-up via Docker Compose.

## Prerequisites
- Docker + Docker Compose

## Quick Start

```
cd vision-vogue-ai
docker compose up --build
```

Services:
- Models API (FastAPI): http://localhost:8000/docs
- Taxonomy API (Spring Boot): http://localhost:8080/swagger-ui.html
- Widget: http://localhost:8080/widget/{partnerId}
- File Drop SFTP: sftp/scp on localhost:2222 (user `uploader`/`uploader`)
- Postgres: localhost:5432 (db: `vision_vogue`, user: `postgres`, pass: `postgres`)

The taxonomy app is configured to call the models API via internal Docker DNS name `models-api`.

## Project Layout

- `services/models` — Python FastAPI service (migrated from `vision-vogue-models`)
- `services/taxonomy` — Spring Boot + Angular widget (migrated from `vision-vogue-taxonomy`)

Compose builds from these internal folders; no external repos needed.

## Environment Overrides

You can customize ports and service URLs using standard Spring Boot and Docker env overrides.
For example, to change the taxonomy similarity threshold:

```yaml
services:
  taxonomy:
    environment:
      SEMANTIC_SEARCH_MIN_SIMILARITY_THRESHOLD: 0.4
```

Common overrides:
- `SPRING_DATASOURCE_URL`: JDBC URL for Postgres (default: `jdbc:postgresql://db:5432/vision_vogue`)
- `APP_ANALYZE_URL`: URL for analyzer endpoint (default: `http://models-api:8000/analyze`)
- `EMBEDDING_API_URL`: URL for embeddings endpoint (default: `http://models-api:8000/embed`)

### Uploading Images

Use the baked-in SSH/SFTP service to drop files into the taxonomy watcher:

```bash
scp -P 2222 my-image.jpg uploader@localhost:incoming/
```

The command above maps directly to `data/incoming` inside the taxonomy container, so partner folders (for example `incoming/123/`) can be created locally or through the upload service.

To change credentials, set `USER_NAME`, `USER_PASSWORD`, and related variables under the `file-drop` service in `docker-compose.yml`.

## Development Notes

- First run will download ML models in the models container; subsequent runs are cached in the container layer.
- If you want to persist the HuggingFace cache across runs, you can mount a volume to `/root/.cache/huggingface` in the `models-api` service.
- The taxonomy service depends on `db` and `models-api` and waits for them to be healthy before starting.

## Troubleshooting

- If the taxonomy app cannot reach the models API, ensure the `models-api` container is healthy and that `APP_ANALYZE_URL` and `EMBEDDING_API_URL` point to `http://models-api:8000/...`.
- If database migrations fail, clear the DB volume: `docker volume rm vision-vogue-ai_db_data` and restart.
