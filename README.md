# RepoLens

RepoLens is a Java 21 and vanilla JavaScript application for exploring GitHub repository metadata and development activity. It uses the JDK `HttpServer` and `HttpClient`, Jackson, Gradle, and GitHub REST API; no web framework or frontend framework is required.

## Features

- GitHub OAuth sign-in, session-backed API access, and logout.
- Repository metadata and language composition.
- Repository health estimate with factor scores, explanations, strengths, and attention areas.
- Commit activity chart for a selected 7-, 30-, or 90-day period (30 days by default).
- Paginated recent commits and contributor statistics with contribution distribution.
- Code intelligence from GitHub's recursive Git Trees API: file and directory counts, file-type composition, folder exploration, largest files, and evidence-backed structural observations.
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
| `GET /api/repository/codebase` | `url` (required), `limit` (1–30; default 10) | One recursive tree analysis for composition, directories, explorer, largest files, and hotspots |

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

## Code intelligence methodology and limitations

The code intelligence endpoint fetches repository metadata once to identify its default branch, then requests one recursive Git tree. All codebase counts, directory aggregates, and observations are computed from that tree response; the endpoint does not fetch each file's contents. Existing language-byte analytics remains a separate GitHub API request.

- Tracked files are Git tree `blob` entries. Tree entries are directories; Git does not track empty directories, so they cannot be detected or reported.
- A file's extension is its final suffix, lowercased; dotfiles and extensionless names use `[no extension]`. File categories (`Source code`, `Configuration`, `Documentation`, `Assets`, `Other`) and language file counts use a fixed extension/name mapping. These heuristics are not GitHub Linguist and can be inaccurate. Binary formats are categorized as assets, not source code.
- Blob byte totals include only entries with a numeric size. Missing sizes are counted separately and excluded from byte shares. Nested-directory counts and percentages include descendants, so nested shares overlap. `"(root)"` describes files directly in the repository root.
- The largest-file list ranks entries with known blob sizes. File sizes are Git blob sizes, not checkout size, source-line counts, or proof of a performance or maintainability problem.
- Tree exploration returns at most 3,000 path entries and is incrementally rendered in batches of 100. Directory aggregates are capped at 2,000 results. GitHub can truncate recursive trees; when it does, counts and observations cover only returned entries and must not be interpreted as complete.
- Structural observations use explicit thresholds: more than 500 files under a directory, a file larger than 10 MiB, a file path at depth 12 or more, and a directory containing more than 50% of known blob bytes. Single-file directories and recognizable `node_modules`, `vendor`, `dist`, `build`, `target`, `coverage`, `generated`, or `.git` paths are also surfaced as observations. These are investigation prompts, not health ratings or claims of complexity/technical debt.

The codebase result is produced from one tree retrieval per request and is not cached. This avoids cross-user leakage of private repository data and stale authorization-dependent entries. The API response and frontend path list are bounded for rendering, while tree parsing is limited to the entries returned by GitHub.

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
