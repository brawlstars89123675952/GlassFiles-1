# GitHub API Coverage Analysis for GlassFiles

Last normalized: 2026-05-03. This matrix reflects the local code and
`WORKLOG_GITHUB_MODULES.md`; keep backlog rows free of already implemented
items.

## ✅ FULLY IMPLEMENTED (Backend + UI)

### Authentication & User
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| Token login | `/user` | ✅ | ✅ | LoginScreen |
| Get current user | `/user` | ✅ | ✅ | Cached user support |
| Get user profile | `/users/{username}` | ✅ | ✅ | ProfileScreen |
| Update profile | `/user` (PATCH) | ✅ | ✅ | SettingsModule |
| Follow/unfollow user | `/user/following/{user}` | ✅ | ✅ | ProfileScreen |
| List followers | `/user/followers` | ✅ | ✅ | SettingsModule |
| List following | `/user/following` | ✅ | ✅ | SettingsModule |
| Block/unblock users | `/user/blocks/{user}` | ✅ | ✅ | SettingsModule |
| Interaction limits | `/user/interaction-limits` | ✅ | ✅ | SettingsModule |

### Repositories
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List user repos | `/user/repos` | ✅ | ✅ | ReposScreen with pagination |
| Search repos | `/search/repositories` | ✅ | ✅ | Public search toggle |
| Create repo | `/user/repos` (POST) | ✅ | ✅ | CreateRepoDialog |
| Delete repo | `/repos/{owner}/{repo}` (DELETE) | ✅ | ✅ | Via menu |
| Get repo contents | `/repos/{owner}/{repo}/contents` | ✅ | ✅ | File browser with branches |
| Get file content | `/repos/{owner}/{repo}/contents/{path}` | ✅ | ✅ | Base64 decode |
| Update file content | `/repos/{owner}/{repo}/contents/{path}` (PUT with sha) | ✅ | ✅ | CodeEditorScreen commit flow |
| Upload file | `/repos/{owner}/{repo}/contents/{path}` (PUT) | ✅ | ✅ | UploadDialog |
| Delete file | `/repos/{owner}/{repo}/contents/{path}` (DELETE) | ✅ | ✅ | DeleteFileDialog |
| Download file | `download_url` | ✅ | ✅ | To Downloads/GlassFiles_Git |
| Clone repo (zip) | `/repos/{owner}/{repo}/zipball` | ✅ | ✅ | With progress callback |
| Upload directory | Git tree API | ✅ | ✅ | Multi-file commit |
| Star/unstar repo | `/user/starred/{owner}/{repo}` | ✅ | ✅ | RepoDetailScreen |
| Fork repo | `/repos/{owner}/{repo}/forks` | ✅ | ✅ | Via menu |
| Watch/unwatch repo | `/repos/{owner}/{repo}/subscription` | ✅ | ✅ | RepoDetailScreen |
| List starred repos | `/user/starred` | ✅ | ✅ | StarredScreen |
| Get README | `/repos/{owner}/{repo}/readme` | ✅ | ✅ | README tab |
| Get languages | `/repos/{owner}/{repo}/languages` | ✅ | ✅ | README tab |
| Get contributors | `/repos/{owner}/{repo}/contributors` | ✅ | ✅ | README tab |
| Search code | `/search/code` | ✅ | ✅ | CodeSearchTab |
| Update repo settings | `/repos/{owner}/{repo}` (PATCH) | ✅ | ✅ | Description, homepage, features, merge settings, archive |
| Repo topics | `/repos/{owner}/{repo}/topics` | ✅ | ✅ | List/replace topics in settings |
| Repo tags | `/repos/{owner}/{repo}/tags` | ✅ | ✅ | Read-only tags list in settings |
| Branch protection rules | `/repos/{owner}/{repo}/branches/{branch}/protection` | ✅ | ✅ | Required checks/reviews/admins/conversation resolution |
| Repo collaborators | `/repos/{owner}/{repo}/collaborators` | ✅ | ✅ | List/add/remove/update permission |
| Repo teams | `/repos/{owner}/{repo}/teams`, `/orgs/{org}/teams/{team_slug}/repos/{owner}/{repo}` | ✅ | ✅ | List org repo teams, add/remove teams, update team permission |

### Branches
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List branches | `/repos/{owner}/{repo}/branches` | ✅ | ✅ | BranchPickerDialog |
| Create branch | `/repos/{owner}/{repo}/git/refs` (POST) | ✅ | ✅ | CreateBranchDialog |
| Delete branch | `/repos/{owner}/{repo}/git/refs/heads/{branch}` (DELETE) | ✅ | ✅ | Via menu |
| Switch branch | `?ref=` param | ✅ | ✅ | Full branch support |

### Commits
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List commits | `/repos/{owner}/{repo}/commits` | ✅ | ✅ | With pagination |
| Get commit diff | `/repos/{owner}/{repo}/commits/{sha}` | ✅ | ✅ | CommitDiffScreen |
| View commit details | `/repos/{owner}/{repo}/commits/{sha}` | ✅ | ✅ | Files, stats, patches |
| Compare commits | `/repos/{owner}/{repo}/compare/{base}...{head}` | ✅ | ✅ | Branch compare, commits, changed files, diff viewer, PR creation |

### Issues
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List issues | `/repos/{owner}/{repo}/issues` | ✅ | ✅ | With pagination, state filter |
| Create issue | `/repos/{owner}/{repo}/issues` (POST) | ✅ | ✅ | CreateIssueDialog |
| Close/reopen issue | `/repos/{owner}/{repo}/issues/{number}` (PATCH) | ✅ | ✅ | IssueDetailScreen |
| Get issue detail | `/repos/{owner}/{repo}/issues/{number}` | ✅ | ✅ | Full detail with labels |
| List comments | `/repos/{owner}/{repo}/issues/{number}/comments` | ✅ | ✅ | IssueDetailScreen |
| Add comment | `/repos/{owner}/{repo}/issues/{number}/comments` (POST) | ✅ | ✅ | Comment input |
| Issue reactions | `/repos/{owner}/{repo}/issues/{number}/reactions` | ✅ | ✅ | Add/list reactions |
| Comment reactions | `/repos/{owner}/{repo}/issues/comments/{id}/reactions` | ✅ | ✅ | Add/list reactions on issue comments |
| List labels | `/repos/{owner}/{repo}/labels` | ✅ | ✅ | SettingsModule |
| Create label | `/repos/{owner}/{repo}/labels` (POST) | ✅ | ✅ | SettingsModule |
| Delete label | `/repos/{owner}/{repo}/labels/{name}` (DELETE) | ✅ | ✅ | SettingsModule |
| List milestones | `/repos/{owner}/{repo}/milestones` | ✅ | ✅ | SettingsModule |
| Create milestone | `/repos/{owner}/{repo}/milestones` (POST) | ✅ | ✅ | SettingsModule |
| Update issue meta | `/repos/{owner}/{repo}/issues/{number}` (PATCH) | ✅ | ✅ | Labels, assignees, milestone |
| List assignees | `/repos/{owner}/{repo}/assignees` | ✅ | ✅ | SettingsModule |

### Issues Advanced
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| Lock/unlock issue | `/repos/{owner}/{repo}/issues/{number}/lock` (PUT/DELETE) | ✅ | ✅ | Lock dialog with GitHub lock reasons |
| Issue timeline | `/repos/{owner}/{repo}/issues/{number}/timeline` | ✅ | ✅ | Full history dialog |
| Issue events | `/repos/{owner}/{repo}/issues/events` | ✅ | ✅ | Repository-wide issue event feed |
| Update comment | `/repos/{owner}/{repo}/issues/comments/{id}` (PATCH) | ✅ | ✅ | Edit existing issue comments |
| Delete comment | `/repos/{owner}/{repo}/issues/comments/{id}` (DELETE) | ✅ | ✅ | Delete comments with confirmation |

### Pull Requests
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List PRs | `/repos/{owner}/{repo}/pulls` | ✅ | ✅ | With pagination |
| Get PR detail | `/repos/{owner}/{repo}/pulls/{number}` | ✅ | ✅ | Mergeability, draft/merged state, stats |
| Create PR | `/repos/{owner}/{repo}/pulls` (POST) | ✅ | ✅ | CreatePRDialog |
| Merge PR | `/repos/{owner}/{repo}/pulls/{number}/merge` (PUT) | ✅ | ✅ | Via menu |
| Submit PR review | `/repos/{owner}/{repo}/pulls/{number}/reviews` (POST) | ✅ | ✅ | Approve/request changes |
| Get PR files | `/repos/{owner}/{repo}/pulls/{number}/files` | ✅ | ✅ | PullRequestDiffScreen |
| View PR diff | `/repos/{owner}/{repo}/pulls/{number}/files` | ✅ | ✅ | PullRequestDiffScreen |
| PR review comments | `/repos/{owner}/{repo}/pulls/{number}/comments` | ✅ | ✅ | Line comments in diff viewer |
| PR check runs | `/repos/{owner}/{repo}/commits/{ref}/check-runs` | ✅ | ✅ | PR detail checks summary and full checks screen |
| Update PR | `/repos/{owner}/{repo}/pulls/{number}` (PATCH) | ✅ | ✅ | Title, body, base, state |
| List PR reviews | `/repos/{owner}/{repo}/pulls/{number}/reviews` | ✅ | ✅ | Review history |
| Get single review | `/repos/{owner}/{repo}/pulls/{number}/reviews/{id}` | ✅ | ✅ | Review detail dialog |
| Update review | `/repos/{owner}/{repo}/pulls/{number}/reviews/{id}` (PUT) | ✅ | ✅ | Pending review edit |
| Delete review | `/repos/{owner}/{repo}/pulls/{number}/reviews/{id}` (DELETE) | ✅ | ✅ | Pending review delete |
| Create review comment | `/repos/{owner}/{repo}/pulls/{number}/comments` (POST) | ✅ | ✅ | Line-level comments |
| Update review comment | `/repos/{owner}/{repo}/pulls/comments/{id}` (PATCH) | ✅ | ✅ | Diff/comment views |
| Delete review comment | `/repos/{owner}/{repo}/pulls/comments/{id}` (DELETE) | ✅ | ✅ | Diff/comment views |
| Squash merge | `/repos/{owner}/{repo}/pulls/{number}/merge` (PUT) with `squash` | ✅ | ✅ | Merge method selector |
| Rebase merge | `/repos/{owner}/{repo}/pulls/{number}/merge` (PUT) with `rebase` | ✅ | ✅ | Merge method selector |
| Request reviewers | `/repos/{owner}/{repo}/pulls/{number}/requested_reviewers` (POST) | ✅ | ✅ | Reviewer request flow |
| Remove reviewers | `/repos/{owner}/{repo}/pulls/{number}/requested_reviewers` (DELETE) | ✅ | ✅ | Reviewer removal flow |
| PR mergeability | `/repos/{owner}/{repo}/pulls/{number}` | ✅ | ✅ | Uses `mergeable` and `mergeable_state` from PR detail |

### Releases
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List releases | `/repos/{owner}/{repo}/releases` | ✅ | ✅ | ReleasesScreen |
| Create release | `/repos/{owner}/{repo}/releases` (POST) | ✅ | ✅ | CreateReleaseDialog |
| Update release | `/repos/{owner}/{repo}/releases/{id}` (PATCH) | ✅ | ✅ | EditReleaseDialog |
| Delete release | `/repos/{owner}/{repo}/releases/{id}` (DELETE) | ✅ | ✅ | With confirmation |
| Upload release asset | `/repos/{owner}/{repo}/releases/{id}/assets` (POST) | ✅ | ✅ | Actions artifacts and manual file picker |
| Download release asset | `browser_download_url` | ✅ | ✅ | To Downloads/GlassFiles_Git |
| Delete release asset | `/repos/{owner}/{repo}/releases/assets/{asset_id}` | ✅ | ✅ | With confirmation |

### GitHub Actions
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List workflows | `/repos/{owner}/{repo}/actions/workflows` | ✅ | ✅ | ActionsTab |
| List workflow runs | `/repos/{owner}/{repo}/actions/runs` | ✅ | ✅ | With live polling |
| Get run jobs | `/repos/{owner}/{repo}/actions/runs/{id}/jobs` | ✅ | ✅ | WorkflowRunDetailScreen |
| Get run logs | `/repos/{owner}/{repo}/actions/runs/{id}/logs` | ✅ | ✅ | Redirect handling |
| Get job logs | `/repos/{owner}/{repo}/actions/jobs/{id}/logs` | ✅ | ✅ | Direct download |
| Rerun workflow | `/repos/{owner}/{repo}/actions/runs/{id}/rerun` (POST) | ✅ | ✅ | Via menu |
| Cancel run | `/repos/{owner}/{repo}/actions/runs/{id}/cancel` (POST) | ✅ | ✅ | Via menu |
| Dispatch workflow | `/repos/{owner}/{repo}/actions/workflows/{id}/dispatches` (POST) | ✅ | ✅ | DispatchWorkflowDialog |
| List artifacts | `/repos/{owner}/{repo}/actions/runs/{id}/artifacts` | ✅ | ✅ | WorkflowRunDetailScreen |
| Download artifact | `/repos/{owner}/{repo}/actions/artifacts/{id}/zip` | ✅ | ✅ | To local file |
| Delete artifact | `/repos/{owner}/{repo}/actions/artifacts/{id}` (DELETE) | ✅ | ✅ | Run detail and repository artifact panel |
| List repository artifacts | `/repos/{owner}/{repo}/actions/artifacts` | ✅ | ✅ | Repository-wide artifacts panel |
| Enable workflow | `/repos/{owner}/{repo}/actions/workflows/{id}/enable` (PUT) | ✅ | ✅ | Workflow toggle |
| Disable workflow | `/repos/{owner}/{repo}/actions/workflows/{id}/disable` (PUT) | ✅ | ✅ | Workflow toggle |
| Rerun failed jobs | `/repos/{owner}/{repo}/actions/runs/{id}/rerun-failed-jobs` (POST) | ✅ | ✅ | Run menu |
| Rerun job | `/repos/{owner}/{repo}/actions/jobs/{id}/rerun` (POST) | ✅ | ✅ | Job action |
| Force cancel run | `/repos/{owner}/{repo}/actions/runs/{id}/force-cancel` (POST) | ✅ | ✅ | Run danger action |
| Delete run logs | `/repos/{owner}/{repo}/actions/runs/{id}/logs` (DELETE) | ✅ | ✅ | Run danger action |
| Delete workflow run | `/repos/{owner}/{repo}/actions/runs/{id}` (DELETE) | ✅ | ✅ | Run danger action |
| Workflow run attempts | `/repos/{owner}/{repo}/actions/runs/{id}/attempts/{attempt}` | ✅ | ✅ | Attempt picker and attempt jobs/logs |
| Workflow usage | `/repos/{owner}/{repo}/actions/workflows/{id}/timing` | ✅ | ✅ | Usage metadata |
| Workflow run usage | `/repos/{owner}/{repo}/actions/runs/{id}/timing` | ✅ | ✅ | Run summary metadata |
| Pending deployments | `/repos/{owner}/{repo}/actions/runs/{id}/pending_deployments` | ✅ | ✅ | Deployment review section |
| Actions cache usage/list/delete | `/repos/{owner}/{repo}/actions/cache/*` | ✅ | ✅ | Caches panel |
| Actions variables CRUD | `/repos/{owner}/{repo}/actions/variables` | ✅ | ✅ | Variables panel |
| Actions secrets CRUD | `/repos/{owner}/{repo}/actions/secrets` | ✅ | ✅ | Secrets panel with public-key encryption |
| Self-hosted runners | `/repos/{owner}/{repo}/actions/runners` | ✅ | ✅ | List/delete plus registration/remove tokens |
| Actions permissions read/write | `/repos/{owner}/{repo}/actions/permissions` | ✅ | ✅ | Settings panel |
| Workflow token permissions read/write | `/repos/{owner}/{repo}/actions/permissions/workflow` | ✅ | ✅ | Settings panel |
| Artifact/log retention read/write | `/repos/{owner}/{repo}/actions/permissions/artifact-and-log-retention` | ✅ | ✅ | Settings panel |
| Matrix job grouping | Local UI over run jobs | - | ✅ | Collapsible prefix groups for large kernel matrices |
| Kernel failure diagnostics | Local asset/cache/remote pattern catalog | - | ✅ | Remote-updatable kernel builder error summaries |

### Gists
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List gists | `/gists` | ✅ | ✅ | GistsScreen |
| Create gist | `/gists` (POST) | ✅ | ✅ | CreateGistDialog |
| Get gist content | `/gists/{id}` | ✅ | ✅ | File viewer |
| Delete gist | `/gists/{id}` (DELETE) | ✅ | ✅ | Via menu |

### Notifications
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List notifications | `/notifications` | ✅ | ✅ | NotificationsScreen |
| Mark as read | `/notifications/threads/{id}` (PATCH) | ✅ | ✅ | Per notification |
| Mark all read | `/notifications` (PUT) | ✅ | ✅ | Bulk action |
| Get thread subscription | `/notifications/threads/{id}/subscription` | ✅ | ✅ | Subscription dialog |
| Set thread subscription | `/notifications/threads/{id}/subscription` (PUT) | ✅ | ✅ | Subscribe or ignore thread |
| Delete thread subscription | `/notifications/threads/{id}/subscription` (DELETE) | ✅ | ✅ | Reset to default |

### Search
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| Search repositories | `/search/repositories` | ✅ | ✅ | Home search and AdvancedSearchScreen |
| Search users | `/search/users` | ✅ | ✅ | AdvancedSearchScreen profile navigation |
| Search issues and PRs | `/search/issues` | ✅ | ✅ | AdvancedSearchScreen with labels/comments/open on GitHub |
| Search commits | `/search/commits` | ✅ | ✅ | AdvancedSearchScreen with repo/sha/author metadata |
| Search topics | `/search/topics` | ✅ | ✅ | AdvancedSearchScreen topic cards |
| Search labels | `/search/labels` | ✅ | ✅ | Repo-scoped AdvancedSearchScreen mode with repository id lookup |

### Organizations
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List orgs | `/user/orgs` | ✅ | ✅ | OrgsScreen |
| List org repos | `/orgs/{org}/repos` | ✅ | ✅ | OrgsScreen |

### Discussions
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List discussions | GraphQL `Repository.discussions` | ✅ | ✅ | DiscussionsScreen with search/category filters |
| Get discussion | GraphQL `Repository.discussion(number:)` | ✅ | ✅ | Full detail with metadata/body |
| Create discussion | GraphQL `createDiscussion` | ✅ | ✅ | Category-aware create dialog |
| Update discussion | GraphQL `updateDiscussion` | ✅ | ✅ | Title/body/category edit dialog |
| Delete discussion | GraphQL `deleteDiscussion` | ✅ | ✅ | Confirmation dialog |
| List discussion categories | GraphQL `Repository.discussionCategories` | ✅ | ✅ | Filter chips and create/edit category picker |
| Discussion comments | GraphQL `Discussion.comments`, `addDiscussionComment` | ✅ | ✅ | Comment list and composer |

### Projects
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List projects | `/repos/{owner}/{repo}/projects` | ✅ | ✅ | Classic projects tab |
| Get project | `/projects/{id}` | ✅ | ✅ | Classic project detail refresh |
| Create project | `/repos/{owner}/{repo}/projects` (POST) | ✅ | ✅ | Create classic project dialog |
| Update project | `/projects/{id}` (PATCH) | ✅ | ✅ | Name/body/state edit dialog |
| Delete project | `/projects/{id}` (DELETE) | ✅ | ✅ | Confirmation dialog |
| List project columns | `/projects/{id}/columns` | ✅ | ✅ | Column cards inside detail |
| List project cards | `/projects/columns/{id}/cards` | ✅ | ✅ | Cards grouped by column |
| Move project card | `/projects/columns/cards/{id}/moves` (POST) | ✅ | ✅ | Move note cards between columns |
| Projects V2 overview | GraphQL `Repository.projectsV2` | ✅ | ✅ | List with item counts/open state |
| Projects V2 detail | GraphQL `ProjectV2.items`, `ProjectV2.fields`, `ProjectV2.views`, `ProjectV2.workflows` | ✅ | ✅ | Detail screen with fields, views, workflows and items |
| Update Projects V2 | GraphQL `updateProjectV2` | ✅ | ✅ | Title, description, readme, open/closed, public/private |
| Create Projects V2 field | GraphQL `createProjectV2Field` | ✅ | ✅ | Text, number, date and single-select fields |
| Update Projects V2 field | GraphQL `updateProjectV2Field` | ✅ | ✅ | Name and single-select option replacement |
| Delete Projects V2 field | GraphQL `deleteProjectV2Field` | ✅ | ✅ | Confirmation dialog |
| Projects V2 views | GraphQL `ProjectV2.views` | ✅ | ✅ | View list with layout, filter and visible fields |
| Projects V2 workflows | GraphQL `ProjectV2.workflows` | ✅ | ✅ | Workflow list with enabled state |
| Add Projects V2 draft item | GraphQL `addProjectV2DraftIssue` | ✅ | ✅ | Draft issue creation |
| Update Projects V2 draft item | GraphQL `updateProjectV2DraftIssue` | ✅ | ✅ | Draft title/body edit |
| Delete Projects V2 item | GraphQL `deleteProjectV2Item` | ✅ | ✅ | Confirmation dialog |
| Archive/unarchive Projects V2 item | GraphQL `archiveProjectV2Item`, `unarchiveProjectV2Item` | ✅ | ✅ | Item card actions |
| Update Projects V2 item field | GraphQL `updateProjectV2ItemFieldValue`, `clearProjectV2ItemFieldValue` | ✅ | ✅ | Text, number, date and single-select fields |
| Move Projects V2 item | GraphQL `updateProjectV2ItemPosition` | ✅ | ✅ | Move item to top |

### Packages
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List user packages | `/users/{username}/packages` | ✅ | ✅ | Packages screen with owner/type/search filters |
| List org packages | `/orgs/{org}/packages` | ✅ | ✅ | Org selector from current user orgs |
| Get package | `/users/{username}/packages/{package_type}/{package_name}` or org equivalent | ✅ | ✅ | Package detail header |
| Delete package | `/users/{username}/packages/{package_type}/{package_name}` or org equivalent (DELETE) | ✅ | ✅ | Confirmation dialog |
| List package versions | `.../versions` | ✅ | ✅ | Version list with tags |
| Delete package version | `.../versions/{package_version_id}` (DELETE) | ✅ | ✅ | Confirmation dialog |

### Security
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List security advisories | `/repos/{owner}/{repo}/security-advisories` | ✅ | ✅ | Advisories tab with filters/search |
| Enable Dependabot alerts | `/repos/{owner}/{repo}/vulnerability-alerts` (PUT) | ✅ | ✅ | Security settings toggle |
| Disable Dependabot alerts | `/repos/{owner}/{repo}/vulnerability-alerts` (DELETE) | ✅ | ✅ | Security settings toggle |
| Dependabot security updates | `/repos/{owner}/{repo}/automated-security-fixes` | ✅ | ✅ | Read/toggle enable/disable |
| Private vulnerability reporting | `/repos/{owner}/{repo}/private-vulnerability-reporting` | ✅ | ✅ | Read/toggle enable/disable |
| List code scanning alerts | `/repos/{owner}/{repo}/code-scanning/alerts` | ✅ | ✅ | Implemented with filters/detail |
| Get code scanning alert | `/repos/{owner}/{repo}/code-scanning/alerts/{alert_number}` | ✅ | ✅ | Detail dialog refreshes single alert |
| List secret scanning alerts | `/repos/{owner}/{repo}/secret-scanning/alerts` | ✅ | ✅ | Implemented with filters/detail |
| Get secret scanning alert | `/repos/{owner}/{repo}/secret-scanning/alerts/{alert_number}` | ✅ | ✅ | Detail dialog refreshes single alert |
| List Dependabot alerts | `/repos/{owner}/{repo}/dependabot/alerts` | ✅ | ✅ | Implemented with mobile filters/search |
| Get Dependabot alert | `/repos/{owner}/{repo}/dependabot/alerts/{alert_number}` | ✅ | ✅ | Detail dialog refreshes single alert |
| Get repository security advisory | `/repos/{owner}/{repo}/security-advisories/{ghsa_id}` | ✅ | ✅ | Advisory detail dialog |
| Community profile | `/repos/{owner}/{repo}/community/profile` | ✅ | ✅ | Health percentage and community checklist |

### Webhooks
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List webhooks | `/repos/{owner}/{repo}/hooks` | ✅ | ✅ | Webhooks screen |
| Get webhook | `/repos/{owner}/{repo}/hooks/{id}` | ✅ | ✅ | Detail dialog |
| Create webhook | `/repos/{owner}/{repo}/hooks` (POST) | ✅ | ✅ | Create dialog |
| Update webhook | `/repos/{owner}/{repo}/hooks/{id}` (PATCH) | ✅ | ✅ | Edit dialog |
| Delete webhook | `/repos/{owner}/{repo}/hooks/{id}` (DELETE) | ✅ | ✅ | Confirmation dialog |
| Test webhook | `/repos/{owner}/{repo}/hooks/{id}/tests` (POST) | ✅ | ✅ | Card action |
| Ping webhook | `/repos/{owner}/{repo}/hooks/{id}/pings` (POST) | ✅ | ✅ | Card action |
| Get webhook config | `/repos/{owner}/{repo}/hooks/{id}/config` | ✅ | ✅ | Config dialog |
| Update webhook config | `/repos/{owner}/{repo}/hooks/{id}/config` (PATCH) | ✅ | ✅ | Config dialog |
| Get webhook deliveries | `/repos/{owner}/{repo}/hooks/{id}/deliveries` | ✅ | ✅ | Deliveries screen |
| Redeliver webhook | `/repos/{owner}/{repo}/hooks/{id}/deliveries/{delivery_id}/attempts` (POST) | ✅ | ✅ | Delivery detail action |

### Repository Rules
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List rulesets | `/repos/{owner}/{repo}/rulesets` | ✅ | ✅ | Rulesets screen |
| Get ruleset | `/repos/{owner}/{repo}/rulesets/{id}` | ✅ | ✅ | Detail UI |
| Create ruleset | `/repos/{owner}/{repo}/rulesets` (POST) | ✅ | ✅ | Raw rules JSON editor |
| Update ruleset | `/repos/{owner}/{repo}/rulesets/{id}` (PUT) | ✅ | ✅ | Conditions/rules editor |
| Delete ruleset | `/repos/{owner}/{repo}/rulesets/{id}` (DELETE) | ✅ | ✅ | Confirmation dialog |
| Get rule suite | `/repos/{owner}/{repo}/rule-suites/{id}` | ✅ | ✅ | Detail dialog |
| List rule suites | `/repos/{owner}/{repo}/rule-suites` | ✅ | ✅ | Ruleset detail |

### User Settings (Advanced)
| Feature | API Endpoint | Backend | UI | Notes |
|---------|-------------|---------|-----|-------|
| List emails | `/user/emails` | ✅ | ✅ | SettingsModule |
| Add email | `/user/emails` (POST) | ✅ | ✅ | SettingsModule |
| Delete email | `/user/emails` (DELETE) | ✅ | ✅ | SettingsModule |
| Set email visibility | `/user/email/visibility` (PATCH) | ✅ | ✅ | SettingsModule |
| List SSH keys | `/user/keys` | ✅ | ✅ | SettingsModule |
| List SSH signing keys | `/user/ssh_signing_keys` | ✅ | ✅ | SettingsModule |
| List GPG keys | `/user/gpg_keys` | ✅ | ✅ | SettingsModule |
| Add SSH key | `/user/keys` (POST) | ✅ | ✅ | SettingsModule |
| Add SSH signing key | `/user/ssh_signing_keys` (POST) | ✅ | ✅ | SettingsModule |
| Add GPG key | `/user/gpg_keys` (POST) | ✅ | ✅ | SettingsModule |
| Delete SSH key | `/user/keys/{id}` (DELETE) | ✅ | ✅ | SettingsModule |
| Delete SSH signing key | `/user/ssh_signing_keys/{id}` (DELETE) | ✅ | ✅ | SettingsModule |
| Delete GPG key | `/user/gpg_keys/{id}` (DELETE) | ✅ | ✅ | SettingsModule |
| List social accounts | `/user/social_accounts` | ✅ | ✅ | SettingsModule |
| Add social account | `/user/social_accounts` (POST) | ✅ | ✅ | SettingsModule |
| Delete social account | `/user/social_accounts` (DELETE) | ✅ | ✅ | SettingsModule |
| Rate limit check | `/rate_limit` | ✅ | ✅ | SettingsModule |
| Clear cache | Local | ✅ | ✅ | SettingsModule |

---

## ⚠️ PARTIALLY IMPLEMENTED (Backend exists, UI is missing or read-only)

None currently tracked.

---

## ❌ NOT IMPLEMENTED / REMAINING BACKLOG

### Repository Management
| Feature | API Endpoint | Priority | Notes |
|---------|-------------|----------|-------|
| Merge branch | `/repos/{owner}/{repo}/merges` (POST) | Low | Branch-to-branch merge endpoint is not wired |
| Transfer repo | `/repos/{owner}/{repo}/transfer` (POST) | Low | |
| Rename default branch | `/repos/{owner}/{repo}/branches/{branch}/rename` (POST) | Low | |
| Required signatures | `/repos/{owner}/{repo}/branches/{branch}/protection/required_signatures` | Low | |
| Repo invites | `/repos/{owner}/{repo}/invitations` | Low | |
| Repo traffic | `/repos/{owner}/{repo}/traffic/views` | Low | Analytics |
| Repo clones | `/repos/{owner}/{repo}/traffic/clones` | Low | Analytics |
| Repo referrers | `/repos/{owner}/{repo}/traffic/popular/referrers` | Low | Analytics |
| Repo paths | `/repos/{owner}/{repo}/traffic/popular/paths` | Low | Analytics |
| Repo stargazers | `/repos/{owner}/{repo}/stargazers` | Low | List who starred |
| Repo watchers | `/repos/{owner}/{repo}/subscribers` | Low | List who watches |
| Repo events | `/repos/{owner}/{repo}/events` | Low | Activity feed |

### Issues (Advanced)
| Feature | API Endpoint | Priority | Notes |
|---------|-------------|----------|-------|
| Deeper timeline event actions | Multiple issue timeline/event endpoints | Low | Timeline is readable; event-specific mutations are not modeled |

### Pull Requests (Advanced)
| Feature | API Endpoint | Priority | Notes |
|---------|-------------|----------|-------|
| PR check suites | `/repos/{owner}/{repo}/commits/{ref}/check-suites` | Medium | Check runs are implemented; suites are not |
| Check if PR was merged | `/repos/{owner}/{repo}/pulls/{number}/merge` (GET) | Low | Mergeability is shown from PR detail; this explicit endpoint is not wired |

### Git Data (Advanced)
| Feature | API Endpoint | Priority | Notes |
|---------|-------------|----------|-------|
| Get single tree | `/repos/{owner}/{repo}/git/trees/{tree_sha}` | Low | |
| Create tree | `/repos/{owner}/{repo}/git/trees` (POST) | Low | Already used internally |
| Get single blob | `/repos/{owner}/{repo}/git/blobs/{file_sha}` | Low | |
| Create blob | `/repos/{owner}/{repo}/git/blobs` (POST) | Low | Already used internally |
| Get single tag | `/repos/{owner}/{repo}/git/tags/{tag_sha}` | Low | |
| Create tag | `/repos/{owner}/{repo}/git/tags` (POST) | Low | Annotated tags |
| Get single ref | `/repos/{owner}/{repo}/git/ref/{ref}` | Low | |
| Delete ref | `/repos/{owner}/{repo}/git/refs/{ref}` (DELETE) | Low | Already have branch delete |
| Update ref | `/repos/{owner}/{repo}/git/refs/{ref}` (PATCH) | Low | Force push, etc |
| List matching refs | `/repos/{owner}/{repo}/git/matching-refs/{ref}` | Low | |
| Get commit | `/repos/{owner}/{repo}/git/commits/{commit_sha}` | Low | Already used internally |
| Create commit | `/repos/{owner}/{repo}/git/commits` (POST) | Low | Already used internally |

### GitHub Actions (Advanced)
| Feature | API Endpoint | Priority | Notes |
|---------|-------------|----------|-------|
| Get single workflow | `/repos/{owner}/{repo}/actions/workflows/{id}` | Low | Workflow list/detail enough for current UI, no dedicated single-workflow detail endpoint |
| List runner groups | `/repos/{owner}/{repo}/actions/runner-groups` | Low | Enterprise-only / not surfaced |

### GitHub Apps / OAuth
| Feature | API Endpoint | Priority | Notes |
|---------|-------------|----------|-------|
| List app installations | `/user/installations` | Low | GitHub Apps |
| List repos for installation | `/user/installations/{id}/repositories` | Low | |
| OAuth app authorizations | `/authorizations` | Low | Legacy |

### Enterprise / Advanced
| Feature | API Endpoint | Priority | Notes |
|---------|-------------|----------|-------|
| List enterprise runners | `/enterprises/{enterprise}/actions/runners` | Low | Enterprise only |
| List org runner groups | `/orgs/{org}/actions/runner-groups` | Low | Enterprise only |
| SCIM provisioning | `/scim/v2/organizations/{org}/Users` | Low | Enterprise only |
| Audit log | `/orgs/{org}/audit-log` | Low | Enterprise only |
| SAML SSO auth | Various | Low | Enterprise only |

---

## 📊 SUMMARY

### Current Status Matrix

| Area | Status | Remaining gaps |
|------|--------|----------------|
| Authentication & User | ✅ Complete | None tracked |
| Repositories / Files | ✅ Complete for core mobile flows | Merge branch, transfer/rename/default-branch admin, repo invites, traffic analytics, stargazers/watchers/events |
| Branches | ✅ Complete for list/create/delete/switch | Required signatures and other advanced protection sub-resources |
| Commits / Compare | ✅ Complete for current UI | Low-level Git Data endpoints remain mostly internal or unsurfaced |
| Issues | ✅ Complete for main issue flow | Deeper timeline event actions |
| Pull Requests | ✅ Complete for PR detail/reviews/comments/merge methods/check runs | Check suites and explicit `GET /pulls/{number}/merge` merged-state endpoint |
| Releases | ✅ Complete | None tracked |
| GitHub Actions | ✅ Complete for runs/logs/artifacts/dispatch/jobs/cache/secrets/variables/runners/settings | Single-workflow detail and enterprise runner groups |
| Gists | ✅ Complete | None tracked |
| Notifications | ✅ Complete | None tracked |
| Search | ✅ Complete | None tracked |
| Organizations | ✅ Complete for list/user org repos | Org admin APIs intentionally out of scope |
| Discussions | ✅ Complete | None tracked |
| Projects / Projects V2 | ✅ Complete | None tracked |
| Packages | ✅ Complete | None tracked |
| Security | ✅ Complete | None tracked |
| Webhooks | ✅ Complete | None tracked |
| Repository Rules | ✅ Complete | None tracked |
| User Settings | ✅ Complete for supported public APIs | Web-only settings remain out of scope |
| GitHub Apps / OAuth | ❌ Backlog | Installations and legacy OAuth app authorization views |
| Enterprise / Advanced | ❌ Backlog | Enterprise runners, org runner groups, SCIM, audit log, SAML SSO |

### Overall Assessment

**Well Implemented (90%+ coverage):**
- ✅ Authentication & user management
- ✅ Basic repository operations
- ✅ File management (CRUD)
- ✅ Branch management
- ✅ Commits and compare
- ✅ Issues (basic CRUD, comments, reactions, lock/unlock, timeline)
- ✅ Pull Requests (detail, reviews, comments, merge methods, check runs)
- ✅ Releases (full CRUD)
- ✅ GitHub Actions (runs, logs, dispatch, artifacts, caches, secrets, variables, runners)
- ✅ Gists
- ✅ Notifications
- ✅ Advanced search (repositories, users, issues, commits, topics, labels)
- ✅ Organizations
- ✅ Discussions
- ✅ Projects and Projects V2
- ✅ Packages
- ✅ User settings (comprehensive)
- ✅ Security alerts and controls
- ✅ Security single-alert detail and community profile
- ✅ Webhooks (detail, create/edit/delete, ping/test, config, deliveries and redelivery)
- ✅ Repository rulesets and rule suites

**Partially Implemented / In Progress:**
- None currently tracked.

**Not Implemented / Early Coverage — Major Gaps:**
- ⚠️ Git Data standalone UI/API surface.
- ⚠️ Repository analytics/admin extras: traffic, stargazers/watchers/events, transfer, branch rename, invitations.
- ⚠️ Advanced PR extras: check suites and explicit merged-state endpoint.
- ⚠️ Deeper issue timeline event actions.
- ⚠️ GitHub Apps/OAuth and Enterprise-only APIs.

### Recommendations for Next Implementation

**Low Priority (nice to have):**
1. **Advanced PR check suites / explicit merged-state endpoint** — remaining PR polish.
2. **Repository analytics/admin extras** — traffic, stargazers/watchers/events, transfer and branch rename.
3. **Standalone Git Data tools** — tree/blob/tag/ref viewers only if a concrete workflow needs them.
4. **Single workflow detail** — wire `/actions/workflows/{id}` only if the workflow overview needs a separate detail screen.
