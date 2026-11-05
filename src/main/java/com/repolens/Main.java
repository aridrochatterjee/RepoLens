
package com.repolens;

import com.repolens.auth.ExpirationCleanupService;
import com.repolens.auth.GitHubOAuthService;
import com.repolens.auth.OAuthStateManager;
import com.repolens.auth.SessionManager;
import com.repolens.client.GitHubApiClient;
import com.repolens.controller.OAuthController;
import com.repolens.controller.RepositoryController;
import com.repolens.server.WebServer;
import com.repolens.service.RepositoryService;
import com.repolens.service.CodebaseAnalysisService;
import com.repolens.service.DeveloperIntelligenceService;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Main {

    public static void main(String[] args) {

        GitHubOAuthService oauthService =
                new GitHubOAuthService();

        SessionManager sessionManager =
                new SessionManager();

        OAuthStateManager stateManager =
                new OAuthStateManager();

        ExpirationCleanupService expirationCleanupService =
                new ExpirationCleanupService(stateManager, sessionManager);

        OAuthController oauthController =
                new OAuthController(
                        oauthService,
                        sessionManager,
                        stateManager,
                        false
                );

        GitHubApiClient githubApiClient =
                new GitHubApiClient();

        RepositoryService repositoryService =
                new RepositoryService(githubApiClient);

        CodebaseAnalysisService codebaseAnalysisService =
                new CodebaseAnalysisService(
                        githubApiClient,
                        repositoryService
                );

        DeveloperIntelligenceService developerIntelligenceService =
                new DeveloperIntelligenceService(
                        githubApiClient,
                        repositoryService
                );

        RepositoryController repositoryController =
                new RepositoryController(
                        repositoryService,
                        codebaseAnalysisService,
                        developerIntelligenceService
                );

        WebServer server =
                new WebServer(
                        8080,
                        InetAddress.getLoopbackAddress(),
                        oauthController,
                        repositoryController,
                        sessionManager,
                        expirationCleanupService
                );

        Runtime.getRuntime().addShutdownHook(
                new Thread(server::stop, "repolens-shutdown")
        );
        server.start();
    }
}
