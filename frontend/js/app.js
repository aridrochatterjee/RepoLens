
"use strict";

/* =========================================================
   RepoLens — Frontend Application
   Authentication, repository analysis and language insights
   ========================================================= */


/* 01. DOM references
   --------------------------------------------------------- */

const elements = {
    appShell: document.getElementById("app-shell"),
    themeToggle: document.getElementById("theme-toggle"),
    themeColorMeta: document.querySelector('meta[name="theme-color"]'),

    loginView: document.getElementById("login-view"),
    dashboardView: document.getElementById("dashboard-view"),
    footerView: document.getElementById("footer-view"),
    workspaceWelcome: document.getElementById("workspace-welcome"),
    workspaceContext: document.getElementById("workspace-context"),
    workspaceRepositoryContext: document.getElementById("workspace-repository-context"),
    sidebarToggle: document.getElementById("sidebar-toggle"),
    sidebar: document.getElementById("workspace-sidebar"),
    sidebarBackdrop: document.getElementById("sidebar-backdrop"),
    analysisNavigation: document.getElementById("analysis-navigation"),
    workspaceOverview: document.getElementById("workspace-overview"),
    workspacePage: document.getElementById("workspace-view"),
    repositoryDashboard: document.getElementById("repository-dashboard"),

    userProfile: document.getElementById("user-profile"),
    userAvatar: document.getElementById("user-avatar"),
    userName: document.getElementById("user-name"),
    userProfileLink: document.getElementById("user-profile-link"),
    profileMenu: document.getElementById("profile-menu"),
    profileMenuToggle: document.getElementById("profile-menu-toggle"),
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
const applicationState = {
    repository: null,
    repositoryUrl: null,
    activeWorkspace: "dashboard",
    workspaceData: new Map(),
    codebaseData: null,
    selectedFile: null,
    fileRequestId: 0,
    fileObjectUrl: null,
    pendingDirectory: null,
};


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
        contributors: "/api/repository/contributors",
        contributorProfile: "/api/repository/contributors",
        pullRequests: "/api/repository/pull-requests",
        issues: "/api/repository/issues",
        collaboration: "/api/repository/collaboration",
        codebase: "/api/repository/codebase",
        file: "/api/repository/file",
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
        if (!response.ok) return {};
        throw new Error("RepoLens returned an invalid response.");
    }
}

async function apiRequest(endpoint, options = {}) {
    const { headers = {}, ...requestOptions } = options;
    const response = await fetch(endpoint, {
        ...requestOptions,
        credentials: "same-origin",
        headers: {
            Accept: "application/json",
            ...headers,
        },
    });
    return { response, data: await readJson(response) };
}

function handleAuthenticationExpired(response) {
    if (response.status !== 401) return false;
    analysisRequestId += 1;
    showLogin("Your session has expired. Please sign in again.");
    return true;
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
            isLight ? "#f6f8fa" : "#0d1117"
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

function closeMobileNavigation(restoreFocus = true) {
    if (!elements.appShell) return;
    const wasOpen = elements.appShell.classList.contains("navigation-open");
    elements.appShell.classList.remove("navigation-open");
    document.body.classList.remove("navigation-locked");
    elements.sidebarBackdrop.hidden = true;
    elements.sidebarToggle.setAttribute("aria-expanded", "false");
    if (window.matchMedia("(max-width: 800px)").matches) {
        elements.sidebarToggle.setAttribute("aria-label", "Open workspace navigation");
    } else {
        const collapsed = elements.appShell.classList.contains("sidebar-collapsed");
        elements.sidebarToggle.setAttribute("aria-expanded", String(!collapsed));
        elements.sidebarToggle.setAttribute(
            "aria-label",
            collapsed ? "Expand workspace navigation" : "Collapse workspace navigation"
        );
    }
    if (wasOpen && restoreFocus) elements.sidebarToggle.focus();
}

function closeProfileMenu(restoreFocus = true) {
    if (!elements.profileMenu || elements.profileMenu.hidden) return;
    elements.profileMenu.hidden = true;
    elements.profileMenuToggle.setAttribute("aria-expanded", "false");
    if (restoreFocus) elements.profileMenuToggle.focus();
}

function initializeNavigationControls() {
    const isMobile = () => window.matchMedia("(max-width: 800px)").matches;

    elements.sidebarToggle.addEventListener("click", () => {
        if (isMobile()) {
            const opening = !elements.appShell.classList.contains("navigation-open");
            if (opening) {
                elements.appShell.classList.add("navigation-open");
                document.body.classList.add("navigation-locked");
                elements.sidebarBackdrop.hidden = false;
                elements.sidebarToggle.setAttribute("aria-expanded", "true");
                elements.sidebarToggle.setAttribute("aria-label", "Close workspace navigation");
                elements.sidebar.querySelector(".nav-item:not([hidden])")?.focus();
            } else {
                closeMobileNavigation();
            }
            return;
        }

        const collapsed = elements.appShell.classList.toggle("sidebar-collapsed");
        elements.sidebarToggle.setAttribute("aria-expanded", String(!collapsed));
        elements.sidebarToggle.setAttribute(
            "aria-label",
            collapsed ? "Expand workspace navigation" : "Collapse workspace navigation"
        );
    });

    elements.sidebarBackdrop.addEventListener("click", () => closeMobileNavigation());

    elements.sidebar.querySelectorAll(".nav-item").forEach((link) => {
        link.addEventListener("click", (event) => {
            const workspace = link.dataset.workspace;
            if (!workspace) return;
            event.preventDefault();
            if (isMobile()) closeMobileNavigation(false);
            navigateWorkspace(workspace);
        });
    });

    window.addEventListener("popstate", () => {
        const workspace = window.location.hash.slice(1) || "dashboard";
        navigateWorkspace(workspace, false);
    });

    elements.profileMenuToggle.addEventListener("click", () => {
        const opening = elements.profileMenu.hidden;
        elements.profileMenu.hidden = !opening;
        elements.profileMenuToggle.setAttribute("aria-expanded", String(opening));
        if (opening) {
            elements.profileMenu.querySelector("a:not([hidden]), button:not([hidden])")?.focus();
        }
    });

    document.addEventListener("click", (event) => {
        if (!elements.userProfile.contains(event.target)) closeProfileMenu(false);
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            if (elements.appShell.classList.contains("navigation-open")) {
                closeMobileNavigation();
            } else {
                closeProfileMenu();
            }
            return;
        }

        if (event.key !== "Tab" || !elements.appShell.classList.contains("navigation-open")) {
            return;
        }

        const focusable = Array.from(
            elements.sidebar.querySelectorAll("a[href], button:not(:disabled)")
        ).filter((item) => !item.closest("[hidden]"));
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    });

    window.addEventListener("resize", () => closeMobileNavigation(false));

    if (!isMobile()) {
        elements.sidebarToggle.setAttribute("aria-label", "Collapse workspace navigation");
        elements.sidebarToggle.setAttribute("aria-expanded", "true");
    }
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
    elements.workspaceContext.hidden = true;
    elements.sidebarToggle.hidden = true;
    elements.analysisNavigation.hidden = true;
    elements.workspaceWelcome.hidden = false;
    closeMobileNavigation(false);
    closeProfileMenu(false);

    showLoginMessage(messageText);
}

function showDashboard(user) {
    elements.loginView.hidden = true;
    elements.dashboardView.hidden = false;
    elements.footerView.hidden = false;
    elements.userProfile.hidden = false;
    elements.sidebarToggle.hidden = false;
    elements.analysisNavigation.hidden = !applicationState.repository;
    elements.workspaceContext.hidden =
        !applicationState.repository;
    elements.workspaceWelcome.hidden =
        Boolean(applicationState.repository);

    elements.userName.textContent = user.name || user.username || "GitHub user";
    const profileUrl = user.username
        ? safeGitHubUrl(`https://github.com/${encodeURIComponent(user.username)}`)
        : null;
    if (profileUrl) {
        elements.userProfileLink.href = profileUrl;
        elements.userProfileLink.hidden = false;
    } else {
        elements.userProfileLink.hidden = true;
    }

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
    navigateWorkspace(applicationState.activeWorkspace, false);
}

async function checkAuthentication() {
    try {
        const { response, data: payload } = await apiRequest(
            CONFIG.endpoints.currentUser,
            { method: "GET" }
        );

        if (!response.ok) {
            showLogin();
            return false;
        }

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
        const { response } = await apiRequest(
            CONFIG.endpoints.logout,
            { method: "POST" }
        );

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
    applicationState.repository = null;
    applicationState.repositoryUrl = null;
    applicationState.workspaceData.clear();
    applicationState.codebaseData = null;
    applicationState.selectedFile = null;
    applicationState.fileRequestId += 1;
    if (applicationState.fileObjectUrl) {
        URL.revokeObjectURL(applicationState.fileObjectUrl);
        applicationState.fileObjectUrl = null;
    }
    elements.repositoryDashboard.replaceChildren();
    elements.repositoryDashboard.hidden = true;
    elements.workspacePage.replaceChildren();
    elements.workspacePage.hidden = true;
    elements.workspaceOverview.hidden = false;
    elements.workspaceWelcome.hidden = false;
    elements.workspaceContext.hidden = true;
    elements.analysisNavigation.hidden = true;
    window.history.replaceState(
        { workspace: "dashboard" },
        "",
        `${window.location.pathname}#dashboard`
    );
    navigateWorkspace("dashboard", false);
}

function showRepositoryResult(repository) {
    applicationState.repository = repository;
    const resultSection = createElement("section", "repository-result");
    resultSection.id = "repository-result";
    resultSection.setAttribute("aria-labelledby", "repository-result-heading");

    const heading = createElement("div", "repository-heading");
    const headingTitle = createElement(
        "h2",
        "",
        repository.full_name || repository.name || "Repository"
    );
    headingTitle.id = "repository-result-heading";
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

    const description = createElement(
        "p",
        "repository-description",
        repository.description || "No description provided."
    );

    const metadata = createElement("div", "repository-metadata");
    metadata.append(
        createElement("span", "repository-visibility",
            typeof repository.private === "boolean"
                ? repository.private ? "Private repository" : "Public repository"
                : "Visibility unavailable"),
        createElement("span", "", repository.language || "Language unavailable")
    );

    const statsContainer = createElement("div", "repository-stats");
    [["Stars", repository.stargazers_count], ["Forks", repository.forks_count]]
        .forEach(([label, value]) => {
            const stat = createElement("span", "repository-stat");
            stat.append(
                createElement("strong", "", value == null ? "—" : formatNumber(value)),
                createElement("span", "", label)
            );
            statsContainer.append(stat);
        });
    const summary = createElement("div", "overview-summary-grid");
    [["Repository health", "Loading…", "health"], ["Commits · 30 days", "Loading…", "commits"], ["Open items", repository.open_issues_count == null ? "—" : formatNumber(repository.open_issues_count), "open-items"]]
        .forEach(([label, value, key]) => {
            const metric = createElement("article", "overview-metric");
            metric.dataset.metric = key;
            metric.append(createElement("span", "", label), createElement("strong", "", value));
            if (key === "health") {
                const link = createElement("a", "text-link", "View breakdown");
                link.href = "#insights";
                link.addEventListener("click", (event) => {
                    event.preventDefault();
                    navigateWorkspace("insights");
                });
                metric.append(link);
            }
            summary.append(metric);
        });
    const visuals = createElement("div", "overview-visual-grid");
    const activityPanel = createInsightPanel(
        "overview-activity",
        "Recent activity",
        "Weekly commits · last 30 days"
    );
    const languagePanel = createInsightPanel(
        "overview-language",
        "Language distribution",
        "GitHub-reported language bytes"
    );
    visuals.append(activityPanel.panel, languagePanel.panel);
    const recentPanel = createInsightPanel(
        "overview-recent",
        "Recent commits",
        "Latest changes on the default branch"
    );
    const explore = createElement("nav", "overview-explore-links");
    explore.setAttribute("aria-label", "Explore more repository workspaces");
    [
        ["Repository insights", "insights"],
        ["Code explorer", "code-explorer"],
        ["Contributors", "contributors"],
        ["Pull requests", "pull-requests"],
        ["Issues", "issues"],
    ].forEach(([label, workspace]) => {
        const link = createElement("a", "text-link", label);
        link.href = `#${workspace}`;
        link.addEventListener("click", (event) => {
            event.preventDefault();
            navigateWorkspace(workspace);
        });
        explore.append(link);
    });
    resultSection.append(
        heading,
        description,
        metadata,
        statsContainer,
        summary,
        visuals,
        recentPanel.panel,
        explore
    );
    elements.repositoryDashboard.replaceChildren(resultSection);
    elements.repositoryDashboard.hidden = false;
    elements.workspaceWelcome.hidden = true;
    elements.workspaceContext.hidden = false;
    elements.workspaceRepositoryContext.textContent =
        repository.full_name || repository.name || "Repository workspace";
    elements.analysisNavigation.hidden = false;
    applicationState.workspaceData.clear();
    void loadOverview(applicationState.repositoryUrl, analysisRequestId, {
        activity: activityPanel.body,
        language: languagePanel.body,
        commits: recentPanel.body,
    });
}

const WORKSPACE_COPY = {
    insights: ["Repository insights", "Health, activity and commit history from GitHub."],
    "code-explorer": ["Code explorer", "Browse tracked files and open source in RepoLens."],
    "code-composition": ["Code composition", "Explore languages, file types and tracked code size."],
    "directory-analysis": ["Directory analysis", "Compare directory footprint and review structural observations."],
    contributors: ["Contributors", "Explore the returned contributor leaderboard and public profiles."],
    "pull-requests": ["Pull requests", "Review PR lifecycle, activity, merge and review signals."],
    issues: ["Issues", "Review issue activity, labels and resolution signals."],
    collaboration: ["Collaboration", "Understand contribution distribution and evidence-based observations."],
    settings: ["Settings", "Adjust the appearance and application preferences."],
    "code-viewer": ["Source viewer", "Read repository source without leaving RepoLens."],
};

function navigateWorkspace(workspace, pushHistory = true) {
    const knownWorkspace = workspace === "dashboard"
        || Object.hasOwn(WORKSPACE_COPY, workspace);
    if (!knownWorkspace) workspace = "dashboard";
    if (!applicationState.repository
            && workspace !== "dashboard" && workspace !== "settings") {
        workspace = "dashboard";
    }
    if (workspace === "code-viewer" && !applicationState.selectedFile) {
        workspace = "code-explorer";
    }

    applicationState.activeWorkspace = workspace;
    elements.workspaceOverview.hidden = workspace !== "dashboard";
    elements.workspacePage.hidden = workspace === "dashboard";
    elements.sidebar.querySelectorAll("[data-workspace]").forEach((link) => {
        const selected = workspace === "code-viewer"
            ? link.dataset.workspace === "code-explorer"
            : link.dataset.workspace === workspace;
        link.classList.toggle("active", selected);
        link.setAttribute("aria-current", selected ? "page" : "false");
    });

    if (pushHistory && window.location.hash !== `#${workspace}`) {
        window.history.pushState({ workspace }, "", `#${workspace}`);
    }
    if (workspace === "dashboard") return;
    renderWorkspace(workspace);
}

function renderWorkspace(workspace) {
    elements.workspacePage.replaceChildren();
    const [title, description] = WORKSPACE_COPY[workspace];
    const heading = createElement("header", "workspace-page-heading");
    const headingText = createElement("div", "");
    const breadcrumb = createElement("p", "workspace-breadcrumb", `Workspace / ${title}`);
    const titleElement = createElement("h1", "", title);
    titleElement.tabIndex = -1;
    headingText.append(breadcrumb, titleElement, createElement("p", "workspace-page-description", description));
    heading.append(headingText);
    elements.workspacePage.append(heading);
    elements.workspacePage.hidden = false;
    window.setTimeout(() => titleElement.focus({ preventScroll: true }), 0);

    if (workspace === "settings") {
        renderSettingsWorkspace();
    } else if (workspace === "code-viewer") {
        renderSelectedFile();
    } else {
        loadWorkspaceContent(workspace);
    }
}

function renderSettingsWorkspace() {
    const section = createElement("section", "settings-section");
    section.append(
        createElement("h2", "", "Appearance"),
        createElement("p", "", "Choose the color theme used across your RepoLens workspace.")
    );
    const themeButton = createElement(
        "button",
        "button-secondary",
        document.documentElement.dataset.theme === "dark"
            ? "Switch to light theme"
            : "Switch to dark theme"
    );
    themeButton.type = "button";
    themeButton.addEventListener("click", () => {
        const nextTheme = document.documentElement.dataset.theme === "dark"
            ? "light"
            : "dark";
        applyTheme(nextTheme);
        themeButton.textContent = nextTheme === "dark"
            ? "Switch to light theme"
            : "Switch to dark theme";
    });
    section.append(themeButton);
    elements.workspacePage.append(section);
}

function createWorkspacePanel(className, title, description) {
    const panel = createInsightPanel(className, title, description);
    panel.panel.classList.add("workspace-panel");
    elements.workspacePage.append(panel.panel);
    return panel.body;
}

async function loadWorkspaceData(
    cacheKey,
    endpoint,
    body,
    renderer,
    parameters = {}
) {
    const requestId = analysisRequestId;
    if (applicationState.workspaceData.has(cacheKey)) {
        renderer(body, applicationState.workspaceData.get(cacheKey));
        return;
    }

    body.setAttribute("aria-busy", "true");
    body.replaceChildren(createElement("p", "insight-loading", "Loading repository data…"));
    try {
        const data = await requestInsight(
            endpoint,
            applicationState.repositoryUrl,
            requestId,
            parameters
        );
        if (!data || requestId !== analysisRequestId) return;
        applicationState.workspaceData.set(cacheKey, data);
        renderer(body, data);
    } catch (error) {
        if (requestId !== analysisRequestId) return;
        console.error(`Unable to load ${cacheKey}:`, error);
        setInsightMessage(
            body,
            error.message || "Unable to load this workspace.",
            "error"
        );
    }
}

function loadCodebaseWorkspace(body, mode) {
    const render = (target, data) => renderCodebase(target, data, mode);
    if (applicationState.codebaseData) {
        render(body, applicationState.codebaseData);
        return;
    }
    const requestId = analysisRequestId;
    body.setAttribute("aria-busy", "true");
    body.replaceChildren(createElement("p", "insight-loading", "Loading repository tree…"));
    void requestInsight(
        CONFIG.endpoints.codebase,
        applicationState.repositoryUrl,
        requestId,
        { limit: "30" }
    ).then((data) => {
        if (!data || requestId !== analysisRequestId) return;
        applicationState.codebaseData = data;
        render(body, data);
    }).catch((error) => {
        if (requestId !== analysisRequestId) return;
        console.error("Unable to load codebase analysis:", error);
        setInsightMessage(
            body,
            error.message || "Unable to load repository files.",
            "error"
        );
    });
}

function loadWorkspaceContent(workspace) {
    if (workspace === "insights") {
        const health = createWorkspacePanel(
            "workspace-health-panel",
            "Repository health",
            "An explainable RepoLens estimate, with the evidence behind it."
        );
        const activity = createWorkspacePanel(
            "workspace-activity-panel",
            "Commit activity",
            "Weekly commits across the last 30 days."
        );
        const commits = createWorkspacePanel(
            "workspace-commits-panel",
            "Commit history",
            "Recent changes on the default branch."
        );
        void loadWorkspaceData("health", CONFIG.endpoints.health, health, renderHealth);
        void loadWorkspaceData("activity", CONFIG.endpoints.activity, activity, renderActivity);
        void loadCommitPage(commits, applicationState.repositoryUrl, 1, analysisRequestId);
        return;
    }
    if (workspace === "code-explorer"
            || workspace === "code-composition"
            || workspace === "directory-analysis") {
        const title = workspace === "code-explorer"
            ? "Repository files"
            : workspace === "code-composition"
                ? "Tracked code composition"
                : "Directory footprint";
        const body = createWorkspacePanel(
            `workspace-${workspace}-panel`,
            title,
            workspace === "code-explorer"
                ? "Choose a directory, filter its entries, and open source files here."
                : workspace === "code-composition"
                    ? "Composition is inferred from tracked file names and GitHub language statistics."
                    : "Directory sizes use reported Git blob bytes; nested directory shares overlap."
        );
        if (workspace === "code-composition") {
            const languageBody = createWorkspacePanel(
                "workspace-language-panel",
                "Language distribution",
                "GitHub-reported language bytes."
            );
            loadCodebaseWorkspace(body, "code-composition");
            void loadWorkspaceData(
                "languages",
                CONFIG.endpoints.languages,
                languageBody,
                renderLanguageAnalytics
            );
        } else {
            loadCodebaseWorkspace(
                body,
                workspace === "code-explorer" ? "code-explorer" : "directory-analysis"
            );
        }
        return;
    }

    if (workspace === "contributors") {
        const leaderboard = createWorkspacePanel(
            "workspace-contributors-panel",
            "Contributor leaderboard",
            "Ranks reflect GitHub's returned contribution counts, not individual effort."
        );
        const profiles = createWorkspacePanel(
            "workspace-contributor-profile-panel",
            "Contributor activity",
            "Select a contributor to inspect their public repository activity."
        );
        setInsightMessage(
            profiles,
            "Select a contributor from the leaderboard to load their public activity."
        );
        void loadWorkspaceData(
            "contributors",
            CONFIG.endpoints.contributors,
            leaderboard,
            (target, data) => renderDeveloperContributors(
                target,
                data,
                applicationState.repositoryUrl,
                analysisRequestId,
                profiles
            )
        );
        return;
    }

    if (workspace === "pull-requests" || workspace === "issues") {
        const isPullRequest = workspace === "pull-requests";
        const body = createWorkspacePanel(
            `workspace-${workspace}-panel`,
            isPullRequest ? "Pull request activity" : "Issue activity",
            "Counts and trends are limited to the GitHub records returned for this repository."
        );
        const endpoint = isPullRequest
            ? CONFIG.endpoints.pullRequests
            : CONFIG.endpoints.issues;
        const renderer = isPullRequest
            ? renderPullRequestAnalytics
            : renderIssueAnalytics;
        void loadWorkspaceData(
            workspace,
            endpoint,
            body,
            (target, data) => renderer(
                target,
                data,
                applicationState.repositoryUrl,
                analysisRequestId
            ),
            { period: "30", page: "1", limit: "100" }
        );
        return;
    }

    if (workspace === "collaboration") {
        const body = createWorkspacePanel(
            "workspace-collaboration-panel",
            "Collaboration signals",
            "Evidence from the returned contributor sample; no productivity or quality scores."
        );
        void loadWorkspaceData(
            "collaboration",
            CONFIG.endpoints.collaboration,
            body,
            renderCollaboration
        );
    }
}

function renderCollaboration(container, data) {
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");
    if (!data.available) {
        setInsightMessage(
            container,
            data.limitation || "Collaboration data is unavailable."
        );
        return;
    }

    const metrics = createElement("div", "developer-metrics");
    [
        [formatNumber(data.contributorsReturned), "contributors returned"],
        [formatNumber(data.totalContributionsReturned), "contributions in sample"],
        [data.topContributorShare == null ? "—" : `${data.topContributorShare}%`, "top contributor share"],
        [data.botContributors == null ? "—" : formatNumber(data.botContributors), "bot accounts"],
    ].forEach(([value, label]) => {
        const metric = createElement("div", "developer-metric");
        metric.append(createElement("strong", "", value), createElement("span", "", label));
        metrics.append(metric);
    });
    container.append(metrics);

    const observations = Array.isArray(data.observations) ? data.observations : [];
    if (observations.length === 0) {
        container.append(createElement("p", "insight-message", "No collaboration observations were returned."));
    } else {
        const list = createElement("ul", "collaboration-observations");
        observations.forEach((observation) => {
            const item = createElement("li", "collaboration-observation");
            item.append(
                createElement("strong", "", observation.title),
                createElement("span", "", observation.evidence)
            );
            list.append(item);
        });
        container.append(list);
    }
    container.append(createElement(
        "p",
        "developer-note",
        `Shares describe the returned subset, not effort or productivity. ${data.limitation || ""}`
    ));
}

async function loadOverviewResource(endpoint, repositoryUrl, requestId, parameters = {}) {
    return requestInsight(endpoint, repositoryUrl, requestId, parameters);
}

async function loadOverview(repositoryUrl, requestId, panels) {
    const healthMetric = elements.repositoryDashboard.querySelector('[data-metric="health"] strong');
    const commitMetric = elements.repositoryDashboard.querySelector('[data-metric="commits"] strong');
    const resources = [
        loadOverviewResource(CONFIG.endpoints.health, repositoryUrl, requestId)
            .then((data) => {
                if (!data || requestId !== analysisRequestId) return;
                const score = data.score;
                if (healthMetric) {
                    healthMetric.textContent = score == null ? "—" : `${score}/100`;
                }
            })
            .catch((error) => showOverviewMetricError(healthMetric, error, requestId)),
        loadOverviewResource(CONFIG.endpoints.activity, repositoryUrl, requestId)
            .then((data) => {
                if (!data || requestId !== analysisRequestId) return;
                if (commitMetric) {
                    commitMetric.textContent = data.totalCommits == null
                        ? "—"
                        : formatNumber(data.totalCommits);
                }
                renderActivity(panels.activity, data);
            })
            .catch((error) => showOverviewError(panels.activity, error, requestId)),
        loadOverviewResource(CONFIG.endpoints.languages, repositoryUrl, requestId)
            .then((data) => {
                if (!data || requestId !== analysisRequestId) return;
                renderOverviewLanguages(panels.language, data);
            })
            .catch((error) => showOverviewError(panels.language, error, requestId)),
        loadOverviewResource(
            CONFIG.endpoints.commits,
            repositoryUrl,
            requestId,
            { page: "1", limit: "5" }
        )
            .then((data) => {
                if (!data || requestId !== analysisRequestId) return;
                renderRecentCommits(panels.commits, data);
            })
            .catch((error) => showOverviewError(panels.commits, error, requestId)),
    ];
    await Promise.all(resources);
}

function showOverviewError(container, error, requestId) {
    if (requestId !== analysisRequestId) return;
    console.error("Overview data request failed:", error);
    setInsightMessage(
        container,
        error.message || "Unable to load this overview data.",
        "error"
    );
}

function showOverviewMetricError(metric, error, requestId) {
    if (requestId !== analysisRequestId) return;
    console.error("Overview metric request failed:", error);
    if (metric) metric.textContent = "Unavailable";
}

function renderOverviewLanguages(container, data) {
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");
    const languages = Array.isArray(data.languages)
        ? data.languages.filter((item) => item && Number(item.bytes) > 0)
        : [];
    if (languages.length === 0) {
        setInsightMessage(container, "No language statistics are available.");
        return;
    }
    const distribution = createElement("div", "language-distribution");
    distribution.setAttribute("role", "img");
    distribution.setAttribute(
        "aria-label",
        languages.map((item) => `${item.language}: ${item.percentage}%`).join(", ")
    );
    languages.forEach((item, index) => {
        const segment = createElement("span", "language-segment");
        segment.style.width = `${Math.max(0, Math.min(100, Number(item.percentage) || 0))}%`;
        segment.style.backgroundColor = getLanguageColor(item.language, index);
        segment.title = `${item.language}: ${Number(item.percentage).toFixed(1)}%`;
        distribution.append(segment);
    });
    const list = createElement("ul", "overview-language-list");
    languages.slice(0, 5).forEach((item, index) => {
        const row = createElement("li", "");
        const swatch = createElement("span", "language-dot");
        swatch.style.backgroundColor = getLanguageColor(item.language, index);
        row.append(
            swatch,
            createElement("span", "", item.language),
            createElement("strong", "", `${Number(item.percentage).toFixed(1)}%`)
        );
        list.append(row);
    });
    container.append(distribution, list);
}

function renderRecentCommits(container, data) {
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");
    const commits = Array.isArray(data.commits) ? data.commits : [];
    if (commits.length === 0) {
        setInsightMessage(container, "No recent commits were returned.");
        return;
    }
    const list = createElement("ol", "recent-commit-list");
    commits.slice(0, 5).forEach((commit) => {
        const item = createElement("li", "");
        const body = createElement("div", "");
        body.append(
            createElement("strong", "", commit.message || "Commit message unavailable"),
            createElement("span", "", `${commit.author || "Unknown author"} · ${formatTimestamp(commit.date)}`)
        );
        const url = safeGitHubUrl(commit.htmlUrl);
        if (url) {
            const link = createElement("a", "commit-sha-link", commit.shortSha || "Commit");
            link.href = url;
            link.target = "_blank";
            link.rel = "noopener noreferrer";
            item.append(body, link);
        } else {
            item.append(body, createElement("span", "commit-sha", commit.shortSha || ""));
        }
        list.append(item);
    });
    container.append(list);
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
    panel.id = className.split(/\s+/)[0];
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
    const contributionTotal = data.totalContributionsReturned ??
        contributors.reduce(
            (total, contributor) => total + (Number(contributor.contributions) || 0),
            0
        );
    const contributorCount = data.contributorsReturned ?? data.totalReturned ?? contributors.length;
    const topShare = data.topContributorShare ??
        contributors[0]?.contributionShare ?? null;
    const topFiveShare = data.topFiveContributorShare ??
        (contributors.length
            ? contributors.slice(0, 5).reduce(
                (total, contributor) => total + (Number(contributor.contributionShare) || 0),
                0
            )
            : null);
    [
        [formatNumber(contributionTotal), "returned contributions"],
        [formatNumber(contributorCount), "contributors returned"],
        [topShare == null ? "—" : `${Number(topShare).toFixed(1)}%`, "top contributor share"],
        [topFiveShare == null ? "—" : `${Number(topFiveShare).toFixed(1)}%`, "top five share"],
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

function renderCodebase(container, data, mode = "all") {
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
    summary.dataset.workspaceSection = "all";
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
    breakdowns.id = "codebase-composition";
    breakdowns.dataset.workspaceSection = "code-composition";
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
    directoryPanel.id = "codebase-directories";
    directoryPanel.dataset.workspaceSection = "directory-analysis";
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
                applicationState.pendingDirectory =
                    directory.path === "(root)" ? "" : directory.path;
                navigateWorkspace("code-explorer");
            });
            directoryChart.append(row);
        });
    if (directoryChart.childElementCount === 0) {
        directoryChart.append(createElement("p", "insight-message", "No directory byte totals are available."));
    }
    directoryPanel.append(directoryChart);
    container.append(directoryPanel);

    const explorer = createElement("section", "codebase-subsection");
    explorer.id = "codebase-explorer";
    explorer.dataset.workspaceSection = "code-explorer";
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
    if (applicationState.pendingDirectory !== null) {
        select.value = applicationState.pendingDirectory;
        applicationState.pendingDirectory = null;
    }
    const filterLabel = createElement("label", "codebase-select-label", "Filter current folder");
    const filterInput = createElement("input", "codebase-file-filter");
    filterInput.type = "search";
    filterInput.placeholder = "Search files and folders";
    filterInput.setAttribute("aria-label", "Filter files and folders in the selected directory");
    filterLabel.append(filterInput);
    const contents = createElement("div", "codebase-file-list");
    const renderContents = () => {
        contents.replaceChildren();
        const directory = select.value;
        const filter = filterInput.value.trim().toLocaleLowerCase();
        const children = entries.filter((entry) => {
            let isChild;
            if (directory === "") {
                isChild = !entry.path.includes("/") ||
                    (entry.type === "directory" && !entry.path.includes("/"));
            } else {
                const prefix = `${directory}/`;
                if (!entry.path.startsWith(prefix)) return false;
                const remainder = entry.path.slice(prefix.length);
                isChild = remainder.length > 0 && !remainder.includes("/");
            }
            return isChild && (!filter || entry.name.toLocaleLowerCase().includes(filter)
                || entry.path.toLocaleLowerCase().includes(filter));
        });
        children.sort((a, b) =>
            (a.type === b.type ? 0 : a.type === "directory" ? -1 : 1) ||
            String(a.name).localeCompare(String(b.name))
        );

        if (children.length === 0) {
            contents.append(createElement(
                "p",
                "insight-message",
                filter
                    ? "No entries match this filter."
                    : "No child entries are available in this folder."
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
                    const link = createElement("button", "codebase-file-link", entry.name);
                    link.type = "button";
                    link.addEventListener("click", () => openFileViewer(entry));
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
    filterInput.addEventListener("input", renderContents);
    renderContents();
    explorer.append(selectLabel, filterLabel, contents);
    container.append(explorer);

    const largestSection = createElement("section", "codebase-subsection");
    largestSection.id = "codebase-largest-files";
    largestSection.dataset.workspaceSection = "directory-analysis";
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
            const link = createElement("button", "largest-file-link", file.path || file.name);
            link.type = "button";
            link.addEventListener("click", () => openFileViewer(file));
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
    hotspotSection.dataset.workspaceSection = "directory-analysis";
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
    if (mode !== "all") {
        Array.from(container.children).forEach((child) => {
            const section = child.dataset.workspaceSection;
            if (section && section !== "all" && section !== mode) child.remove();
        });
    }
}

function openFileViewer(file) {
    applicationState.selectedFile = {
        path: file.path,
        name: file.name || file.path.split("/").at(-1),
        htmlUrl: file.htmlUrl,
        sizeBytes: file.sizeBytes,
    };
    navigateWorkspace("code-viewer");
}

function renderSelectedFile() {
    const file = applicationState.selectedFile;
    if (!file) return;

    const navigation = createElement("div", "file-viewer-navigation");
    const back = createElement("button", "button-secondary", "Back to explorer");
    back.type = "button";
    back.addEventListener("click", () => navigateWorkspace("code-explorer"));
    const breadcrumb = createElement("nav", "file-breadcrumb", "");
    breadcrumb.setAttribute("aria-label", "File path");
    const crumbParts = file.path.split("/");
    crumbParts.forEach((part, index) => {
        if (index > 0) breadcrumb.append(createElement("span", "breadcrumb-separator", "/"));
        breadcrumb.append(createElement(
            index === crumbParts.length - 1 ? "strong" : "span",
            "",
            part
        ));
    });
    navigation.append(back, breadcrumb);

    const header = createElement("div", "file-viewer-header");
    const details = createElement("div", "file-viewer-details");
    details.append(
        createElement("h2", "", file.name),
        createElement("span", "file-viewer-meta", "Loading file metadata…")
    );
    const actions = createElement("div", "file-viewer-actions");
    const openOnGitHubUrl = safeGitHubUrl(file.htmlUrl);
    if (openOnGitHubUrl) {
        const external = createElement("a", "button-secondary", "Open on GitHub");
        external.href = openOnGitHubUrl;
        external.target = "_blank";
        external.rel = "noopener noreferrer";
        actions.append(external);
    }
    header.append(details, actions);

    const content = createElement("div", "file-viewer-content");
    content.setAttribute("aria-busy", "true");
    content.setAttribute("aria-live", "polite");
    content.append(createElement("p", "insight-loading", "Loading source file…"));
    elements.workspacePage.append(navigation, header, content);
    void loadSelectedFile(file, details, actions, content);
}

async function loadSelectedFile(file, details, actions, content) {
    const requestId = ++applicationState.fileRequestId;
    content.setAttribute("aria-busy", "true");
    content.replaceChildren(createElement("p", "insight-loading", "Loading source file…"));
    details.querySelector(".file-viewer-meta").textContent = "Loading file metadata…";
    const query = new URLSearchParams({
        url: applicationState.repositoryUrl,
        path: file.path,
    });
    const reference = applicationState.repository?.defaultBranch;
    if (reference) query.set("ref", reference);

    try {
        const { response, data } = await apiRequest(
            `${CONFIG.endpoints.file}?${query}`,
            { method: "GET" }
        );
        if (requestId !== applicationState.fileRequestId
                || applicationState.activeWorkspace !== "code-viewer") return;
        if (handleAuthenticationExpired(response)) return;
        if (!response.ok) {
            throw new Error(data.error || "Unable to load this file.");
        }
        renderFileContent(data, details, actions, content);
    } catch (error) {
        if (requestId !== applicationState.fileRequestId) return;
        console.error("File viewer request failed:", error);
        content.replaceChildren();
        content.setAttribute("aria-busy", "false");
        const message = createElement(
            "p",
            "insight-message error",
            error.message || "Unable to load this file."
        );
        const retry = createElement("button", "button-secondary", "Retry");
        retry.type = "button";
        retry.addEventListener("click", () => loadSelectedFile(file, details, actions, content));
        content.append(message, retry);
        details.querySelector(".file-viewer-meta").textContent = "File unavailable";
    }
}

function renderFileContent(file, details, actions, container) {
    details.querySelector(".file-viewer-meta").textContent =
        `${file.language || "Plain text"} · ${formatBytes(file.sizeBytes)} · ${file.encoding || "Unknown encoding"}`;
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");
    if (file.binary) {
        container.append(createElement(
            "p",
            "insight-message",
            "This file contains binary data and is not displayed as text."
        ));
        return;
    }

    const content = typeof file.content === "string" ? file.content : "";
    const copyStatus = createElement("span", "file-action-status");
    copyStatus.setAttribute("role", "status");
    const copy = createElement("button", "button-secondary", "Copy");
    copy.type = "button";
    copy.addEventListener("click", async () => {
        try {
            await navigator.clipboard.writeText(content);
            copyStatus.textContent = "Copied to clipboard.";
        } catch (error) {
            console.error("Unable to copy source file:", error);
            copyStatus.textContent = "Clipboard access is unavailable.";
        }
    });
    const download = createElement("a", "button-secondary", "Download");
    if (applicationState.fileObjectUrl) {
        URL.revokeObjectURL(applicationState.fileObjectUrl);
    }
    const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
    applicationState.fileObjectUrl = URL.createObjectURL(blob);
    download.href = applicationState.fileObjectUrl;
    download.download = file.name;
    actions.prepend(copy, download, copyStatus);

    if (content.length === 0) {
        container.append(createElement("p", "insight-message", "This file is empty."));
    } else if (!supportsSyntaxHighlighting(file.language)) {
        container.append(createElement(
            "p",
            "code-viewer-note",
            "No syntax highlighter is available for this file type. The text is displayed safely as plain text."
        ));
    } else if (content.length > 300000) {
        container.append(createElement(
            "p",
            "code-viewer-note",
            "Syntax highlighting is disabled for this large file to keep the viewer responsive. The complete text remains available."
        ));
    }

    const lines = content.split("\n");
    const codePanel = createElement("div", "code-panel");
    const lineNumbers = createElement("pre", "code-line-numbers");
    lineNumbers.setAttribute("aria-hidden", "true");
    lineNumbers.textContent = lines.map((_, index) => index + 1).join("\n");
    const source = createElement("pre", "code-source");
    const code = createElement("code", "language-source");
    if (supportsSyntaxHighlighting(file.language) && content.length <= 300000) {
        appendHighlightedSource(code, content, file.language);
    } else {
        code.textContent = content;
    }
    source.append(code);
    codePanel.append(lineNumbers, source);
    container.append(codePanel);
}

function appendHighlightedSource(code, source, language) {
    const keywordSets = {
        JavaScript: "async await break case catch class const continue debugger default delete do else export extends finally for from function if import in instanceof let new of return static super switch this throw try typeof var void while yield",
        TypeScript: "abstract any as async await break case catch class const continue declare default delete do else enum export extends finally for from function if implements import in infer instanceof interface is keyof let module namespace never new number object of private protected public readonly return static string super switch this throw type typeof undefined unique unknown var void while yield",
        Java: "abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while",
        Python: "and as assert async await break class continue def del elif else except False finally for from global if import in is lambda None nonlocal not or pass raise return True try while with yield",
        SQL: "alter and as begin by case commit create delete drop else end from group having in insert into join on order select set table union update values where",
        Shell: "case do done elif else esac fi for function if in select then until while",
        CSS: "important inherit initial unset var",
    };
    if (!keywordSets[language]) {
        code.textContent = source;
        return;
    }
    const keywords = keywordSets[language]
        .split(/\s+/)
        .map((word) => word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
        .join("|");
    const tokens = [];
    if (["JavaScript", "TypeScript", "Java", "C", "C++", "C#", "Go", "Rust", "CSS"].includes(language)) {
        tokens.push("\\/\\*[^]*?\\*\\/", "\\/\\/[^\\n]*");
    }
    if (["Python", "Shell", "Ruby", "R", "Perl", "YAML"].includes(language)) {
        tokens.push("#[^\\n]*");
    }
    tokens.push(
        "\"(?:\\\\.|[^\"\\\\])*\"",
        "'(?:\\\\.|[^'\\\\])*'",
        "`(?:\\\\.|[^`\\\\])*`",
        `\\b(?:${keywords})\\b`,
        "\\b\\d+(?:\\.\\d+)?\\b"
    );
    const pattern = new RegExp(tokens.join("|"), "g");
    let offset = 0;
    for (const match of source.matchAll(pattern)) {
        const index = match.index;
        if (index > offset) code.append(document.createTextNode(source.slice(offset, index)));
        const token = match[0];
        const className = token.startsWith("//")
                || token.startsWith("/*") || token.startsWith("#")
            ? "syntax-comment"
            : token.startsWith("\"") || token.startsWith("'") || token.startsWith("`")
                ? "syntax-string"
                : /^\d/.test(token)
                    ? "syntax-number"
                    : "syntax-keyword";
        code.append(createElement("span", className, token));
        offset = index + token.length;
    }
    if (offset < source.length) code.append(document.createTextNode(source.slice(offset)));
}

function supportsSyntaxHighlighting(language) {
    return ["JavaScript", "TypeScript", "Java", "Python", "SQL", "Shell", "CSS"]
        .includes(language);
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
    const { response, data } = await apiRequest(
        `${endpoint}?${query}`,
        { method: "GET" }
    );
    if (requestId !== analysisRequestId) return null;

    if (handleAuthenticationExpired(response)) return null;
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

/* Language analytics rendering
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
    if (isAnalyzing && !applicationState.repository) {
        elements.workspaceWelcome.hidden = true;
    } else if (!isAnalyzing && !applicationState.repository) {
        elements.workspaceWelcome.hidden = false;
    }

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
    const { response, data } = await apiRequest(
        `${CONFIG.endpoints.repository}?url=${encodeURIComponent(repositoryUrl)}`,
        { method: "GET" }
    );

    if (requestId !== analysisRequestId) return;

    if (handleAuthenticationExpired(response)) return;

    if (!response.ok) {
        showMessage(
            data.error || "Repository analysis failed.",
            "error"
        );

        return;
    }

    applicationState.repositoryUrl = repositoryUrl;
    applicationState.workspaceData.clear();
    applicationState.codebaseData = null;
    showRepositoryResult(data);
    navigateWorkspace("dashboard", false);
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
    initializeNavigationControls();
    document.querySelectorAll("[data-github-login]").forEach((link) => {
        link.addEventListener("click", () => {
            link.setAttribute("aria-busy", "true");
            showLoginMessage("Connecting to GitHub...");
        });
    });
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

    if (authenticated) {
        const requestedWorkspace = window.location.hash.slice(1) || "dashboard";
        navigateWorkspace(requestedWorkspace, false);
    }

    if (!authenticated && authMessage) {
        showLogin(authMessage);
    }
}

initializeApp();
