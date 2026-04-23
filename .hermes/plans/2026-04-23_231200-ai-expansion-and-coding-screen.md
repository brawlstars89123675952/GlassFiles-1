# AI Expansion + Coding Screen Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Expand GlassFiles AI so the user can add Kimi, Grok, and ChatGPT API keys, choose model versions per provider, support image/video-capable models with proper media rendering in chat, and add a separate coding/agent-oriented AI screen.

**Architecture:** Replace the current provider-as-model enum approach with a provider catalog + model catalog + capability metadata. Keep the current chat UI style, but separate provider credentials/settings from per-session model choice. Add a dedicated coding AI screen sharing storage/network primitives with the regular AI chat while exposing coding/agent model lists and coding-focused actions.

**Tech Stack:** Kotlin, Jetpack Compose, SharedPreferences, existing `AiManager`, `ChatHistoryManager`, existing GlassFiles theme/localization system.

---

## Current context / findings

### Existing files already relevant
- `app/src/main/java/com/glassfiles/data/ai/AiManager.kt`
- `app/src/main/java/com/glassfiles/data/ai/ChatHistoryManager.kt`
- `app/src/main/java/com/glassfiles/ui/screens/AiChatScreen.kt`
- `app/src/main/java/com/glassfiles/data/Localization.kt`

### Important current limitations
1. `AiProvider` currently mixes **vendor + specific model** in one enum.
2. Only Gemini + Qwen are supported today.
3. Key storage is inside `GeminiKeyStore`, which is now misnamed for a multi-provider future.
4. Chat history persists only `role` and `content`, but not rich media payloads robustly enough for future image/video generations.
5. Chat UI displays user-attached images correctly, but generated media support is not represented as structured message content.
6. There is no separate coding/agent screen; coding is mixed into normal chat.
7. The user explicitly wants images to render visually in chat, not as broken text/symbol garbage.

### Product constraints from user request
- Add API key support for:
  - Kimi
  - Grok
  - ChatGPT
- Allow version/model selection for each provider.
- Include models capable of image generation and video generation.
- Render generated images/media properly in chat.
- Add separate AI coding screen for coding/agent-capable models.
- This is planning only for now; do not implement in this turn.

---

## Proposed design

## 1. Data model refactor

### Replace current `AiProvider` enum role
Keep backward compatibility short-term, but move toward these models:

```kotlin
enum class AiVendor {
    GEMINI,
    QWEN,
    OPENAI,
    XAI,
    KIMI
}

enum class AiMediaCapability {
    TEXT,
    VISION_INPUT,
    IMAGE_GENERATION,
    VIDEO_GENERATION,
    FILES,
    CODING,
    AGENTIC
}

data class AiModelInfo(
    val id: String,
    val vendor: AiVendor,
    val label: String,
    val description: String,
    val capabilities: Set<AiMediaCapability>,
    val category: String,
    val enabledByDefault: Boolean = true
)

data class AiProviderConfig(
    val vendor: AiVendor,
    val apiKey: String = "",
    val baseUrl: String = "",
    val region: String = "",
    val selectedChatModelId: String = "",
    val selectedCodingModelId: String = "",
    val selectedImageModelId: String = "",
    val selectedVideoModelId: String = ""
)
```

### Why this refactor is needed
Current enum values like `GEMINI_FLASH`, `QWEN3_CODER_PLUS` encode too many concerns in a single type. The user now wants:
- provider credential setup,
- separate model versions,
- multiple modalities,
- coding/agent-only screen.

That requires provider-level storage and model-level selection.

---

## 2. Credential and settings storage redesign

### Replace `GeminiKeyStore` with provider-agnostic storage
Create a new storage object, likely in:
- `app/src/main/java/com/glassfiles/data/ai/AiProviderStore.kt`

Responsibilities:
- save/load API keys for Gemini, Qwen, OpenAI, xAI(Grok), Kimi
- keep existing Gemini/Qwen migration path
- store selected default model IDs per provider + per use case
- store optional provider-specific settings:
  - Gemini proxy/base URL
  - Qwen region
  - OpenAI custom base URL if needed later
  - Kimi base URL if needed later

### Suggested persisted keys
```kotlin
ai_key_gemini
ai_key_qwen
ai_key_openai
ai_key_xai
ai_key_kimi
ai_proxy_gemini
ai_region_qwen
ai_model_chat_gemini
ai_model_chat_qwen
ai_model_chat_openai
ai_model_chat_xai
ai_model_chat_kimi
ai_model_coding_*
ai_model_image_*
ai_model_video_*
```

### Migration step
On first load:
- read old `GeminiKeyStore` values,
- copy them into new store,
- continue reading old values as fallback for a transition period.

---

## 3. Supported provider/model catalog

Create a dedicated catalog file:
- `app/src/main/java/com/glassfiles/data/ai/AiModelCatalog.kt`

### Initial provider/model groups to support

#### Gemini
Chat/vision/coding models:
- Gemini 2.5 Flash
- Gemini 2.5 Pro
- Gemini 2.5 Flash Lite

Image/video-capable entries can be represented in catalog only if actual API integration is planned for them.

#### Qwen / existing
Preserve current useful models:
- Qwen Plus
- Qwen Turbo
- Qwen3 Max
- Qwen coder models
- Qwen vision models

#### ChatGPT / OpenAI
Examples:
- GPT-4.1
- GPT-4.1 mini
- GPT-4o
- GPT-4o mini
- o3 / o4-mini (if user wants reasoning/coding grouping)
- image-capable OpenAI entries as explicit generation models
- video-capable entries only if actually supported via API path chosen later

#### Grok / xAI
Examples:
- Grok-4
- Grok-3
- Grok-3 mini
- Grok Vision model entries if available in chosen API path
- image generation entries if supported in integration plan

#### Kimi / Moonshot
Examples:
- kimi-k2 / current flagship
- kimi-latest style aliases if officially supported
- long-context entries
- coding-capable entries if they are available in the target API

### Important rule for implementation phase
Only wire models that match a documented API endpoint the app can actually call. The UI should not show fake capabilities. During implementation, capability flags in the catalog must match real request paths.

---

## 4. Message model for rich media rendering

Current message model:
```kotlin
data class ChatMessage(
    val role: String,
    val content: String,
    val imageBase64: String? = null,
    val fileContent: String? = null
)
```

This is too limited for generated media.

### Replace with richer structure
```kotlin
enum class ChatAttachmentType {
    INPUT_IMAGE,
    GENERATED_IMAGE,
    GENERATED_VIDEO,
    FILE,
    ARCHIVE,
    AUDIO
}

data class ChatAttachment(
    val type: ChatAttachmentType,
    val mimeType: String = "",
    val localPath: String? = null,
    val remoteUrl: String? = null,
    val base64: String? = null,
    val fileName: String = "",
    val previewText: String = ""
)

data class ChatMessage(
    val role: String,
    val content: String,
    val attachments: List<ChatAttachment> = emptyList(),
    val modelId: String = "",
    val vendor: String = ""
)
```

### Why this matters
To avoid broken symbols/garbage instead of proper images:
- do **not** dump binary/base64 into visible text content,
- keep media payload in structured attachments,
- render image/video cards directly in Compose,
- only show textual fallback when rendering fails.

### Rendering requirement
For generated image attachment:
- if local file path exists → render via Coil/AsyncImage or Bitmap decode
- if URL exists → render via `AsyncImage`
- if base64 exists → decode into bitmap and render image composable
- never append raw base64 to chat text bubble

For generated video attachment:
- show video card with thumbnail placeholder + file/URL metadata
- allow open/save/share
- inline playback can be phase 2 if needed

---

## 5. AI manager architecture update

### Split responsibilities inside AI layer
Current `AiManager` does too much in one file. Refactor into:
- `AiManager.kt` — orchestration entrypoints
- `AiProviderStore.kt` — saved keys/settings
- `AiModelCatalog.kt` — model metadata
- optional provider clients:
  - `OpenAiClient.kt`
  - `XAiClient.kt`
  - `KimiClient.kt`
  - keep Gemini/Qwen logic where practical, or split too if file gets too large

### Core APIs to expose
```kotlin
suspend fun chat(
    model: AiModelInfo,
    messages: List<ChatMessage>,
    providerConfig: AiProviderConfig,
    onChunk: (String) -> Unit
): AiChatResult

suspend fun generateImage(
    model: AiModelInfo,
    prompt: String,
    providerConfig: AiProviderConfig
): AiGenerationResult

suspend fun generateVideo(
    model: AiModelInfo,
    prompt: String,
    providerConfig: AiProviderConfig
): AiGenerationResult
```

### Result models
```kotlin
data class AiChatResult(
    val text: String,
    val attachments: List<ChatAttachment> = emptyList()
)

data class AiGenerationResult(
    val attachments: List<ChatAttachment>,
    val rawText: String = ""
)
```

### Integration strategy by provider
- Gemini/Qwen: adapt existing chat code first.
- OpenAI/ChatGPT: likely OpenAI-compatible chat/generation endpoints.
- Grok/xAI: use documented xAI API endpoints / OpenAI-compatible path if applicable.
- Kimi: use official Moonshot/Kimi-compatible endpoints.

Implementation phase must verify endpoint compatibility before wiring each provider.

---

## 6. AI chat screen redesign requirements

File likely to heavily change:
- `app/src/main/java/com/glassfiles/ui/screens/AiChatScreen.kt`

### Main goals
1. Keep current polished dark UI style.
2. Replace single mixed settings dialog with provider-aware settings.
3. Replace current model picker with grouped provider/model/version picker.
4. Add generation mode selection:
   - Chat
   - Image
   - Video
5. Render generated media correctly inside chat.

### UI structure proposal
#### In normal AI chat screen
Top bar:
- current active provider badge
- current selected model/version
- settings button
- mode switch chip row

Settings dialog / screen:
- cards for each provider:
  - Gemini
  - Qwen
  - ChatGPT
  - Grok
  - Kimi
- each card has:
  - API key input
  - optional provider-specific settings
  - default chat model selector
  - default image model selector (if supported)
  - default video model selector (if supported)
  - default coding model selector (if supported)

Model picker:
- grouped by provider first
- grouped by category second
- show badges:
  - Vision
  - Image
  - Video
  - Coding
  - Agent
  - Files

### Message rendering requirements
When assistant returns generated image:
- show image bubble/card with actual image preview
- long-press or action row: save / share / open
- no raw JSON/base64 in text output

When assistant returns generated video:
- show a compact card with thumbnail/label
- show file size if known, provider/model tag, and actions: save/open/share
- do not dump URL/blob text unless expanded intentionally

---

## 7. Dedicated AI coding screen

### Create a separate screen
Suggested file:
- `app/src/main/java/com/glassfiles/ui/screens/AiCodingScreen.kt`

### Purpose
This screen is for coding-capable and agent-capable models only.
It should not replace the normal AI chat screen.

### Candidate model filters
Show only models with capabilities containing:
- `CODING`
- or `AGENTIC`

### Screen structure
Top bar:
- Coding AI title
- model picker
- provider badge
- settings shortcut

Main body layout:
- chat/history area
- coding quick prompts
- code/task cards
- optional workspace/context panel

Bottom composer:
- prompt input
- attach file/folder/archive
- code-specific quick actions

### Coding-specific actions
- Explain code
- Fix code
- Refactor code
- Generate file
- Generate shell command
- Generate patch
- Convert snippet to another language

### Agent-oriented behavior
Since user asked for coding models “как агенты”, phase 1 can provide:
- coding-oriented prompt presets,
- richer file/folder attachments,
- structured code output handling,
- direct “save code” and “open in editor/terminal” actions.

Phase 2 can later add real multi-step tool-driven agent orchestration if the app architecture supports it.

### Shared components to reuse
Reuse from `AiChatScreen.kt` where sensible:
- chat history mechanics
- message bubble rendering
- file/image/archive attachment helpers
- code block rendering
- model card UI patterns

But keep the coding screen a separate file and separate entrypoint.

---

## 8. Chat history persistence update

File:
- `app/src/main/java/com/glassfiles/data/ai/ChatHistoryManager.kt`

### Needed changes
Current history only saves:
- role
- content

It must be expanded to save:
- attachments
- provider/vendor
- model id
- chat mode / screen type if needed

### Suggested additions
```kotlin
data class ChatSession(
    val id: String,
    val title: String,
    val provider: String,
    val modelId: String,
    val screenType: String, // "chat" or "coding"
    val messages: List<ChatMessage>,
    val createdAt: Long,
    val updatedAt: Long
)
```

### Migration strategy
When loading old history JSON:
- default `modelId = ""`
- default `screenType = "chat"`
- parse missing attachments as empty list

This prevents breaking old chats.

---

## 9. Navigation / app integration

Need to inspect where AI entrypoint is registered, then add:
- standard AI Chat entry
- separate AI Coding entry

Likely files to inspect during implementation:
- main navigation/root screen files
- any menu/dashboard/home screen listing AI tools

### UX recommendation
Keep existing AI button opening regular chat.
Add an additional entry:
- `AI Coding`
- or `AI Agent`

If there is an AI hub already, split it into two tiles.

---

## 10. Localization updates

File:
- `app/src/main/java/com/glassfiles/data/Localization.kt`

Add strings for:
- ChatGPT
- Grok
- Kimi
- API key labels
- provider settings labels
- image generation
- video generation
- coding screen labels
- coding quick actions
- save/open/share generated media
- image/video model support badges
- fallback text like `Image generated`, `Video generated`, `Open media`, `Save media`

Avoid hardcoding new labels directly in UI.

---

## 11. Rich media rendering rules

This is a must-have because the user explicitly complained about image display quality.

### Rules
1. Never place raw base64/image bytes inside displayed message text.
2. Represent generated media as structured attachments.
3. Use image composables for image attachments.
4. Use cards/placeholders for video attachments.
5. Keep textual description separate from binary payload.
6. When provider returns markdown/image URL payloads, normalize them before rendering.

### Rendering fallback order for images
1. local file path
2. remote URL
3. base64 decode
4. fallback text card: `Image generated, preview unavailable`

### Rendering fallback order for videos
1. local file path
2. remote URL
3. fallback card with metadata and action buttons

---

## 12. Phased implementation plan

### Phase 1 — foundation
1. Add provider/model catalog.
2. Add provider store and migrate old Gemini/Qwen keys.
3. Refactor `ChatMessage` and `ChatSession` persistence.
4. Keep old chat functionality working for Gemini/Qwen.

### Phase 2 — provider expansion
5. Add OpenAI/ChatGPT provider config and chat support.
6. Add Grok/xAI provider config and chat support.
7. Add Kimi provider config and chat support.
8. Add per-provider model selection UI.

### Phase 3 — media generation
9. Add image-generation request flow for providers/models that support it.
10. Add video-generation request flow where practical.
11. Render image/video attachments in chat properly.

### Phase 4 — coding screen
12. Add `AiCodingScreen.kt`.
13. Add coding-model-only picker and coding quick actions.
14. Wire navigation entry.

### Phase 5 — polish
15. Localization pass.
16. Session migration verification.
17. UI polish for provider badges, capability chips, media cards.

---

## Files likely to change

### Core AI layer
- `app/src/main/java/com/glassfiles/data/ai/AiManager.kt`
- `app/src/main/java/com/glassfiles/data/ai/ChatHistoryManager.kt`
- `app/src/main/java/com/glassfiles/data/ai/AiProviderStore.kt` *(new)*
- `app/src/main/java/com/glassfiles/data/ai/AiModelCatalog.kt` *(new)*
- `app/src/main/java/com/glassfiles/data/ai/OpenAiClient.kt` *(new, likely)*
- `app/src/main/java/com/glassfiles/data/ai/XAiClient.kt` *(new, likely)*
- `app/src/main/java/com/glassfiles/data/ai/KimiClient.kt` *(new, likely)*

### UI
- `app/src/main/java/com/glassfiles/ui/screens/AiChatScreen.kt`
- `app/src/main/java/com/glassfiles/ui/screens/AiCodingScreen.kt` *(new)*
- navigation/root/home screen files that register AI entrypoints *(to inspect during implementation)*

### Strings
- `app/src/main/java/com/glassfiles/data/Localization.kt`

### Project log
- `WORKLOG_GITHUB_MODULES.md` only if user wants unified worklog there
- preferably also add a dedicated AI worklog if this becomes large

---

## Risks / tradeoffs

### Risk 1: provider API divergence
OpenAI, xAI, Kimi, Gemini, and Qwen have different payload shapes and streaming behavior.
**Mitigation:** normalize at client layer and return app-level result models.

### Risk 2: oversized `AiChatScreen.kt`
It is already large and multifunctional.
**Mitigation:** extract reusable composables and provider/settings/model picker components.

### Risk 3: media rendering regressions
If message schema migration is careless, old chats may break.
**Mitigation:** backward-compatible JSON parsing with defaults.

### Risk 4: fake capability claims
Some models may not truly support image/video generation through the exact API path used.
**Mitigation:** only expose capability badges backed by implemented request flows.

### Risk 5: coding screen duplication
Blind copy-paste of `AiChatScreen` will become hard to maintain.
**Mitigation:** share lower-level bubble/input/model-picker components.

---

## Validation checklist for implementation phase

### Data / storage
- existing Gemini/Qwen keys still work after migration
- old chat sessions still open
- selected models persist per provider

### Chat behavior
- normal text chat works for old providers
- ChatGPT / Grok / Kimi keys can be entered and saved
- provider model versions can be chosen per provider

### Media behavior
- generated image appears visually in chat
- generated image is not rendered as raw text/base64 garbage
- generated video shows as a media card, not broken text
- save/open/share actions work for generated media

### Coding screen
- separate coding screen exists
- only coding/agent-capable models are shown there
- file/archive/code workflows feel coding-oriented

---

## Open questions for implementation phase

1. Which exact official endpoints should be used for:
   - OpenAI image generation
   - xAI image generation
   - Kimi image/video generation
2. Should generated videos be inline-playable in phase 1, or just shown as cards with open/save actions?
3. Should AI chat and AI coding share history, or have separate session lists?
   - recommended default: separate session types under same storage model

---

## Recommended execution order

1. Inspect navigation entrypoints for current AI screen registration.
2. Introduce provider/model catalog + provider store.
3. Migrate `ChatMessage` / `ChatSession` schema safely.
4. Refactor `AiManager` for provider-agnostic dispatch.
5. Upgrade regular `AiChatScreen` settings/model selection UI.
6. Add structured image rendering for generated image attachments.
7. Add structured video card rendering.
8. Add `AiCodingScreen.kt`.
9. Wire new navigation entry.
10. Do final localization/polish pass.

---

## Suggested commit groups during implementation

1. `refactor: add ai provider store and model catalog`
2. `refactor: migrate ai chat sessions to structured attachments`
3. `feat: add openai grok and kimi provider settings`
4. `feat: add rich media rendering for ai image and video results`
5. `feat: add dedicated ai coding screen`
6. `chore: update ai localization and worklog`
