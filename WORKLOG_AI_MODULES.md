# GlassFiles AI Modules Progress

## 2026-05-03

### ACEMusic music generation
- Added standalone AI music generation flow under the AI hub.
- Added ACEMusic as a dedicated provider with its own API key slot and model capability (`MUSIC_GEN`).
- Kept music generation separate from chat, coding, image generation, and video generation paths.
- Implemented ACEMusic async API flow:
  - submit generation task
  - poll task status
  - download generated audio into app cache
- Added terminal-style music UI with prompt, lyrics, sample mode, model picker, duration, BPM, key, time signature, language, format, batch, seed, diffusion, CFG, and LM controls.
- Added music history, open/share/delete actions, optional system Music library save, and estimated usage records.
- Added in-app audio playback and file-only sharing/download behavior for generated music.

### Verification
- Ran `git diff --check`.
- Local Android build/server-side compile was not run by project preference.

## 2026-05-04

### ACEMusic engine endpoint
- Switched ACEMusic default base URL to `https://ai-api.acemusic.ai/engine/api/engine/`.
- Changed ACEMusic to the single Eruda-observed endpoint:
  - `POST /release_task`
  - no `GET token`
  - no `POST /query_result`
- `release_task` uses `application/x-www-form-urlencoded` with:
  - `Ai_token=<jwt>`
  - `task_id_list=["<uuid>"]`
  - `app=studio-web`
- Generation now creates a fresh UUID task id before submit.
- `Ai_token` is read from ACEMusic API key config when provided; otherwise the saved key is reused as the form token.
- Kept raw HTTP body logging/error extraction for debugging 500 responses.
- Re-enabled Music Generation in the AI hub after the previous ACEMusic pause.
- Updated ACEMusic API key hint and music screen subtitle to the engine endpoint.

### Verification
- Ran `git diff --check`.
- Local Android build was not run by project preference.
