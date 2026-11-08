# JARVIS AI

Native Kotlin Android assistant targeting Android 12+ (tested build target: Moto G60 / Android 12).

## Included in the current Android milestone

- Dark navy and cyan dashboard with an animated Arc Reactor-style microphone control
- "GOOD DAY, SIR." JARVIS branding
- Tap-to-talk microphone input, English default, with English/Hindi/Punjabi voice-input selection
- On-screen typed chat and response history for the current app session
- No text-to-speech playback; replies are displayed on screen
- Local commands: open Camera, WhatsApp, YouTube, Chrome, Gmail, Settings, supported installed apps, Google Search, Maps, time/date, jokes, and alarm-based reminders
- User-started foreground "Hey Jarvis" wake-word service with a persistent Android notification and Stop action
- Home-screen JARVIS widget that opens the app and requests microphone input
- Study-helper and Free Fire India guide behavior through Sarvam chat prompts
- Sarvam Chat Completions integration using `sarvam-105b-conversations`

## Sarvam API key

The app asks for a Sarvam API key when online chat is first used. The key is kept in app memory for that session and is not hard-coded into the APK. It must be entered again after the app process restarts. For a production release, use the included FastAPI backend as the only holder of the secret; do not ship a permanent API key in an APK.

Sarvam Chat Completions V1 docs: https://docs.sarvam.ai/api/token-factory/chat-completions

## Home-screen widget

After installing and opening JARVIS once, long-press an empty area on the Android home screen, choose **Widgets**, find **JARVIS AI**, and drag the JARVIS talk widget onto the home screen. The first tap may ask for microphone permission.

## Build

GitHub Actions builds a debug APK on each push. Open the repository's **Actions** tab and download the `jarvis-ai-debug-apk` artifact from the latest successful run.

## Important platform limits

- Always-on wake-word listening is an opt-in foreground microphone service. Android displays an ongoing notification while it listens; battery management and the speech-recognition provider can affect reliability.
- Voice input language is selectable. The selected Android speech-recognition provider must have the corresponding English, Hindi, or Punjabi recognition pack/service available.
- The current app is a debug/testing build, not a Play Store release. Online AI requires internet access and a valid Sarvam subscription key.
- The FastAPI backend starter exists in `backend/`, but this Android milestone uses the session-entered key directly; backend deployment and client routing are a separate production-hardening step.
- No app can open every protected Android screen or bypass Android permission prompts. Commands are limited to apps and actions Android exposes.

## Security

Never commit API keys, passwords, or private tokens. Do not put permanent API credentials in Android source code or APKs.
