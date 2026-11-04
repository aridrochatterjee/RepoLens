# RepoLens

RepoLens is a Java 21 and vanilla JavaScript application for exploring GitHub repository metadata, source structure, and development activity. It uses the JDK `HttpServer` and `HttpClient`, Jackson, Gradle, and GitHub REST API; no web framework or frontend framework is required.

## RepoLens 2.0 frontend

The frontend is a dark-first, GitHub-inspired workspace for repository insights, code intelligence, and developer intelligence. The redesign retains the static HTML/CSS/vanilla JavaScript architecture and existing API contracts.

- **Design system:** `frontend/css/style.css` defines shared color, typography, spacing, border, surface, elevation, transition, and layout tokens. Components use the same tokens in dark and light themes.
- **Frontend structure:** `frontend/index.html` contains the signed-out product landing page and authenticated workspace shell. `frontend/js/app.js` handles authentication state, repository analysis, safe DOM rendering, and navigation.
- **Navigation:** The signed-in shell has independent Dashboard, Repository Insights, Code Explorer, Code Composition, Directory Analysis, Contributors, Pull Requests, Issues, Collaboration, and Settings workspaces. Analytics load when a workspace is opened instead of being displayed together.
- **Dashboard:** A concise repository header, three summary metrics, recent commits, activity trend, and language distribution. Detailed analytics live in their own workspaces.
- **Source viewer:** Files open inside RepoLens with path metadata, safe text rendering, line numbers, lightweight syntax highlighting, copy/download actions, and a secondary GitHub link. Binary files are identified and are not returned as text.
- **Theme:** The theme toggle switches between dark and light CSS custom-property sets. The preference is stored locally as a presentation setting; authentication tokens are never stored in browser storage.
- **Responsive and accessible behavior:** The sidebar becomes a keyboard-operable mobile drawer, workspaces and code panels reflow for narrow screens, focus indicators remain visible, charts expose text descriptions, and motion is reduced when requested by the operating system.
- **Dependencies:** No frontend packages, build system, or framework were added. The existing server continues to serve the static files at their existing paths.

Run the frontend syntax check and backend tests with:

```sh
node --check frontend/js/app.js
./gradlew clean test
```

## Features

- GitHub OAuth sign-in, session-backed API access, and logout.
- Repository metadata and language composition.
- Repository health estimate with factor scores, explanations, strengths, and attention areas.
- Commit activity chart for a selected 7-, 30-, or 90-day period (30 days by default).
- Paginated recent commits and contributor statistics with contribution distribution.
- Developer intelligence: a ranked contributor leaderboard, public contributor activity profiles, bounded pull-request and issue analytics, and collaboration observations grounded in returned GitHub data.
- Code intelligence from GitHub's recursive Git Trees API: file and directory counts, file-type composition, folder exploration, largest files, and evidence-backed structural observations.
- Authenticated in-app file viewing through the GitHub Contents API, with path/ref validation, UTF-8 and binary detection, and bounded response sizes.
- Responsive dark and light themes.

## Run locally

1. Copy `.env.example` to `.env` and set your GitHub OAuth application values. Keep `.env` local; never commit credentials or tokens.
2. Set the OAuth callback URL to `http://localhost:8080/auth/github/callback`.
3. Run `./gradlew run` and open `http://localhost:8080`.

The server binds to the loopback interface for local development. Use a properly configured HTTPS deployment and secure cookies before making the application available to other machines. OAuth access tokens remain server-side.

## API

Repository analytics routes require the RepoLens session cookie and accept only `GET`. They return `401` when the request is unauthenticated. The browser never supplies a GitHub access token.

| Endpoint | Query parameters | Description |
|---|---|---|
| `GET /api/repository` | `url` (required) | Repository metadata |
| `GET /api/repository/languages` | `url` (required) | Language byte counts and percentages |
| `GET /api/repository/health` | `url` (required) | Explainable health estimate |
| `GET /api/repository/activity` | `url` (required), `period` (7, 30, or 90; default 30) | Daily and weekly commit activity |
| `GET /api/repository/commits` | `url` (required), `page` (1–100; default 1), `limit` (1–30; default 10) | Recent commits, latest first |
| `GET /api/repository/contributors` | `url` (required) | Contributions and relative distribution |
| `GET /api/repository/contributors/{username}` | `url` (required), `limit` (1–30; default 10) | Public contributor profile, latest matching commits, and repository-scoped search counts |
| `GET /api/repository/pull-requests` | `url` (required), `period` (7, 30, or 90; default 30), `page` (1–10; default 1), `limit` (1–100; default 100) | Recently updated PRs up to the requested result limit per page, merge and review observations |
| `GET /api/repository/issues` | `url` (required), `period` (7, 30, or 90; default 30), `page` (1–10; default 1), `limit` (1–100; default 100) | Recently updated issue entries up to the requested result limit per page, labels and resolution observations |
| `GET /api/repository/collaboration` | `url` (required) | Bounded contributor leaderboard and contribution concentration |
| `GET /api/repository/codebase` | `url` (required), `limit` (1–30; default 10) | One recursive tree analysis for composition, directories, explorer, largest files, and hotspots |
| `GET /api/repository/file` | `url` and `path` (required), `ref` (optional; default branch) | Authenticated bounded text file metadata/content; binary files return metadata without content |

Repository URLs are validated server-side and must be HTTPS `github.com/{owner}/{repository}` URLs. GitHub API errors are translated to safe HTTP error responses; rate limits and permission failures are surfaced rather than replaced with synthetic data.

Example health response (only one available factor is shown):

```json
{
  "repositoryFullName": "octocat/Hello-World",
  "score": 80,
  "availableWeight": 20,
  "factors": [
    {
      "name": "Recent commit activity",
      "score": 80,
      "weight": 20,
      "explanation": "16 commits in the last 30 days; 20 commits reaches the factor's 100-point cap."
    }
  ],
  "strengths": ["Recent commit activity"],
  "areasForAttention": [],
  "disclaimer": "RepoLens estimate, not an official GitHub rating."
}
```

Example activity response:

```json
{
  "repositoryFullName": "octocat/Hello-World",
  "periodDays": 30,
  "totalCommits": 16,
  "averageCommitsPerWeek": 3.73,
  "mostActiveDay": "TUESDAY",
  "dailyActivity": [{"date": "2026-10-06", "commits": 2}],
  "weeklyActivity": [{"weekStarting": "2026-10-05", "commits": 4}],
  "available": true,
  "unavailableReason": null
}
```

If GitHub is still calculating statistics, activity is returned with `available: false`, null totals, and an explanation. The UI displays an unavailable state instead of a zero-valued chart.

Commit responses contain `repositoryFullName`, `page`, `limit`, and a `commits` array. Entries include `sha`, `shortSha`, `message`, nullable author avatar, author name, date, and GitHub commit URL. Contributor responses include `repositoryFullName`, `totalReturned`, `available`, `mayBeTruncated`, `contributors`, and a `limitation` explanation. Each contributor includes nullable profile fields, contribution count, and percentage share of contributions returned.

The codebase response includes availability, branch, truncation flags, summary totals, composition counts, a bounded `treeEntries` list, directory aggregates, ranked largest files, structural observations, and limitations. A missing default branch or unavailable tree returns `available: false`. A valid empty tree returns `available: true` with zero counts. GitHub's `truncated` flag is retained, and all derived totals are explicitly partial in that case. Explorer output is capped at 3,000 entries, directory results at 2,000, and largest-file results at the validated `limit` (maximum 30).

The file endpoint validates repository URLs, rejects absolute paths, backslashes, empty path components, and `.` / `..` traversal segments, and validates branch/commit references before requesting GitHub. It uses the existing server-side OAuth token and never accepts a token parameter. Text files are strictly decoded as UTF-8; invalid UTF-8 or NUL-containing files are reported as binary with `content: null`. The default maximum file size is 1 MiB and can be set with `-Drepolens.maxFileBytes=<bytes>` (1 byte–10 MiB). Files over the configured limit receive HTTP `413`. GitHub requests use the existing 20-second timeout, and redirect destinations are restricted to `api.github.com`. File API errors include a stable error code for missing files, permission failures, rate limits, timeouts, and unsupported content.

If the viewer reports that the file endpoint is unavailable, restart RepoLens with the current Java backend. The frontend can be refreshed independently of the already-running `HttpServer`; an older process will continue routing `/api/repository/file` through the general repository handler and return `Endpoint not found` until restarted.

## Developer intelligence methodology and limitations

- **Contributor leaderboard and concentration:** GitHub's Contributors API is read in at most five pages of 100 results (500 contributors). Duplicate named accounts are removed case-insensitively; GitHub's anonymous contributor entries cannot be reliably deduplicated without an identity. Ranks sort by returned contribution count. Percentages divide by the sum of those returned counts and are explicitly a subset share, not a measure of developer effort. Bot accounts are marked using GitHub's public account type or `[bot]` login suffix. GitHub may omit anonymous users and returns commit-based contribution counts, not all forms of work. Recent-activity ordering is not offered because the leaderboard endpoint does not provide comparable per-contributor timestamps.
- **Contributor profiles:** The profile endpoint requests up to 30 commits filtered by GitHub author, one public GitHub user record, up to 500 repository contributor records for a matching contribution count, and three bounded GitHub search counts for authored PRs, authored issues, and PR reviews. GitHub Search is indexed and caps result sets at 1,000; its counts may therefore be incomplete. Commit charts and first/most-recent observed dates represent only the latest returned commits. A missing account or field is shown as unavailable, not inferred as zero. Only public profile fields and repository activity are returned; no productivity or personal-characteristic score is calculated.
- **Pull requests:** Analytics use the requested result limit (1–100, default 100) per page, with pages bounded to 1–10. `merged_at` identifies merged PRs; closed includes merged PRs, and merge rate is `merged / returned closed × 100` (unavailable when no returned PRs are closed). Average time-to-merge is the mean of non-negative intervals for returned PRs with valid `created_at` and `merged_at`. The selected 7-, 30-, or 90-day window applies to the created-date activity chart; the aggregate status sample remains the page of recently updated records. Drafts remain in the returned PR population and are reported separately. Review-comment totals come from GitHub's PR list; unique reviewers are sampled from at most five PR review lists. The response identifies these scopes and limitations.
- **Issues:** Analytics use the requested result limit (1–100, default 100) recently updated entries per page, with pages bounded to 1–10. Because the Issues API also returns PRs, any entry containing `pull_request` is removed before issue counts, labels, activity, and resolution-time calculations. Resolution time uses only valid `created_at` / `closed_at` pairs. Issue trends and recent lists use the selected date window, while open/closed totals and label counts describe the returned page only.
- **Collaboration observations:** Contribution concentration and PR/issue authorship shares describe returned GitHub records and are presented as observations, not judgments about individuals or team relationships. A top-contributor observation is shown at a 50% share of returned contributions. Review participation counts unique non-bot usernames found in the first 100 reviews for at most five returned PRs; it does not imply review quality. No collaboration or productivity score is generated.
- **Request bounds:** PR and issue analytics each make one list request with `per_page` set to the validated `limit` (maximum 100); PR review details are requested for at most five PRs. Contributor profiles are only loaded when selected and do not trigger work for every leaderboard entry. The app does not cache authenticated responses. GitHub permissions, rate limits, search indexing, anonymous contributors, and partial account data can make metrics unavailable or incomplete.

## Code intelligence methodology and limitations

The code intelligence endpoint fetches repository metadata once to identify its default branch, then requests one recursive Git tree. All codebase counts, directory aggregates, and observations are computed from that tree response; the endpoint does not fetch each file's contents. Existing language-byte analytics remains a separate GitHub API request.

- Tracked files are Git tree `blob` entries. Tree entries are directories; Git does not track empty directories, so they cannot be detected or reported.
- A file's extension is its final suffix, lowercased; dotfiles and extensionless names use `[no extension]`. File categories (`Source code`, `Configuration`, `Documentation`, `Assets`, `Other`) and language file counts use a fixed extension/name mapping. These heuristics are not GitHub Linguist and can be inaccurate. Binary formats are categorized as assets, not source code.
- Blob byte totals include only entries with a numeric size. Missing sizes are counted separately and excluded from byte shares. Nested-directory counts and percentages include descendants, so nested shares overlap. `"(root)"` describes files directly in the repository root.
- The largest-file list ranks entries with known blob sizes. File sizes are Git blob sizes, not checkout size, source-line counts, or proof of a performance or maintainability problem.
- Tree exploration returns at most 3,000 path entries and is incrementally rendered in batches of 100. Directory aggregates are capped at 2,000 results. GitHub can truncate recursive trees; when it does, counts and observations cover only returned entries and must not be interpreted as complete.
- Structural observations use explicit thresholds: more than 500 files under a directory, a file larger than 10 MiB, a file path at depth 12 or more, and a directory containing more than 50% of known blob bytes. Single-file directories and recognizable `node_modules`, `vendor`, `dist`, `build`, `target`, `coverage`, `generated`, or `.git` paths are also surfaced as observations. These are investigation prompts, not health ratings or claims of complexity/technical debt.

The codebase result is produced from one tree retrieval per request and is not cached. This avoids cross-user leakage of private repository data and stale authorization-dependent entries. File contents are fetched only after a file is selected, are bounded by the configured maximum, and are not cached. The API response and frontend path list are bounded for rendering, while tree parsing is limited to the entries returned by GitHub.

## Health estimate methodology

RepoLens presents an estimate, not an official GitHub rating or an assessment of code quality, security, or project suitability. Five factors have equal 20% weight; unavailable factors are omitted and the remaining weights are renormalized. If no factors can be scored, the overall score is unavailable rather than zero.

| Factor | Measured signal and scoring |
|---|---|
| Recent commit activity | Commits in the last 30 days × 5, capped at 100 |
| Maintenance recency | Last push within 30 days: 100; within 31–90: 75; 91–180: 50; 181–365: 25; older: 0 |
| Open issues relative to activity | `open issues / (30-day commits + 1)`: at most 0.1: 100; at most 0.25: 80; at most 0.5: 60; at most 1: 40; at most 2: 20; otherwise 0 |
| Contributor activity | Contributors returned by GitHub × 20, capped at 100 |
| Development consistency | Percentage of calendar weeks in the 30-day window that contain commits |

Factor scores of 70 or higher are listed as positive signals; scores below 50 are flagged for attention. These are simple, transparent thresholds, not universal indicators of project health. GitHub activity may be delayed or incomplete; the open-issues count may include pull requests; contributor results can omit anonymous accounts, private-repository work, and contributors beyond GitHub's 500-entry limit.

## GitHub API dependencies and limitations

RepoLens uses the GitHub REST API for repository metadata, languages, commit activity, commits, contributors, and recursive Git trees. GitHub's commit activity endpoint may return a processing response while statistics are being generated; those data are explicitly shown as unavailable. Contributor queries are capped at 500 entries to bound API usage, and the response indicates when that cap is reached. Repository visibility and access depend on the signed-in GitHub user's token permissions; the configured OAuth scope is intentionally not expanded by this phase. GitHub may truncate trees or omit size data for some entries.

## Security notes

- Configure OAuth credentials outside source control; `.env` is ignored and `.env.example` contains placeholders only.
- API routes use the existing server-side session and do not accept tokens from query parameters.
- File paths and references are validated server-side; source is rendered using text nodes and is never executed. Binary content is not returned as text, and code viewer downloads are generated locally from content the authenticated endpoint has already bounded.
- The local server binds to loopback, session lifetime is enforced server-side, session cookies are `HttpOnly` and `SameSite=Lax`, and outbound GitHub requests require HTTPS and do not follow redirects to other hosts.
- A production deployment must terminate HTTPS, set `Secure` cookies, and review CSRF protections and session storage for its deployment topology.
- GitHub data is rendered with safe DOM APIs; external profile and commit links are restricted to HTTPS GitHub hosts.
- Code intelligence does not cache authenticated tree responses, avoiding cross-account cache leakage. Tree paths are validated before aggregation and are never resolved against the server filesystem.

## Tests

Run the backend tests with:

```sh
./gradlew clean test
```

Tests use local fixtures and mocks; they do not require OAuth credentials or make uncontrolled GitHub API requests. Frontend JavaScript syntax can be checked with `node --check frontend/js/app.js`.
