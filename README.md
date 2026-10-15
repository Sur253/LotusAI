# Lotus AI 1.1

This version connects the existing Tamil/Tanglish UI to the Gemini API.

Features:
- Tamil/Tanglish AI chat
- Voice input -> AI chat
- Video picker -> Gemini video understanding
- Settings screen for Gemini API key
- Java/Kotlin JVM target 17 aligned for GitHub Actions

Important:
For a real production app, do NOT ship a personal Gemini API key inside the APK. The safer architecture is to put the Gemini key on your own backend and let the app call that backend.

Setup:
1. Create a Gemini API key in Google AI Studio.
2. Build/install the APK.
3. Open Lotus AI -> ⚙ Settings.
4. Paste the API key and press SAVE.
5. Type a question and press SEND.
6. For video AI, choose a video first, then ask a question and press SEND.

The app uses Gemini's Files API for video upload and waits for the video to become ACTIVE before asking the model about it.
