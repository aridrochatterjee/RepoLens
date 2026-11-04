# RepoLens

RepoLens is a Java 21 and vanilla JavaScript application for exploring GitHub repository metadata and development activity. It uses the JDK `HttpServer` and `HttpClient`, Jackson, Gradle, and GitHub REST API; no web framework or frontend framework is required.

## Features

- GitHub OAuth sign-in, session-backed API access, and logout.
- Repository metadata and language composition.
- Repository health estimate with factor scores, explanations, strengths, and attention areas.
- Commit activity chart for a selected 7-, 30-, or 90-day period (30 days by default).
- Paginated recent commits and contributor statistics with contribution distribution.
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

RepoLens uses the GitHub REST API for repository metadata, languages, commit activity, commits, and contributors. GitHub's commit activity endpoint may return a processing response while statistics are being generated; those data are explicitly shown as unavailable. Contributor queries are capped at 500 entries to bound API usage, and the response indicates when that cap is reached. Repository visibility and access depend on the signed-in GitHub user's token permissions; the configured OAuth scope is intentionally not expanded by this phase.

## Security notes

- Configure OAuth credentials outside source control; `.env` is ignored and `.env.example` contains placeholders only.
- API routes use the existing server-side session and do not accept tokens from query parameters.
- The local server binds to loopback, session lifetime is enforced server-side, session cookies are `HttpOnly` and `SameSite=Lax`, and outbound GitHub requests require HTTPS and do not follow redirects to other hosts.
- A production deployment must terminate HTTPS, set `Secure` cookies, and review CSRF protections and session storage for its deployment topology.
- GitHub data is rendered with safe DOM APIs; external profile and commit links are restricted to HTTPS GitHub hosts.

## Tests

Run the backend tests with:

```sh
./gradlew clean test
```

Tests use local fixtures and mocks; they do not require OAuth credentials or make uncontrolled GitHub API requests. Frontend JavaScript syntax can be checked with `node --check frontend/js/app.js`.
