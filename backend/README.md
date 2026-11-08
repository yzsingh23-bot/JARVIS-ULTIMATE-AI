# JARVIS Sarvam backend

This FastAPI service keeps the Sarvam subscription key on the server, not inside the Android APK.

## Run locally

1. Install Python 3.10 or newer.
2. Open this folder in a terminal.
3. Create a virtual environment and install dependencies:

   ```bash
   python -m venv .venv
   # Windows: .venv\\Scripts\\activate
   # macOS/Linux: source .venv/bin/activate
   pip install -r requirements.txt
   ```

4. Copy `.env.example` to `.env` and set `SARVAM_API_KEY`.
5. Load the environment variables, then start the server:

   ```bash
   # macOS/Linux
   set -a && . ./.env && set +a
   uvicorn main:app --host 0.0.0.0 --port 8000
   ```

   On Windows PowerShell, set `$env:SARVAM_API_KEY` before running Uvicorn.

6. Open `http://127.0.0.1:8000/health` or `http://127.0.0.1:8000/docs`.

## Chat request

```bash
curl -X POST http://127.0.0.1:8000/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"Hello JARVIS, introduce yourself."}]}'
```

The API key is read only from the backend process environment. Do not add it to Git, Android resources, Gradle BuildConfig, or the APK.

## Android connection status

The backend endpoint is ready, but the existing Android `MainActivity` has not yet been switched from its direct Sarvam call to this service. Deploy this service first, then configure the app to call its HTTPS URL at `POST /v1/chat`. A live Android-to-backend connection and APK build are not claimed by this backend-only change.
