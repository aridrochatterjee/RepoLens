
package com.repolens;

import com.repolens.auth.GitHubOAuthService;
import com.repolens.auth.OAuthStateManager;
import com.repolens.auth.SessionManager;
import com.repolens.client.GitHubApiClient;
import com.repolens.controller.OAuthController;
import com.repolens.controller.RepositoryController;
import com.repolens.server.WebServer;
import com.repolens.service.RepositoryService;

public class Main {

    public static void main(String[] args) {

        GitHubOAuthService oauthService =
                new GitHubOAuthService();

        SessionManager sessionManager =
                new SessionManager();

        OAuthStateManager stateManager =
                new OAuthStateManager();

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

        RepositoryController repositoryController =
                new RepositoryController(repositoryService);

        WebServer server =
                new WebServer(
                        8080,
                        oauthController,
                        repositoryController,
                        sessionManager
                );

        server.start();
    }
}
