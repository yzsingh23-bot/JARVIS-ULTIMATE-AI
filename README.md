# JARVIS ULTIMATE AI

A native Kotlin Android voice-assistant project targeting Android 12 and newer.

## First milestone

- Dark navy and cyan JARVIS-inspired dashboard
- Tap-to-talk speech recognition
- Spoken responses using Android text-to-speech
- Safe offline commands to open common apps and launch Google Search or Maps
- Clear microphone permission and privacy messaging

## Sarvam AI backend

A secure FastAPI backend starter is in [backend/](backend/). It exposes:
- `GET /health` for service status
- `POST /v1/chat` for Sarvam chat completions

Set `SARVAM_API_KEY` as a backend environment variable. Never commit a real key or put it in Android code or the APK. See [backend/README.md](backend/README.md) for local setup. The current Android client still needs to be wired to the deployed backend URL before end-to-end chat works.

## Build

Open the project in Android Studio, allow Gradle sync, then select **Build > Build APK(s)**. The first milestone is a starter app; wake-word listening and the online AI backend are later milestones and are not yet fully implemented.

## Security

Never commit API keys, passwords, or private tokens. Backend credentials must stay server-side.
