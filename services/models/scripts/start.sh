#!/usr/bin/env bash
set -euo pipefail

# Simple helper to run the FastAPI app in a local venv.
# - Creates .venv if missing
# - Installs requirements
# - Starts Uvicorn server

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

PYTHON_BIN=${PYTHON_BIN:-python3}
VENV_DIR=${VENV_DIR:-.venv}
PORT=${PORT:-8000}
RELOAD=${RELOAD:-0}

if [ ! -d "$VENV_DIR" ]; then
  echo "[setup] Creating virtual environment in $VENV_DIR"
  "$PYTHON_BIN" -m venv "$VENV_DIR"
fi

source "$VENV_DIR/bin/activate"

echo "[setup] Upgrading pip and wheel"
pip install --upgrade pip wheel

if [ -f requirements.txt ]; then
  echo "[setup] Installing dependencies from requirements.txt"
  pip install -r requirements.txt
else
  echo "[warn] requirements.txt not found; installing minimal runtime deps"
  pip install fastapi "uvicorn[standard]"
fi

UVICORN_ARGS=("app:app" "--host" "0.0.0.0" "--port" "$PORT")
if [ "$RELOAD" = "1" ]; then
  UVICORN_ARGS+=("--reload")
fi

echo "[run] Starting server on http://0.0.0.0:$PORT"
exec uvicorn "${UVICORN_ARGS[@]}"

