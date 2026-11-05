const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const test = require("node:test");

const source = readFileSync(
    new URL("../js/app.js", `file://${__filename}`),
    "utf8"
);
const workspaceSource = source.slice(
    source.indexOf("function loadCommitAnalyticsWorkspace()"),
    source.indexOf("function renderCommitContributorSummary(")
);
const heatmapSource = source.slice(
    source.indexOf("function renderContributorCommitHeatmap("),
    source.indexOf("function renderCalendarHeatmap(")
);

test("contributor heatmap requests stay within the backend commit page limit", () => {
    assert.match(source, /const COMMIT_PAGE_LIMIT = 30;/);
    assert.match(workspaceSource, /limit:\s*String\(COMMIT_PAGE_LIMIT\)/);
    assert.doesNotMatch(workspaceSource, /limit:\s*["'](?:31|100)["']/);
});

test("contributor and date filters are preserved and clearing contributor restores repository activity", () => {
    assert.match(workspaceSource, /const username = activityContributor\.value;/);
    assert.match(workspaceSource, /author:\s*username/);
    assert.match(workspaceSource, /since:\s*sinceDate/);
    assert.match(workspaceSource, /until:\s*untilDate/);
    assert.match(workspaceSource, /const endpoint = username\s*\?\s*CONFIG\.endpoints\.commits\s*:\s*CONFIG\.endpoints\.activity/);
    assert.match(workspaceSource, /activityContributor\.addEventListener\("change", loadActivity\)/);
    assert.match(workspaceSource, /period\.addEventListener\("change", loadActivity\)/);
});

test("retry uses the current request builder and stale contributor/date results are ignored", () => {
    assert.match(workspaceSource, /renderActivityUnavailableState\(\s*heatmap,[\s\S]*?loadActivity/);
    assert.match(workspaceSource, /currentRequest !== activityRequest/);
    assert.match(workspaceSource, /requestId !== analysisRequestId/);
});

test("a full response page is labeled potentially incomplete using API metadata", () => {
    assert.match(heatmapSource, /Number\(commitPage\.limit\)/);
    assert.match(
        heatmapSource,
        /\(commitPage\.commits \|\| \[\]\)\.length\s*>=\s*limit/
    );
    assert.match(heatmapSource, /Results may be incomplete/);
    assert.doesNotMatch(heatmapSource, /complete history/);
});
