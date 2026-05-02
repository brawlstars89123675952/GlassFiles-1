# GlassFiles GitHub Module

![module](https://img.shields.io/badge/module-GitHub-24292f)
![ui](https://img.shields.io/badge/UI-terminal_style-39d353)
![compose](https://img.shields.io/badge/Android-Compose-58a6ff)
![material3](https://img.shields.io/badge/Material3-not_used-f85149)

GlassFiles GitHub Module is the in-app GitHub client used by GlassFiles.
It brings repositories, code browsing, issues, pull requests, Actions,
settings, security surfaces and AI Agent GitHub tools into one terminal-style
mobile workflow.

The module is built for dense repository work: quick scanning, explicit write
actions, compact dialogs, readable code, and no Material UI surfaces inside
GitHub screens.

## Overview

GlassFiles uses this module to work with GitHub without leaving the app.

It provides:

- **Repository workspace**: browse repositories, branches, files, README files,
  commits, compare views and releases.
- **Collaboration flows**: issues, pull requests, reviews, comments,
  discussions, projects, teams and collaborators.
- **CI and automation**: workflow runs, jobs, logs, artifacts, check runs,
  reruns and manual workflow dispatch.
- **Repository administration**: settings, branch protection, webhooks,
  packages, security alerts and rules.
- **AI Agent bridge**: authenticated GitHub tools exposed through the agent
  executor and approval policy.

## Quick Start

Open the GitHub module from the app navigation, sign in, then choose a
repository.

Typical repository workflow:

1. Open **GitHub**.
2. Select a repository from home, search, profile, organization or recent list.
3. Use the repo tabs to switch between code, issues, pull requests, Actions,
   releases, security and settings.
4. Use terminal-style action buttons for write operations such as edits,
   comments, dispatches, reruns or settings changes.
5. For agent work, call GitHub tools through AI Agent instead of using local
   filesystem tools for authenticated repository operations.

```text
GitHub
├── Home / Explore / Search
├── Repository
│   ├── Code
│   ├── Issues
│   ├── Pull requests
│   ├── Actions
│   ├── Releases
│   ├── Security
│   └── Settings
└── AI Agent GitHub tools
```

## Key Features

### Terminal-Style UI

All GitHub screens use the same visual language as the AI module: dark
surfaces, compact bordered controls, monospace metadata, dense lists and
bottom sheets that look like terminal panels.

Use shared primitives instead of Material components:

```kotlin
AiModuleSurface(...)
AiModuleText(...)
AiModuleIcon(...)
AiModuleIconButton(...)
GitHubTerminalButton(...)
GitHubTerminalSection(...)
```

### Repository Browser

The repository module handles the core GitHub workspace: file tree, README
rendering, code preview, code editing, diffs, branches, commits and compare
views.

Main entry points:

```text
GitHubScreen.kt
GitHubHomeModule.kt
GitHubRepoModule.kt
GitHubCodeEditorModule.kt
GitHubDiffModule.kt
GitHubMarkdownModule.kt
```

### Pull Requests And Issues

Issues, pull requests, comments and review surfaces should stay scannable on
mobile. Lists show compact metadata first; full content opens in a terminal
sheet or detail screen.

Write actions must be explicit:

```text
open item -> inspect content -> choose action -> confirm/write
```

Avoid hidden mutations on row taps.

### Actions And Checks

Actions screens cover workflow runs, jobs, logs, artifacts, check runs, reruns
and dispatch. Logs and check output should render in code-like blocks with
clear loading, empty and error states.

### AI Agent Integration

The AI Agent uses GitHub tools for authenticated repository work.

Relevant files:

```text
app/src/main/java/com/glassfiles/data/ai/agent/AiTool.kt
app/src/main/java/com/glassfiles/data/ai/agent/AgentToolRegistry.kt
app/src/main/java/com/glassfiles/data/ai/agent/GitHubToolExecutor.kt
app/src/main/java/com/glassfiles/ui/screens/AiAgentScreen.kt
```

Rules:

- repository operations go through GitHub tools;
- local tools are for chat/session files and workspace files;
- chat-only mode must not inject repository assumptions;
- destructive or write operations must respect approval policy.

## Architecture

The module is split into a data facade, terminal UI primitives and feature
screens.

```text
app/src/main/java/com/glassfiles/
├── data/github/
│   ├── GitHubManager.kt
│   ├── GitHubRepoSettingsManager.kt
│   ├── GitHubSecretCrypto.kt
│   └── KernelErrorPatterns.kt
└── ui/screens/
    ├── GitHubScreen.kt
    ├── GitHubHomeModule.kt
    ├── GitHubRepoModule.kt
    ├── GitHubSharedUiModule.kt
    ├── GitHubActionsModule.kt
    ├── GitHubCodeEditorModule.kt
    ├── GitHubSecurityModule.kt
    ├── GitHubRepoSettingsModule.kt
    └── GitHub*Module.kt
```

### Data Layer

`GitHubManager.kt` is the main API facade. It owns auth/session helpers,
REST/GraphQL helpers, parsers and most endpoint wrappers.

`GitHubRepoSettingsManager.kt` contains repository settings APIs that are large
enough to keep separate from the main manager.

`GitHubSecretCrypto.kt` handles local secret/token crypto helpers.

`KernelErrorPatterns.kt` contains known error pattern helpers used by
Actions/log analysis flows.

### UI Layer

`GitHubScreen.kt` is the top-level navigation shell.

`GitHubRepoModule.kt` is the repository detail screen and tab host.

`GitHubSharedUiModule.kt` contains shared GitHub terminal components. Add new
reusable GitHub controls there before styling one-off widgets in a feature
screen.

## Module Map

```text
GitHubActionsModule.kt             workflow runs, jobs, logs, artifacts
GitHubAdvancedSearchModule.kt      advanced GitHub search
GitHubBranchProtectionModule.kt    branch protection
GitHubCheckRunsModule.kt           check run presentation
GitHubCodeEditorModule.kt          editor, search, outline, preview, commit
GitHubCollaboratorsModule.kt       collaborators and access
GitHubCompareModule.kt             compare refs and changed files
GitHubDiffModule.kt                reusable diff rendering
GitHubDiscussionsModule.kt         discussions
GitHubExploreModule.kt             explore, search and discovery
GitHubGistsAndDialogsModule.kt     gists and shared dialogs
GitHubMarkdownModule.kt            lightweight README/comment markdown
GitHubNotificationsModule.kt       GitHub notifications
GitHubPackagesModule.kt            packages
GitHubProfileModule.kt             profile and org surfaces
GitHubProjectsModule.kt            projects
GitHubReleasesModule.kt            releases and assets
GitHubRepoSettingsModule.kt        repo settings sections
GitHubRepoSettingsScreen.kt        repo settings screen wrapper
GitHubSecurityModule.kt            alerts, rules and security settings
GitHubSettingsModule.kt            module-level settings
GitHubTeamsModule.kt               teams
GitHubWebhooksModule.kt            webhooks
GitHubGlyphs.kt                    shared terminal glyph constants
```

## Add A Feature

Use this path when adding a GitHub capability:

1. Decide whether the endpoint belongs in `GitHubManager` or
   `GitHubRepoSettingsManager`.
2. Add a small data class if existing models do not fit.
3. Keep endpoint parsing close to the manager method.
4. Add UI in the narrowest existing feature module.
5. Reuse `GitHubSharedUiModule.kt` and `AiModulePrimitives.kt`.
6. Keep loading, empty and error states terminal-style.
7. Add agent tool metadata only when the capability should be model-callable.
8. Update this README if the feature adds a module or new convention.

Example manager-to-screen flow:

```text
GitHubRepoModule.kt
  -> GitHubManager.fetchRepository(...)
  -> render terminal rows/tabs
  -> user opens explicit action
  -> GitHubManager.writeOperation(...)
  -> refresh state or show terminal error
```

## Code Style

The GitHub module must stay visually consistent with the AI module.

Required:

- use `AiModuleTheme`, `AiModuleSurface`, `AiModuleText`, `AiModuleIcon`,
  `AiModuleIconButton` and GitHub terminal primitives;
- use `JetBrainsMono` for terminal text, status labels, code-like labels and
  compact metadata;
- use compact bordered controls with low radius;
- keep top-bar buttons symmetric;
- prefer terminal bottom sheets/dialogs for menus and detail actions;
- keep repeated repo/list items dense and readable.

Do not add Material UI back into GitHub screens:

```text
androidx.compose.material3
DropdownMenu
DropdownMenuItem
MaterialTheme
TextButton
OutlinedTextField
FloatingActionButton
CircularProgressIndicator
```

If a feature needs a missing primitive, add a reusable one instead of copying
inline styling across screens.

## Static Checks

Do not run Gradle for this project unless explicitly requested.

Safe local checks:

```bash
rg -n "androidx\\.compose\\.material3|\\bDropdownMenu\\b|\\bDropdownMenuItem\\b|\\bMaterialTheme\\b|\\bTextButton\\b|\\bOutlinedTextField\\b|\\bFloatingActionButton\\b|\\bCircularProgressIndicator\\b" app/src/main/java/com/glassfiles/ui/screens/GitHub*.kt
git diff --check
```

Expected result for the first command is no matches in `GitHub*.kt` screens.

## Security

GitHub tokens and repository write operations must be treated as sensitive.

Rules:

- never log tokens or secrets;
- keep token storage behind `GitHubSecretCrypto`;
- make write/destructive actions explicit in UI;
- route AI Agent write actions through approval policy;
- keep repository tools separate from local file tools;
- avoid broad permissions for features that only need read access.

## Related Docs

- `GITHUB_MANAGER_MAP.md` - structural map of `GitHubManager.kt`.
- `GITHUB_API_ANALYSIS.md` - API analysis notes.
- `GITHUB_SETTINGS_API.md` - settings API notes.
- `WORKLOG_GITHUB_MODULES.md` - historical worklog.
