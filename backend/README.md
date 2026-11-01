# JARVIS AI backend — planned milestone

The first Android milestone works with local Android speech recognition and device text-to-speech. The online AI backend is not connected yet.

Planned backend:
- Python FastAPI service
- Ollama-compatible chat endpoint
- Configurable model name
- Authentication token supplied through environment variables
- No private API keys committed to this repository

Do not expose an unauthenticated backend to the public internet. Add authentication, rate limits, and HTTPS before deployment.
