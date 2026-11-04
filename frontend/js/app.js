
"use strict";

/* =========================================================
   RepoLens — Frontend Application
   Authentication, repository analysis and language insights
   ========================================================= */


/* 01. DOM references
   --------------------------------------------------------- */

const elements = {
    themeToggle: document.getElementById("theme-toggle"),
    themeColorMeta: document.querySelector('meta[name="theme-color"]'),

    loginView: document.getElementById("login-view"),
    dashboardView: document.getElementById("dashboard-view"),
    footerView: document.getElementById("footer-view"),

    userProfile: document.getElementById("user-profile"),
    userAvatar: document.getElementById("user-avatar"),
    userName: document.getElementById("user-name"),
    logoutButton: document.getElementById("logout-button"),
    loginMessage: document.getElementById("login-message"),

    repoForm: document.getElementById("repo-form"),
    repoInput: document.getElementById("repo-url"),
    analyzeButton: document.getElementById("analyze-button"),
    clearButton: document.getElementById("clear-button"),

    message: document.getElementById("message"),
};

let analysisRequestId = 0;
let contributorProfileRequestId = 0;


/* 02. Configuration
   --------------------------------------------------------- */

const CONFIG = {
    themeStorageKey: "repolens-theme",

    endpoints: {
        currentUser: "/api/auth/me",
        logout: "/api/auth/logout",
        repository: "/api/repository",
        languages: "/api/repository/languages",
        health: "/api/repository/health",
        activity: "/api/repository/activity",
        commits: "/api/repository/commits",
        contributorProfile: "/api/repository/contributors",
        pullRequests: "/api/repository/pull-requests",
        issues: "/api/repository/issues",
        collaboration: "/api/repository/collaboration",
        codebase: "/api/repository/codebase",
    },

    languageColors: {
        JavaScript: "#f1e05a",
        TypeScript: "#3178c6",
        Java: "#b07219",
        Python: "#3572A5",
        Rust: "#dea584",
        Go: "#00ADD8",
        HTML: "#e34c26",
        CSS: "#563d7c",
        Shell: "#89e051",
        C: "#555555",
        "C++": "#f34b7d",
        "C#": "#178600",
        PHP: "#4F5D95",
        Ruby: "#701516",
        Kotlin: "#A97BFF",
        Swift: "#F05138",
        Dart: "#00B4AB",
        Vue: "#41b883",
        Svelte: "#ff3e00",
    },

    fallbackLanguageColors: [
        "#9b7aff",
        "#60a5fa",
        "#4ade80",
        "#f59e0b",
        "#f472b6",
        "#22d3ee",
        "#a78bfa",
    ],
};


/* 03. Shared utilities
   --------------------------------------------------------- */

async function readJson(response) {
    try {
        return await response.json();
    } catch {
        return {};
    }
}

function createElement(tag, className, text) {
    const element = document.createElement(tag);

    if (className) {
        element.className = className;
    }

    if (text !== undefined && text !== null) {
        element.textContent = String(text);
    }

    return element;
}

function formatNumber(value) {
    const number = Number(value);

    if (!Number.isFinite(number)) {
        return "0";
    }

    return number.toLocaleString();
}

function formatBytes(bytes) {
    const value = Number(bytes);

    if (!Number.isFinite(value) || value <= 0) {
        return "0 B";
    }

    if (value < 1024) {
        return `${value} B`;
    }

    if (value < 1024 * 1024) {
        return `${(value / 1024).toFixed(1)} KB`;
    }

    if (value < 1024 * 1024 * 1024) {
        return `${(value / (1024 * 1024)).toFixed(2)} MB`;
    }

    return `${(value / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function getLanguageColor(language, index = 0) {
    return (
        CONFIG.languageColors[language] ||
        CONFIG.fallbackLanguageColors[
            index % CONFIG.fallbackLanguageColors.length
        ]
    );
}


/* 04. Theme management
   --------------------------------------------------------- */

function getSavedTheme() {
    try {
        const saved = localStorage.getItem(CONFIG.themeStorageKey);

        if (saved === "dark" || saved === "light") {
            return saved;
        }
    } catch (error) {
        console.warn("Unable to read theme preference.", error);
    }

    return "dark";
}

function applyTheme(theme) {
    const selected = theme === "light" ? "light" : "dark";
    const isLight = selected === "light";

    document.documentElement.setAttribute("data-theme", selected);

    if (elements.themeToggle) {
        elements.themeToggle.setAttribute(
            "aria-pressed",
            String(isLight)
        );

        elements.themeToggle.setAttribute(
            "aria-label",
            isLight ? "Switch to dark theme" : "Switch to light theme"
        );
    }

    if (elements.themeColorMeta) {
        elements.themeColorMeta.setAttribute(
            "content",
            isLight ? "#faf9fc" : "#0d0c10"
        );
    }

    try {
        localStorage.setItem(CONFIG.themeStorageKey, selected);
    } catch (error) {
        console.warn("Unable to save theme preference.", error);
    }
}

function initializeTheme() {
    applyTheme(getSavedTheme());

    elements.themeToggle?.addEventListener("click", () => {
        const current = document.documentElement.getAttribute("data-theme");

        applyTheme(current === "dark" ? "light" : "dark");
    });
}


/* 05. Message handling
   --------------------------------------------------------- */

function showMessage(text, type = "info") {
    if (!elements.message) return;

    elements.message.textContent = text;
    elements.message.className = `message visible ${type}`;
}

function clearMessage() {
    if (!elements.message) return;

    elements.message.textContent = "";
    elements.message.className = "message";
}

function showLoginMessage(text = "") {
    if (elements.loginMessage) {
        elements.loginMessage.textContent = text;
    }
}


/* 06. Authentication
   --------------------------------------------------------- */

function showLogin(messageText = "") {
    elements.loginView.hidden = false;
    elements.dashboardView.hidden = true;
    elements.footerView.hidden = true;
    elements.userProfile.hidden = true;

    showLoginMessage(messageText);
}

function showDashboard(user) {
    elements.loginView.hidden = true;
    elements.dashboardView.hidden = false;
    elements.footerView.hidden = false;
    elements.userProfile.hidden = false;

    elements.userName.textContent = user.name || user.username || "GitHub user";

    if (user.avatarUrl) {
        elements.userAvatar.src = user.avatarUrl;
        elements.userAvatar.hidden = false;
    } else {
        elements.userAvatar.removeAttribute("src");
        elements.userAvatar.hidden = true;
    }

    elements.userAvatar.alt = user.username
        ? `${user.username}'s GitHub avatar`
        : "GitHub profile avatar";

    showLoginMessage("");
}

async function checkAuthentication() {
    try {
        const response = await fetch(CONFIG.endpoints.currentUser, {
            method: "GET",
            credentials: "same-origin",
            headers: {
                Accept: "application/json",
            },
        });

        if (!response.ok) {
            showLogin();
            return false;
        }

        const payload = await readJson(response);
        const user = payload?.user;

        if (
            payload?.authenticated !== true ||
            !user ||
            (!user.username && !user.name)
        ) {
            showLogin();
            return false;
        }

        showDashboard(user);
        return true;

    } catch (error) {
        console.error("Authentication check failed:", error);

        showLogin("Unable to connect to RepoLens. Please refresh.");
        return false;
    }
}

async function logout() {
    elements.logoutButton.disabled = true;
    analysisRequestId += 1;

    try {
        const response = await fetch(CONFIG.endpoints.logout, {
            method: "POST",
            credentials: "same-origin",
        });

        if (!response.ok) {
            showMessage("Logout failed. Please try again.", "error");
            return;
        }

        elements.repoInput.value = "";
        updateClearButton();
        clearMessage();
        removeRepositoryResult();

        showLogin();

    } catch (error) {
        console.error("Logout failed:", error);
        showMessage("Unable to contact the server.", "error");

    } finally {
        elements.logoutButton.disabled = false;
    }
}


/* 07. GitHub repository URL validation
   --------------------------------------------------------- */

function validateGitHubRepository(input) {
    if (!input || !input.trim()) {
        return {
            valid: false,
            error: "Please enter a GitHub repository URL.",
        };
    }

    let parsedURL;

    try {
        parsedURL = new URL(input.trim());
    } catch {
        return {
            valid: false,
            error: "Please enter a valid URL.",
        };
    }

    if (parsedURL.protocol !== "https:") {
        return {
            valid: false,
            error: "Please use an HTTPS GitHub URL.",
        };
    }

    if (parsedURL.hostname.toLowerCase() !== "github.com") {
        return {
            valid: false,
            error: "Only github.com URLs are supported.",
        };
    }

    if (
        parsedURL.port ||
        parsedURL.username ||
        parsedURL.password ||
        parsedURL.search ||
        parsedURL.hash
    ) {
        return {
            valid: false,
            error: "Please enter a clean GitHub URL.",
        };
    }

    let path = parsedURL.pathname.replace(/\/+$/, "");
    path = path.replace(/\.git$/, "");

    const segments = path.split("/").filter(Boolean);

    if (segments.length !== 2) {
        return {
            valid: false,
            error: "Expected: github.com/owner/repository",
        };
    }

    const [owner, repository] = segments;
    const validName = /^[a-zA-Z0-9_.-]+$/;

    if (
        !validName.test(owner) ||
        !validName.test(repository) ||
        owner === "." ||
        owner === ".." ||
        repository === "." ||
        repository === ".."
    ) {
        return {
            valid: false,
            error: "Invalid repository owner or name.",
        };
    }

    return {
        valid: true,
        owner,
        repository,
        url: `https://github.com/${owner}/${repository}`,
    };
}


/* 08. Repository result rendering
   --------------------------------------------------------- */

function removeRepositoryResult() {
    document.getElementById("repository-result")?.remove();
}

function showRepositoryResult(repository) {
    let resultSection = document.getElementById("repository-result");

    if (!resultSection) {
        resultSection = createElement(
            "section",
            "repository-result"
        );

        resultSection.id = "repository-result";

        elements.repoForm.insertAdjacentElement(
            "afterend",
            resultSection
        );
    }

    resultSection.replaceChildren();

    const heading = createElement("div", "repository-heading");
    const headingTitle = createElement(
        "h2",
        "",
        repository.full_name || repository.name || "Repository"
    );
    const repositoryLink = safeGitHubUrl(repository.html_url);
    if (repositoryLink) {
        const link = createElement("a", "repository-github-link", "View on GitHub ↗");
        link.href = repositoryLink;
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        heading.append(headingTitle, link);
    } else {
        heading.append(headingTitle);
    }

    heading.append(
        createElement(
            "span",
            "repository-visibility",
            typeof repository.private === "boolean"
                ? repository.private ? "Private" : "Public"
                : "Visibility unavailable"
        )
    );

    const description = createElement(
        "p",
        "repository-description",
        repository.description || "No description provided."
    );

    const metadata = createElement("div", "repository-metadata");
    metadata.append(
        createElement(
            "span",
            "",
            `Primary language: ${repository.language || "Not specified"}`
        )
    );

    const stats = [
        ["Stars", repository.stargazers_count],
        ["Forks", repository.forks_count],
        ["Watchers", repository.watchers_count],
        ["Open issues", repository.open_issues_count],
    ];

    const statsContainer = createElement("div", "repository-stats");

    stats.forEach(([label, value]) => {
        const card = createElement("div", "repository-stat");

        const number = createElement(
            "strong",
            "",
            value === null || value === undefined || !Number.isFinite(Number(value))
                ? "—"
                : formatNumber(value)
        );

        const caption = createElement("span", "", label);

        card.append(number, caption);
        statsContainer.append(card);
    });

    if (repository.language) {
        metadata.append(
            createElement("span", "repository-language-tag", repository.language)
        );
    }

    resultSection.append(heading, description, metadata, statsContainer);

    resultSection.scrollIntoView({
        behavior: "smooth",
        block: "nearest",
    });
}

function safeGitHubUrl(value) {
    try {
        const url = new URL(value);
        return url.protocol === "https:" &&
            url.hostname.toLowerCase() === "github.com" &&
            !url.port &&
            !url.username &&
            !url.password
            ? url.href
            : null;
    } catch {
        return null;
    }
}

function safeAvatarUrl(value) {
    try {
        const url = new URL(value);
        return url.protocol === "https:" &&
            url.hostname.toLowerCase() === "avatars.githubusercontent.com" &&
            !url.port
            ? url.href
            : null;
    } catch {
        return null;
    }
}

function formatTimestamp(value) {
    if (!value) return "Date unavailable";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "Date unavailable";
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
    }).format(date);
}

function createInsightPanel(className, title, description) {
    const panel = createElement("section", `insight-panel ${className}`);
    const heading = createElement("div", "insight-heading");
    heading.append(
        createElement("h3", "", title),
        createElement("p", "", description)
    );
    const body = createElement("div", "insight-body");
    body.setAttribute("aria-live", "polite");
    body.setAttribute("aria-busy", "true");
    body.append(createElement("div", "insight-loading", `Loading ${title.toLowerCase()}...`));
    panel.append(heading, body);
    return { panel, body };
}

function setInsightMessage(container, message, type = "empty") {
    container.replaceChildren(
        createElement("p", `insight-message ${type}`, message)
    );
    container.setAttribute("aria-busy", "false");
}

function renderHealth(container, health) {
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");

    const summary = createElement("div", "health-summary");
    const score = health.score;
    const scoreRing = createElement("div", "health-score-ring");
    scoreRing.setAttribute(
        "role",
        score === null || score === undefined ? "img" : "meter"
    );
    scoreRing.setAttribute(
        "aria-label",
        score === null || score === undefined
            ? "Repository health score unavailable"
            : `RepoLens estimated repository health score: ${score} out of 100`
    );
    if (Number.isFinite(Number(score))) {
        scoreRing.style.setProperty(
            "--score-percent",
            `${Math.max(0, Math.min(100, Number(score)))}%`
        );
        scoreRing.append(
            createElement("strong", "", String(score)),
            createElement("span", "", "out of 100")
        );
    } else {
        scoreRing.classList.add("unavailable");
        scoreRing.append(
            createElement("strong", "", "—"),
            createElement("span", "", "unavailable")
        );
    }

    const scoreCopy = createElement("div", "health-score-copy");
    scoreCopy.append(
        createElement("strong", "", score === null || score === undefined
            ? "Not enough data"
            : "Repository health estimate"),
        createElement(
            "p",
            "",
            `${health.availableWeight || 0}% of weighted factors had usable data. This is a RepoLens estimate, not a GitHub rating.`
        )
    );
    summary.append(scoreRing, scoreCopy);
    container.append(summary);

    const factors = Array.isArray(health.factors) ? health.factors : [];
    const factorList = createElement("div", "health-factors");
    factors.forEach((factor) => {
        const row = createElement("div", "health-factor");
        const label = createElement("div", "health-factor-label");
        label.append(
            createElement("strong", "", factor.name || "Factor"),
            createElement(
                "span",
                "",
                factor.score === null || factor.score === undefined
                    ? "Unavailable"
                    : `${factor.score}/100`
            )
        );
        const track = createElement("div", "health-factor-track");
        track.setAttribute("aria-hidden", "true");
        const fill = createElement("span", "health-factor-fill");
        fill.style.width = factor.score === null || factor.score === undefined
            ? "0%"
            : `${Math.max(0, Math.min(100, factor.score))}%`;
        track.append(fill);
        row.append(
            label,
            track,
            createElement("p", "", factor.explanation || "No explanation provided.")
        );
        factorList.append(row);
    });
    container.append(factorList);

    const highlights = createElement("div", "health-highlights");
    const strengths = Array.isArray(health.strengths) ? health.strengths : [];
    const attention = Array.isArray(health.areasForAttention)
        ? health.areasForAttention
        : [];
    highlights.append(
        createHighlightList("Positive signals", strengths),
        createHighlightList("Needs attention", attention)
    );
    container.append(highlights);
    container.append(createElement(
        "p",
        "insight-limitation",
        health.disclaimer || "This score is an estimate based on available GitHub data."
    ));
}

function createHighlightList(title, values) {
    const group = createElement("div", "health-highlight");
    group.append(createElement("h4", "", title));
    const list = createElement("ul", "");
    if (values.length === 0) {
        list.append(createElement("li", "", "No signals to report."));
    } else {
        values.forEach((value) => list.append(createElement("li", "", value)));
    }
    group.append(list);
    return group;
}

function renderActivity(container, activity) {
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");
    if (!activity.available) {
        setInsightMessage(
            container,
            activity.unavailableReason || "Commit activity is not available yet."
        );
        return;
    }

    const metrics = createElement("div", "activity-metrics");
    [
        [formatNumber(activity.totalCommits), "commits in period"],
        [Number(activity.averageCommitsPerWeek).toFixed(1), "average per week"],
        [activity.mostActiveDay || "—", "most active day"],
    ].forEach(([value, label]) => {
        const metric = createElement("div", "activity-metric");
        metric.append(
            createElement("strong", "", value),
            createElement("span", "", label)
        );
        metrics.append(metric);
    });
    container.append(metrics);

    const weeks = Array.isArray(activity.weeklyActivity)
        ? activity.weeklyActivity
        : [];
    if (weeks.length === 0) {
        container.append(createElement(
            "p",
            "insight-message",
            "No weekly activity points were returned."
        ));
        return;
    }

    const max = Math.max(...weeks.map((week) => Number(week.commits) || 0));
    const chart = createElement("div", "activity-chart");
    chart.setAttribute("role", "img");
    chart.setAttribute(
        "aria-label",
        `Weekly commit counts over the last ${activity.periodDays} days: ${
            weeks.map((week) => `${week.weekStarting}: ${week.commits}`).join("; ")
        }`
    );
    weeks.forEach((week) => {
        const item = createElement("div", "activity-week");
        const count = Math.max(0, Number(week.commits) || 0);
        const bar = createElement("div", "activity-bar-track");
        const fill = createElement("span", "activity-bar");
        fill.style.height = max === 0 ? "0%" : `${count / max * 100}%`;
        bar.append(fill);
        item.append(
            createElement("span", "activity-count", formatNumber(count)),
            bar,
            createElement(
                "span",
                "activity-week-label",
                week.weekStarting || ""
            )
        );
        chart.append(item);
    });
    container.append(chart);
}

function renderCommits(container, data, repositoryUrl, requestId) {
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");
    const commits = Array.isArray(data.commits) ? data.commits : [];
    if (commits.length === 0) {
        setInsightMessage(container, "No commits were returned for this page.");
        return;
    }

    const list = createElement("ol", "commit-list");
    commits.forEach((commit) => {
        const item = createElement("li", "commit-item");
        const marker = createElement("span", "commit-marker");
        marker.setAttribute("aria-hidden", "true");
        const content = createElement("div", "commit-content");
        const title = createElement("p", "commit-message", commit.message || "Commit message unavailable");
        const details = createElement("div", "commit-details");
        details.append(
            createElement("span", "", commit.author || "Unknown author"),
            createElement("time", "", formatTimestamp(commit.date))
        );
        if (commit.date) details.lastChild.dateTime = commit.date;
        const shortSha = createElement("span", "commit-sha", commit.shortSha || "");
        const commitUrl = safeGitHubUrl(commit.htmlUrl);
        if (commitUrl) {
            const link = createElement("a", "commit-sha-link", commit.shortSha || "Commit");
            link.href = commitUrl;
            link.target = "_blank";
            link.rel = "noopener noreferrer";
            details.append(link);
        } else {
            details.append(shortSha);
        }
        content.append(title, details);
        item.append(marker, content);
        list.append(item);
    });
    container.append(list);

    const pagination = createElement("div", "commit-pagination");
    const page = Number(data.page) || 1;
    const previous = createElement("button", "", "Previous");
    previous.type = "button";
    previous.disabled = page <= 1;
    previous.addEventListener("click", () => {
        loadCommitPage(container, repositoryUrl, page - 1, requestId);
    });
    const next = createElement("button", "", "Next");
    next.type = "button";
    next.disabled = commits.length < (Number(data.limit) || 10);
    next.addEventListener("click", () => {
        loadCommitPage(container, repositoryUrl, page + 1, requestId);
    });
    pagination.append(
        previous,
        createElement("span", "", `Page ${page}`),
        next
    );
    container.append(pagination);
}

function renderDeveloperContributors(
    container,
    data,
    repositoryUrl,
    requestId,
    profileContainer
) {
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");
    if (data.available === false) {
        setInsightMessage(
            container,
            data.limitation || "Contributor collaboration data is unavailable."
        );
        return;
    }
    const contributors = Array.isArray(data.contributors)
        ? data.contributors
        : [];
    if (contributors.length === 0) {
        setInsightMessage(
            container,
            data.limitation || "No contributor data is available."
        );
        return;
    }

    const metrics = createElement("div", "developer-metrics");
    [
        [formatNumber(data.totalContributionsReturned), "returned contributions"],
        [formatNumber(data.contributorsReturned), "contributors returned"],
        [data.topContributorShare == null ? "—" : `${data.topContributorShare}%`, "top contributor share"],
        [data.topFiveContributorShare == null ? "—" : `${data.topFiveContributorShare}%`, "top five share"],
    ].forEach(([value, label]) => {
        const metric = createElement("div", "developer-metric");
        metric.append(
            createElement("strong", "", value),
            createElement("span", "", label)
        );
        metrics.append(metric);
    });
    container.append(metrics);
    if (Array.isArray(data.observations) && data.observations.length > 0) {
        const observations = createElement("ul", "collaboration-observations");
        data.observations.forEach((observation) => {
            const item = createElement("li", "collaboration-observation");
            item.append(
                createElement("strong", "", observation.title),
                createElement("span", "", observation.evidence)
            );
            observations.append(item);
        });
        container.append(observations);
    }

    const note = createElement(
        "p",
        "developer-note",
        `Ranks use GitHub's returned contribution counts. Shares are of the returned subset, not an estimate of effort. ${data.limitation || ""}`
    );
    container.append(note);
    const list = createElement("ol", "developer-contributor-list");
    const showCount = { value: 20 };
    const renderRows = () => {
        list.replaceChildren();
        contributors.slice(0, showCount.value).forEach((contributor, index) => {
            const row = createElement("li", "developer-contributor");
            const avatar = createElement("span", "contributor-avatar-fallback");
            const avatarUrl = safeAvatarUrl(contributor.avatarUrl);
            if (avatarUrl) {
                const image = createElement("img");
                image.src = avatarUrl;
                image.alt = "";
                image.loading = "lazy";
                image.addEventListener("error", () => image.remove());
                avatar.append(image);
            }
            const details = createElement("div", "developer-contributor-details");
            const login = contributor.login || "Deleted or unavailable account";
            const select = createElement(
                "button",
                "developer-profile-button",
                login
            );
            select.type = "button";
            select.disabled = !contributor.login;
            select.addEventListener("click", () => {
                if (!contributor.login) return;
                loadContributorProfile(
                    profileContainer,
                    repositoryUrl,
                    contributor.login,
                    requestId
                );
            });
            details.append(select);
            if (contributor.bot) {
                details.append(createElement("span", "bot-badge", "Bot"));
            }
            const count = Number(contributor.contributions) || 0;
            details.append(createElement(
                "span",
                "developer-contributor-meta",
                `${formatNumber(count)} contributions`
            ));
            const profileUrl = safeGitHubUrl(contributor.profileUrl);
            if (profileUrl) {
                const external = createElement("a", "developer-external-link", "GitHub ↗");
                external.href = profileUrl;
                external.target = "_blank";
                external.rel = "noopener noreferrer";
                external.setAttribute("aria-label", `Open ${login} on GitHub in a new tab`);
                details.append(external);
            }
            const rank = createElement("span", "developer-rank", `#${index + 1}`);
            row.append(rank, avatar, details);
            const share = contributor.contributionShare == null
                ? null
                : Number(contributor.contributionShare);
            const shareNode = createElement(
                "span",
                "developer-contribution-share",
                share != null && Number.isFinite(share)
                    ? `${share.toFixed(1)}%` : "—"
            );
            shareNode.setAttribute(
                "aria-label",
                share != null && Number.isFinite(share)
                    ? `${share.toFixed(1)} percent of returned contributions`
                    : "Contribution share unavailable"
            );
            row.append(shareNode);
            list.append(row);
        });
    };
    renderRows();
    container.append(list);
    if (contributors.length > showCount.value) {
        const more = createElement(
            "button",
            "developer-more",
            "Show more contributors"
        );
        more.type = "button";
        more.addEventListener("click", () => {
            showCount.value = Math.min(
                contributors.length,
                showCount.value + 20
            );
            renderRows();
            if (showCount.value >= contributors.length) more.remove();
        });
        container.append(more);
    }
}

function loadContributorProfile(container, repositoryUrl, username, requestId) {
    const profileRequestId = ++contributorProfileRequestId;
    container.replaceChildren(
        createElement("p", "insight-loading", `Loading ${username}'s public repository activity...`)
    );
    container.setAttribute("aria-busy", "true");
    void requestInsight(
        `${CONFIG.endpoints.contributorProfile}/${encodeURIComponent(username)}`,
        repositoryUrl,
        requestId,
        { limit: "30" }
    ).then((profile) => {
        if (!profile || profileRequestId !== contributorProfileRequestId
                || requestId !== analysisRequestId) return;
        renderContributorProfile(container, profile);
    }).catch((error) => {
        if (profileRequestId !== contributorProfileRequestId
                || requestId !== analysisRequestId) return;
        setInsightMessage(
            container,
            error.message || "Unable to load contributor activity.",
            "error"
        );
    });
}

function renderContributorProfile(container, profile) {
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");
    const header = createElement("div", "developer-profile-header");
    const avatar = createElement("span", "contributor-avatar-fallback profile-avatar");
    const avatarUrl = safeAvatarUrl(profile.avatarUrl);
    if (avatarUrl) {
        const image = createElement("img");
        image.src = avatarUrl;
        image.alt = "";
        image.addEventListener("error", () => image.remove());
        avatar.append(image);
    }
    const heading = createElement("div", "developer-profile-heading");
    heading.append(
        createElement("h4", "", profile.displayName || profile.username || "Contributor"),
        createElement("span", "", `@${profile.username || "unavailable"}${profile.bot ? " · Bot account" : ""}`)
    );
    const linkUrl = safeGitHubUrl(profile.profileUrl);
    if (linkUrl) {
        const link = createElement("a", "developer-external-link", "View profile ↗");
        link.href = linkUrl;
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        heading.append(link);
    }
    header.append(avatar, heading);
    container.append(header);

    const metrics = createElement("div", "developer-metrics profile-metrics");
    [
        [profile.contributions == null ? "—" : formatNumber(profile.contributions), "repository contributions"],
        [profile.authoredPullRequests == null ? "—" : formatNumber(profile.authoredPullRequests), "authored pull requests"],
        [profile.authoredIssues == null ? "—" : formatNumber(profile.authoredIssues), "authored issues"],
        [profile.reviewedPullRequests == null ? "—" : formatNumber(profile.reviewedPullRequests), "PRs reviewed (search)"],
    ].forEach(([value, label]) => {
        const metric = createElement("div", "developer-metric");
        metric.append(createElement("strong", "", value), createElement("span", "", label));
        metrics.append(metric);
    });
    container.append(metrics);
    container.append(createElement(
        "p",
        "developer-note",
        `First and most recent observed commits: ${profile.firstObservedContribution ? formatTimestamp(profile.firstObservedContribution) : "unavailable"} / ${profile.mostRecentObservedContribution ? formatTimestamp(profile.mostRecentObservedContribution) : "unavailable"}. These dates and charts cover the latest matching commits returned by GitHub, not full history.`
    ));

    const activityTitle = createElement("h5", "developer-subheading", "Commits by day · last 30 days (returned sample)");
    container.append(activityTitle);
    container.append(renderDeveloperActivity(profile.commitActivity, "commits"));
    const commits = Array.isArray(profile.recentCommits) ? profile.recentCommits : [];
    const commitList = createElement("ul", "developer-activity-list");
    commits.slice(0, 10).forEach((commit) => {
        const item = createElement("li", "");
        const linkUrl = safeGitHubUrl(commit.htmlUrl);
        const title = createElement(
            linkUrl ? "a" : "span",
            "developer-activity-link",
            commit.message || "Commit message unavailable"
        );
        if (linkUrl) {
            title.href = linkUrl;
            title.target = "_blank";
            title.rel = "noopener noreferrer";
        }
        item.append(title, createElement("span", "developer-activity-date", formatTimestamp(commit.date)));
        commitList.append(item);
    });
    if (commits.length === 0) {
        commitList.append(createElement("li", "insight-message", "No matching commits were returned."));
    }
    container.append(createElement("h5", "developer-subheading", "Recent commits"));
    container.append(commitList);
    container.append(createElement("p", "developer-note", profile.limitation || ""));
}

function renderPullRequestAnalytics(container, data, repositoryUrl, requestId) {
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");
    const metrics = createElement("div", "developer-metrics");
    [
        [formatNumber(data.totalReturned), "PRs returned"],
        [formatNumber(data.openPullRequests), "open"],
        [formatNumber(data.closedPullRequests), "closed (includes merged)"],
        [formatNumber(data.mergedPullRequests), "merged"],
        [data.mergeRate == null ? "—" : `${Number(data.mergeRate).toFixed(1)}%`, "merged / returned closed"],
        [data.averageTimeToMergeHours == null ? "—" : `${Number(data.averageTimeToMergeHours).toFixed(1)} h`, "average time to merge"],
    ].forEach(([value, label]) => {
        const metric = createElement("div", "developer-metric");
        metric.append(createElement("strong", "", value), createElement("span", "", label));
        metrics.append(metric);
    });
    container.append(metrics);
    container.append(createElement("p", "developer-note", `${data.scope || ""} ${data.limitation || ""}`));
    container.append(createElement("h5", "developer-subheading", `PRs opened · last ${data.periodDays} days`));
    container.append(renderDeveloperActivity(data.activity, "opened"));
    const authorList = createElement("div", "developer-author-list");
    (data.authorDistribution || []).slice(0, 5).forEach((author) => {
        authorList.append(createElement(
            "span",
            "developer-author-chip",
            `${author.username}: ${author.count} (${Number(author.percentage).toFixed(1)}%)`
        ));
    });
    if (authorList.childElementCount) {
        container.append(createElement("h5", "developer-subheading", "Authorship among returned PRs"));
        container.append(authorList);
    }
    container.append(createElement(
        "p",
        "developer-note",
        `${formatNumber(data.draftPullRequests)} drafts; ${formatNumber(data.reviewComments)} review comments; ${data.uniqueReviewersObserved == null ? "Reviewer identity unavailable" : `${formatNumber(data.uniqueReviewersObserved)} unique reviewers observed`} in recent PR review lists.`
    ));
    appendPullRequestList(container, "Recently merged", data.recentlyMerged);
    appendPullRequestList(container, "Recently opened", data.recentlyOpened);
    appendDeveloperPagination(
        container,
        CONFIG.endpoints.pullRequests,
        data,
        repositoryUrl,
        requestId,
        renderPullRequestAnalytics
    );
}

function appendPullRequestList(container, heading, entries) {
    container.append(createElement("h5", "developer-subheading", heading));
    const list = createElement("ul", "developer-activity-list");
    (entries || []).slice(0, 5).forEach((entry) => {
        const item = createElement("li", "");
        const url = safeGitHubUrl(entry.htmlUrl);
        const title = createElement(
            url ? "a" : "span",
            "developer-activity-link",
            `#${entry.number} ${entry.title}${entry.draft ? " · Draft" : ""}`
        );
        if (url) {
            title.href = url;
            title.target = "_blank";
            title.rel = "noopener noreferrer";
        }
        item.append(
            title,
            createElement("span", "developer-activity-date", entry.author || "Author unavailable")
        );
        list.append(item);
    });
    if (!entries || entries.length === 0) {
        list.append(createElement("li", "insight-message", "No pull requests in this returned sample."));
    }
    container.append(list);
}

function renderIssueAnalytics(container, data, repositoryUrl, requestId) {
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");
    const metrics = createElement("div", "developer-metrics");
    [
        [formatNumber(data.totalReturned), "issues returned"],
        [formatNumber(data.openIssues), "open"],
        [formatNumber(data.closedIssues), "closed"],
        [data.averageResolutionHours == null ? "—" : `${Number(data.averageResolutionHours).toFixed(1)} h`, "average resolution"],
    ].forEach(([value, label]) => {
        const metric = createElement("div", "developer-metric");
        metric.append(createElement("strong", "", value), createElement("span", "", label));
        metrics.append(metric);
    });
    container.append(metrics);
    container.append(createElement("p", "developer-note", `${data.scope || ""} ${data.limitation || ""}`));
    container.append(createElement("h5", "developer-subheading", `Opened / closed · last ${data.periodDays} days`));
    container.append(renderDeveloperActivity(data.activity, "issues"));
    const labels = createElement("div", "developer-author-list");
    (data.labels || []).slice(0, 10).forEach((label) => {
        labels.append(createElement("span", "developer-author-chip", `${label.name} · ${label.count}`));
    });
    if (labels.childElementCount) {
        container.append(createElement("h5", "developer-subheading", "Labels in returned issues"));
        container.append(labels);
    }
    const authors = createElement("div", "developer-author-list");
    (data.authorDistribution || []).slice(0, 5).forEach((author) => {
        authors.append(createElement(
            "span",
            "developer-author-chip",
            `${author.username}: ${author.issueCount} (${Number(author.percentage).toFixed(1)}%)`
        ));
    });
    if (authors.childElementCount) {
        container.append(createElement("h5", "developer-subheading", "Issue authorship in returned sample"));
        container.append(authors);
    }
    appendIssueList(container, "Recently opened", data.recentlyOpened);
    appendIssueList(container, "Recently closed", data.recentlyClosed);
    appendIssueList(container, "Recently active", data.recentlyActive);
    appendDeveloperPagination(
        container,
        CONFIG.endpoints.issues,
        data,
        repositoryUrl,
        requestId,
        renderIssueAnalytics
    );
}

function appendDeveloperPagination(
    container,
    endpoint,
    data,
    repositoryUrl,
    requestId,
    renderer
) {
    const pagination = createElement("div", "developer-pagination");
    const page = Number(data.page) || 1;
    const previous = createElement("button", "", "Previous sample");
    previous.type = "button";
    previous.disabled = page <= 1;
    previous.addEventListener("click", () => {
        container.replaceChildren(createElement("div", "insight-loading", "Loading previous page..."));
        void loadInsight(
            container,
            endpoint,
            repositoryUrl,
            requestId,
            (target, result) => renderer(target, result, repositoryUrl, requestId),
            {
                period: String(data.periodDays),
                page: String(page - 1),
                limit: String(data.limit)
            }
        );
    });
    const next = createElement("button", "", "Next sample");
    next.type = "button";
    next.disabled = !data.hasNextPage;
    next.addEventListener("click", () => {
        container.replaceChildren(createElement("div", "insight-loading", "Loading next page..."));
        void loadInsight(
            container,
            endpoint,
            repositoryUrl,
            requestId,
            (target, result) => renderer(target, result, repositoryUrl, requestId),
            {
                period: String(data.periodDays),
                page: String(page + 1),
                limit: String(data.limit)
            }
        );
    });
    pagination.append(
        previous,
        createElement("span", "", `Page ${page} · up to ${data.limit} records`),
        next
    );
    container.append(pagination);
}

function appendIssueList(container, heading, entries) {
    container.append(createElement("h5", "developer-subheading", heading));
    const list = createElement("ul", "developer-activity-list");
    (entries || []).slice(0, 5).forEach((entry) => {
        const item = createElement("li", "");
        const url = safeGitHubUrl(entry.htmlUrl);
        const title = createElement(
            url ? "a" : "span",
            "developer-activity-link",
            `#${entry.number} ${entry.title}`
        );
        if (url) {
            title.href = url;
            title.target = "_blank";
            title.rel = "noopener noreferrer";
        }
        item.append(title, createElement("span", "developer-activity-date", entry.author || "Author unavailable"));
        list.append(item);
    });
    if (!entries || entries.length === 0) {
        list.append(createElement("li", "insight-message", "No issues in this returned sample."));
    }
    container.append(list);
}

function renderDeveloperActivity(points, label) {
    const wrapper = createElement("div", "developer-chart-wrap");
    const chart = createElement("div", "developer-activity-chart");
    chart.setAttribute("role", "img");
    const values = Array.isArray(points) ? points : [];
    const maximum = Math.max(
        1,
        ...values.map((point) =>
            (Number(point.opened) || 0) + (Number(point.closed) || 0)
        )
    );
    const chartDescription = values.map((point) => {
        const opened = Number(point.commits ?? point.opened) || 0;
        const closed = Number(point.closed) || 0;
        return `${point.date}: ${opened} ${label}${closed ? `, ${closed} closed` : ""}`;
    }).join("; ");
    chart.setAttribute(
        "aria-label",
        `${label} activity by day${chartDescription ? `: ${chartDescription}` : " unavailable"}.`
    );
    values.forEach((point) => {
        const opened = Number(point.commits ?? point.opened) || 0;
        const closed = Number(point.closed) || 0;
        const total = opened + closed;
        const bar = createElement("span", "developer-activity-bar");
        bar.title = `${point.date}: ${opened} ${label}${closed ? `, ${closed} closed` : ""}`;
        bar.style.height = `${total === 0 ? 0 : Math.max(4, total / maximum * 100)}%`;
        const openFill = createElement("span", "developer-bar-opened");
        const closedFill = createElement("span", "developer-bar-closed");
        const denominator = Math.max(1, total);
        openFill.style.height = `${opened / denominator * 100}%`;
        closedFill.style.height = `${closed / denominator * 100}%`;
        bar.append(openFill, closedFill);
        chart.append(bar);
    });
    if (values.length === 0) {
        chart.append(createElement("span", "insight-message", "Activity unavailable."));
    }
    wrapper.append(chart);
    if (values.length > 1) {
        const range = createElement("div", "developer-chart-range");
        range.append(
            createElement("span", "", values[0].date),
            createElement("span", "", values.at(-1).date)
        );
        wrapper.append(range);
    }
    return wrapper;
}

function createDeveloperSkeleton() {
    const skeleton = createElement("div", "developer-skeleton");
    skeleton.setAttribute("aria-hidden", "true");
    for (let index = 0; index < 3; index++) {
        skeleton.append(createElement("span", ""));
    }
    return skeleton;
}

function loadDeveloperIntelligence(container, repositoryUrl, requestId) {
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");
    const grid = createElement("div", "developer-intelligence-grid");
    const leaderboard = createElement("section", "developer-card developer-leaderboard-card");
    const pullRequests = createElement("section", "developer-card");
    const issues = createElement("section", "developer-card");
    const profileContainer = createElement("section", "developer-profile-card");
    const leaderboardBody = createElement("div", "developer-card-body");
    const pullRequestBody = createElement("div", "developer-card-body");
    const issueBody = createElement("div", "developer-card-body");
    profileContainer.setAttribute("aria-live", "polite");
    leaderboard.append(
        createElement("h4", "", "Contributor leaderboard"),
        leaderboardBody
    );
    pullRequests.append(
        createElement("h4", "", "Pull request analytics"),
        pullRequestBody
    );
    issues.append(
        createElement("h4", "", "Issue analytics"),
        issueBody
    );
    [leaderboardBody, pullRequestBody, issueBody].forEach((body) => {
        body.setAttribute("aria-busy", "true");
        body.append(createDeveloperSkeleton());
    });
    grid.append(leaderboard, pullRequests, issues);
    container.append(grid, profileContainer);
    setInsightMessage(
        profileContainer,
        "Select a contributor to inspect their public repository activity."
    );
    void loadInsight(
        leaderboardBody,
        CONFIG.endpoints.collaboration,
        repositoryUrl,
        requestId,
        (target, data) => renderDeveloperContributors(
            target,
            data,
            repositoryUrl,
            requestId,
            profileContainer
        )
    );
    void loadInsight(
        pullRequestBody,
        CONFIG.endpoints.pullRequests,
        repositoryUrl,
        requestId,
        (target, data) => renderPullRequestAnalytics(
            target,
            data,
            repositoryUrl,
            requestId
        ),
        { period: "30", page: "1", limit: "100" }
    );
    void loadInsight(
        issueBody,
        CONFIG.endpoints.issues,
        repositoryUrl,
        requestId,
        (target, data) => renderIssueAnalytics(
            target,
            data,
            repositoryUrl,
            requestId
        ),
        { period: "30", page: "1", limit: "100" }
    );
}

function renderCodebase(container, data) {
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");
    if (!data.available) {
        setInsightMessage(
            container,
            data.unavailableReason || "Repository tree data is unavailable."
        );
        return;
    }

    const composition = data.composition || {};
    const summary = createElement("div", "codebase-summary");
    [
        [formatNumber(data.totalTrackedFiles), "tracked files"],
        [formatNumber(data.totalDirectories), "directories"],
        [formatBytes(data.totalKnownBlobSizeBytes), "reported blob size"],
        [String((composition.extensions || []).length), "file types"],
    ].forEach(([value, label]) => {
        const metric = createElement("div", "codebase-metric");
        metric.append(
            createElement("strong", "", value),
            createElement("span", "", label)
        );
        summary.append(metric);
    });
    container.append(summary);

    const missingSizes = Number(data.filesMissingSize) || 0;
    if (missingSizes > 0) {
        container.append(createElement(
            "p",
            "codebase-note",
            `${formatNumber(missingSizes)} files did not include size metadata; known byte totals exclude them.`
        ));
    }
    if (data.treeTruncated) {
        container.append(createElement(
            "p",
            "codebase-note codebase-warning",
            "GitHub marked this recursive tree as truncated. All counts and breakdowns are partial."
        ));
    }
    if (data.explorerTruncated) {
        container.append(createElement(
            "p",
            "codebase-note codebase-warning",
            "The folder explorer is limited to the first 3,000 returned entries."
        ));
    }

    const breakdowns = createElement("div", "codebase-breakdowns");
    breakdowns.append(
        renderTypeBreakdown("File categories", composition.categories || []),
        renderTypeBreakdown("File extensions", composition.extensions || [])
    );
    if ((composition.languages || []).length > 0) {
        breakdowns.append(
            renderTypeBreakdown("Languages inferred from extensions", composition.languages)
        );
    }
    container.append(breakdowns);

    const directoryPanel = createElement("section", "codebase-subsection");
    directoryPanel.append(
        createElement("h4", "", "Directory size distribution"),
        createElement(
            "p",
            "codebase-note",
            "Bars show each directory's share of known blob bytes. Nested directory shares overlap; directories with unknown sizes are excluded from byte totals."
        )
    );
    const directoryChart = createElement("div", "directory-chart");
    const directories = Array.isArray(data.directories) ? data.directories : [];
    directories
        .filter((directory) =>
            directory.path === "(root)" || !directory.path.includes("/")
        )
        .sort((a, b) => {
            const aSize = Number(a.knownSizeBytes) || 0;
            const bSize = Number(b.knownSizeBytes) || 0;
            return bSize - aSize || String(a.path).localeCompare(String(b.path));
        })
        .slice(0, 12)
        .forEach((directory) => {
            const row = createElement("button", "directory-chart-row");
            row.type = "button";
            row.setAttribute(
                "aria-label",
                `Explore ${directory.path}: ${formatNumber(directory.fileCount)} files, ${directory.sizeSharePercent ?? "unavailable"} percent of known bytes`
            );
            const label = createElement("span", "directory-chart-label", directory.path);
            const track = createElement("span", "directory-chart-track");
            const fill = createElement("span", "directory-chart-fill");
            fill.style.width = `${Math.max(0, Math.min(100, Number(directory.sizeSharePercent) || 0))}%`;
            track.append(fill);
            row.append(
                label,
                track,
                createElement("span", "directory-chart-count", `${formatNumber(directory.fileCount)} files`),
                createElement(
                    "span",
                    "directory-chart-size",
                    directory.sizeSharePercent === null || directory.sizeSharePercent === undefined
                        ? "—"
                        : `${Number(directory.sizeSharePercent).toFixed(1)}%`
                )
            );
            row.addEventListener("click", () => {
                const selector = container.querySelector("#codebase-directory-select");
                if (selector) {
                    selector.value = directory.path === "(root)" ? "" : directory.path;
                    selector.dispatchEvent(new Event("change"));
                }
            });
            directoryChart.append(row);
        });
    if (directoryChart.childElementCount === 0) {
        directoryChart.append(createElement("p", "insight-message", "No directory byte totals are available."));
    }
    directoryPanel.append(directoryChart);
    container.append(directoryPanel);

    const explorer = createElement("section", "codebase-subsection");
    explorer.append(
        createElement("h4", "", "File explorer"),
        createElement(
            "p",
            "codebase-note",
            "Select a folder to inspect its immediate contents. Empty directories are not stored in Git and cannot be shown."
        )
    );
    const entries = Array.isArray(data.treeEntries) ? data.treeEntries : [];
    const selectLabel = createElement("label", "codebase-select-label", "Current folder");
    const select = createElement("select", "codebase-directory-select");
    select.id = "codebase-directory-select";
    select.setAttribute("aria-label", "Choose a repository folder");
    select.append(new Option("/", ""));
    entries
        .filter((entry) => entry.type === "directory")
        .sort((a, b) => String(a.path).localeCompare(String(b.path)))
        .forEach((entry) => select.append(new Option(entry.path, entry.path)));
    selectLabel.append(select);
    const contents = createElement("div", "codebase-file-list");
    const renderContents = () => {
        contents.replaceChildren();
        const directory = select.value;
        const children = entries.filter((entry) => {
            if (directory === "") {
                return !entry.path.includes("/") ||
                    (entry.type === "directory" && !entry.path.includes("/"));
            }
            const prefix = `${directory}/`;
            if (!entry.path.startsWith(prefix)) return false;
            const remainder = entry.path.slice(prefix.length);
            return remainder.length > 0 && !remainder.includes("/");
        });
        children.sort((a, b) =>
            (a.type === b.type ? 0 : a.type === "directory" ? -1 : 1) ||
            String(a.name).localeCompare(String(b.name))
        );

        if (children.length === 0) {
            contents.append(createElement(
                "p",
                "insight-message",
                "No child entries are available in this folder."
            ));
            return;
        }

        let visibleCount = 100;
        const appendVisible = () => {
            const batch = children.slice(
                contents.querySelectorAll(".codebase-entry").length,
                visibleCount
            );
            batch.forEach((entry) => {
                const row = createElement("div", "codebase-entry");
                if (entry.type === "directory") {
                    const button = createElement("button", "codebase-folder-button", `▸ ${entry.name}/`);
                    button.type = "button";
                    button.addEventListener("click", () => {
                        select.value = entry.path;
                        renderContents();
                    });
                    row.append(button, createElement("span", "codebase-entry-meta", "Folder"));
                } else {
                    const link = createElement("a", "codebase-file-link", entry.name);
                    const href = safeGitHubUrl(entry.htmlUrl);
                    if (href) {
                        link.href = href;
                        link.target = "_blank";
                        link.rel = "noopener noreferrer";
                    } else {
                        link.removeAttribute("href");
                    }
                    row.append(
                        link,
                        createElement(
                            "span",
                            "codebase-entry-meta",
                            `${entry.extension || "[directory]"} · ${
                                entry.sizeBytes === null || entry.sizeBytes === undefined
                                    ? "size unavailable"
                                    : formatBytes(entry.sizeBytes)
                            }`
                        )
                    );
                }
                contents.append(row);
            });
            contents.querySelector(".codebase-more")?.remove();
            if (visibleCount < children.length) {
                const more = createElement("button", "codebase-more", "Show 100 more");
                more.type = "button";
                more.addEventListener("click", () => {
                    visibleCount += 100;
                    appendVisible();
                });
                contents.append(more);
            }
        };
        appendVisible();
    };
    select.addEventListener("change", renderContents);
    renderContents();
    explorer.append(selectLabel, contents);
    container.append(explorer);

    const largestSection = createElement("section", "codebase-subsection");
    largestSection.append(
        createElement("h4", "", "Largest files"),
        createElement(
            "p",
            "codebase-note",
            "Ranked by reported blob size. Size alone does not demonstrate a performance or maintainability problem."
        )
    );
    const largest = Array.isArray(data.largestFiles) ? data.largestFiles : [];
    if (largest.length === 0) {
        largestSection.append(createElement(
            "p",
            "insight-message",
            "No files with known size metadata were returned."
        ));
    } else {
        const list = createElement("ol", "largest-file-list");
        largest.forEach((file) => {
            const item = createElement("li", "largest-file-item");
            const link = createElement("a", "largest-file-link", file.path || file.name);
            const href = safeGitHubUrl(file.htmlUrl);
            if (href) {
                link.href = href;
                link.target = "_blank";
                link.rel = "noopener noreferrer";
            }
            item.append(
                link,
                createElement("span", "largest-file-type", file.extension || "[no extension]"),
                createElement("strong", "", file.sizeBytes === null || file.sizeBytes === undefined
                    ? "Size unavailable"
                    : formatBytes(file.sizeBytes))
            );
            list.append(item);
        });
        largestSection.append(list);
    }
    container.append(largestSection);

    const hotspots = Array.isArray(data.hotspots) ? data.hotspots : [];
    const hotspotSection = createElement("section", "codebase-subsection");
    hotspotSection.append(
        createElement("h4", "", "Structural observations"),
        createElement(
            "p",
            "codebase-note",
            "Evidence-based structure signals, not measures of code quality, complexity, or technical debt."
        )
    );
    if (hotspots.length === 0) {
        hotspotSection.append(createElement(
            "p",
            "insight-message",
            "No structural observations met the documented thresholds."
        ));
    } else {
        const list = createElement("ul", "hotspot-list");
        hotspots.forEach((hotspot) => {
            const item = createElement("li", "hotspot-item");
            item.append(
                createElement("strong", "", hotspot.category || "Observation"),
                createElement("code", "", hotspot.path || "Repository"),
                createElement("p", "", hotspot.evidence || ""),
                createElement("p", "hotspot-suggestion", hotspot.suggestion || "")
            );
            list.append(item);
        });
        hotspotSection.append(list);
    }
    container.append(hotspotSection);
    container.append(createElement(
        "p",
        "insight-limitation",
        data.limitations || "Repository structure data may be incomplete."
    ));
}

function renderTypeBreakdown(title, values) {
    const section = createElement("section", "codebase-breakdown");
    section.append(createElement("h4", "", title));
    if (!Array.isArray(values) || values.length === 0) {
        section.append(createElement("p", "insight-message", "No file types to report."));
        return section;
    }

    const maxFiles = Math.max(...values.map((item) => Number(item.fileCount) || 0));
    values.slice(0, 12).forEach((item) => {
        const row = createElement("div", "type-stat-row");
        const track = createElement("span", "type-stat-track");
        const fill = createElement("span", "type-stat-fill");
        fill.style.width = maxFiles === 0
            ? "0%"
            : `${Math.max(0, Math.min(100, Number(item.fileCount) / maxFiles * 100))}%`;
        track.append(fill);
        row.append(
            createElement("span", "type-stat-name", item.name || "Other"),
            track,
            createElement("span", "type-stat-count", formatNumber(item.fileCount)),
            createElement("span", "type-stat-size", formatBytes(item.knownSizeBytes))
        );
        section.append(row);
    });
    return section;
}

async function requestInsight(endpoint, repositoryUrl, requestId, parameters = {}) {
    const query = new URLSearchParams({
        url: repositoryUrl,
        ...parameters,
    });
    const response = await fetch(`${endpoint}?${query}`, {
        method: "GET",
        credentials: "same-origin",
        headers: { Accept: "application/json" },
    });
    const data = await readJson(response);
    if (requestId !== analysisRequestId) return null;

    if (response.status === 401) {
        analysisRequestId += 1;
        showLogin("Your session has expired. Please sign in again.");
        return null;
    }
    if (!response.ok) {
        throw new Error(data.error || "Unable to load repository insights.");
    }
    return data;
}

async function loadInsight(panel, endpoint, repositoryUrl, requestId, render, parameters = {}) {
    try {
        const data = await requestInsight(endpoint, repositoryUrl, requestId, parameters);
        if (data && requestId === analysisRequestId) render(panel, data);
    } catch (error) {
        if (requestId !== analysisRequestId) return;
        console.error("Repository insight error:", error);
        setInsightMessage(panel, error.message || "Unable to load this insight.", "error");
    }
}

async function loadCommitPage(panel, repositoryUrl, page, requestId) {
    panel.setAttribute("aria-busy", "true");
    panel.replaceChildren(createElement("div", "insight-loading", "Loading recent commits..."));
    await loadInsight(
        panel,
        CONFIG.endpoints.commits,
        repositoryUrl,
        requestId,
        (target, data) => renderCommits(target, data, repositoryUrl, requestId),
        { page: String(page), limit: "10" }
    );
}

function loadRepositoryInsights(resultSection, repositoryUrl, requestId) {
    const insights = createElement("section", "repository-insights");
    const header = createElement("div", "insights-section-heading");
    header.append(
        createElement("span", "section-label", "REPOSITORY INSIGHTS"),
        createElement("p", "", "Signals and activity sourced from GitHub.")
    );
    const health = createInsightPanel(
        "health-panel",
        "Repository health",
        "An explainable RepoLens estimate"
    );
    const activity = createInsightPanel(
        "activity-panel",
        "Commit activity",
        "Weekly commits across the last 30 days"
    );
    const commits = createInsightPanel(
        "commits-panel",
        "Recent commits",
        "Latest changes from the default repository history"
    );
    const developers = createInsightPanel(
        "developer-intelligence-panel",
        "Developer intelligence",
        "Contributor activity, pull requests, issues, and evidence-based collaboration signals"
    );
    const codebase = createInsightPanel(
        "codebase-panel",
        "Code intelligence",
        "Repository files, composition, directories, largest files, and structural observations"
    );
    insights.append(
        header,
        codebase.panel,
        developers.panel,
        health.panel,
        activity.panel,
        commits.panel
    );
    resultSection.append(insights);

    void loadInsight(
        codebase.body,
        CONFIG.endpoints.codebase,
        repositoryUrl,
        requestId,
        renderCodebase,
        { limit: "10" }
    );
    loadDeveloperIntelligence(developers.body, repositoryUrl, requestId);
    void loadInsight(
        health.body,
        CONFIG.endpoints.health,
        repositoryUrl,
        requestId,
        renderHealth
    );
    void loadInsight(
        activity.body,
        CONFIG.endpoints.activity,
        repositoryUrl,
        requestId,
        renderActivity
    );
    void loadCommitPage(commits.body, repositoryUrl, 1, requestId);
}


/* 09. Language analytics — API
   --------------------------------------------------------- */

async function loadLanguageAnalytics(repositoryUrl, requestId) {
    const resultSection = document.getElementById("repository-result");

    if (!resultSection) return;

    // Remove analytics from the previous repository.
    document.getElementById("language-analytics")?.remove();

    const section = createElement("section", "language-analytics");
    section.id = "language-analytics";

    const heading = createElement("div", "analytics-heading");

    heading.append(
        createElement("h3", "", "Language Analytics"),
        createElement(
            "p",
            "",
            "Code composition and language distribution"
        )
    );

    const loading = createElement(
        "div",
        "language-loading",
        "Loading language statistics..."
    );

    section.append(heading, loading);
    resultSection.append(section);

    try {
        const response = await fetch(
            `${CONFIG.endpoints.languages}?url=${encodeURIComponent(repositoryUrl)}`,
            {
                method: "GET",
                credentials: "same-origin",
                headers: {
                    Accept: "application/json",
                },
            }
        );

        const data = await readJson(response);

        if (requestId !== analysisRequestId) return;

        if (response.status === 401) {
            analysisRequestId += 1;
            showLogin("Your session has expired. Please sign in again.");
            return;
        }

        if (!response.ok) {
            throw new Error(
                data.error || "Unable to load language analytics."
            );
        }

        renderLanguageAnalytics(section, data);

    } catch (error) {
        if (requestId !== analysisRequestId) return;
        console.error("Language analytics error:", error);

        loading.className = "language-error";
        loading.textContent =
            error.message || "Unable to load language analytics.";
    }
}


/* 10. Language analytics — rendering
   --------------------------------------------------------- */

function renderLanguageAnalytics(container, data) {
    container.replaceChildren();

    const heading = createElement("div", "analytics-heading");

    heading.append(
        createElement("h3", "", "Language Analytics"),
        createElement(
            "p",
            "",
            "Code composition and language distribution"
        )
    );

    const languages = Array.isArray(data.languages)
        ? data.languages.filter((language) => {
            return (
                language &&
                typeof language.language === "string" &&
                Number.isFinite(Number(language.bytes)) &&
                Number(language.bytes) >= 0
            );
        })
        : [];

    const totalBytes = Number(data.totalBytes) || 0;

    const summary = createElement("div", "language-summary");

    summary.append(
        createElement("span", "", "Total code size"),
        createElement("strong", "", formatBytes(totalBytes)),
        createElement(
            "span",
            "",
            `${languages.length} ${
                languages.length === 1 ? "language" : "languages"
            }`
        )
    );

    container.append(heading, summary);

    if (languages.length === 0 || totalBytes <= 0) {
        container.append(
            createElement(
                "p",
                "language-empty",
                "No language statistics available for this repository."
            )
        );

        return;
    }

    // Sort by byte count to ensure the primary language is correct.
    languages.sort((a, b) => Number(b.bytes) - Number(a.bytes));

    const primaryLanguage = languages[0];
    const primaryPercentage = Number(primaryLanguage.percentage) || 0;

    // Primary language summary.
    const primary = createElement("div", "primary-language");

    const primaryDot = createElement("span", "language-dot");
    primaryDot.style.backgroundColor = getLanguageColor(
        primaryLanguage.language,
        0
    );

    primary.append(
        primaryDot,
        createElement("span", "", "Primary language"),
        createElement("strong", "", primaryLanguage.language),
        createElement(
            "strong",
            "",
            `${primaryPercentage.toFixed(2)}%`
        )
    );

    container.append(primary);

    // Language distribution bar.
    const distribution = createElement(
        "div",
        "language-distribution"
    );

    distribution.setAttribute("role", "img");

    distribution.setAttribute(
        "aria-label",
        languages
            .map((language) => {
                const percentage = Number(language.percentage) || 0;

                return `${language.language}: ${percentage.toFixed(2)}%`;
            })
            .join(", ")
    );

    languages.forEach((language, index) => {
        const percentage = Math.min(
            100,
            Math.max(0, Number(language.percentage) || 0)
        );

        const segment = createElement("div", "language-segment");

        segment.style.width = `${percentage}%`;
        segment.style.backgroundColor = getLanguageColor(
            language.language,
            index
        );

        segment.title =
            `${language.language}: ${percentage.toFixed(2)}%`;

        distribution.append(segment);
    });

    container.append(distribution);

    // Detailed language breakdown.
    container.append(
        createElement("h4", "language-list-heading", "Language breakdown")
    );

    const list = createElement("div", "language-list");

    languages.forEach((language, index) => {
        const percentage = Number(language.percentage) || 0;
        const bytes = Number(language.bytes) || 0;

        const row = createElement("div", "language-row");

        const left = createElement("div", "language-row-name");

        const dot = createElement("span", "language-dot");
        dot.style.backgroundColor = getLanguageColor(
            language.language,
            index
        );

        left.append(
            dot,
            createElement("span", "", language.language)
        );

        const right = createElement("div", "language-row-stats");

        right.append(
            createElement("span", "", formatBytes(bytes)),
            createElement(
                "strong",
                "",
                `${percentage.toFixed(2)}%`
            )
        );

        row.append(left, right);
        list.append(row);
    });

    container.append(list);
}


/* 11. Repository analysis workflow
   --------------------------------------------------------- */

function setAnalyzing(isAnalyzing) {
    elements.analyzeButton.disabled = isAnalyzing;
    elements.repoInput.disabled = isAnalyzing;
    elements.clearButton.disabled = isAnalyzing;

    const buttonText = elements.analyzeButton.querySelector(".button-text");

    if (buttonText) {
        buttonText.textContent = isAnalyzing
            ? "Analyzing..."
            : "Analyze repository";
    }

    elements.repoForm.setAttribute(
        "aria-busy",
        String(isAnalyzing)
    );
}

async function analyzeRepository(repositoryUrl, requestId) {
    const response = await fetch(
        `${CONFIG.endpoints.repository}?url=${encodeURIComponent(repositoryUrl)}`,
        {
            method: "GET",
            credentials: "same-origin",
            headers: {
                Accept: "application/json",
            },
        }
    );

    const data = await readJson(response);

    if (requestId !== analysisRequestId) return;

    if (response.status === 401) {
        analysisRequestId += 1;
        showLogin("Your session has expired. Please sign in again.");
        return;
    }

    if (!response.ok) {
        showMessage(
            data.error || "Repository analysis failed.",
            "error"
        );

        return;
    }

    showRepositoryResult(data);
    const resultSection = document.getElementById("repository-result");
    if (resultSection) {
        loadRepositoryInsights(resultSection, repositoryUrl, requestId);
    }

    void loadLanguageAnalytics(repositoryUrl, requestId);
}

async function handleRepositorySubmit(event) {
    event.preventDefault();
    clearMessage();

    const result = validateGitHubRepository(elements.repoInput.value);

    if (!result.valid) {
        showMessage(result.error, "error");

        elements.repoInput.setAttribute("aria-invalid", "true");
        elements.repoInput.focus();

        return;
    }

    elements.repoInput.removeAttribute("aria-invalid");

    const requestId = ++analysisRequestId;
    removeRepositoryResult();
    setAnalyzing(true);

    try {
        await analyzeRepository(result.url, requestId);

    } catch (error) {
        console.error("Repository analysis error:", error);

        showMessage(
            "Unable to connect to the RepoLens server. Please try again.",
            "error"
        );

    } finally {
        setAnalyzing(false);
    }
}


/* 12. Search input controls
   --------------------------------------------------------- */

function updateClearButton() {
    elements.clearButton.hidden = elements.repoInput.value.length === 0;
}

function clearRepositoryInput() {
    elements.repoInput.value = "";

    elements.repoInput.removeAttribute("aria-invalid");

    clearMessage();
    updateClearButton();

    elements.repoInput.focus();
}

function initializeSearchControls() {
    elements.repoForm.addEventListener(
        "submit",
        handleRepositorySubmit
    );

    elements.repoInput.addEventListener("input", () => {
        clearMessage();

        elements.repoInput.removeAttribute("aria-invalid");

        updateClearButton();
    });

    elements.clearButton.addEventListener(
        "click",
        clearRepositoryInput
    );

    document.addEventListener("keydown", (event) => {
        if (
            event.key === "Escape" &&
            document.activeElement === elements.repoInput
        ) {
            clearRepositoryInput();
        }
    });

    updateClearButton();
}


/* 13. Authentication controls
   --------------------------------------------------------- */

function initializeAuthenticationControls() {
    elements.logoutButton.addEventListener("click", logout);
}


/* 14. Application initialization
   --------------------------------------------------------- */

async function initializeApp() {
    initializeTheme();
    initializeSearchControls();
    initializeAuthenticationControls();

    const params = new URLSearchParams(window.location.search);
    const authStatus = params.get("auth");

    let authMessage = "";

    if (authStatus === "denied") {
        authMessage = "GitHub sign-in was cancelled.";
    } else if (authStatus === "failed") {
        authMessage = "GitHub sign-in failed. Please try again.";
    }

    if (authStatus) {
        window.history.replaceState(
            {},
            document.title,
            window.location.pathname
        );
    }

    const authenticated = await checkAuthentication();

    if (!authenticated && authMessage) {
        showLogin(authMessage);
    }
}

initializeApp();
