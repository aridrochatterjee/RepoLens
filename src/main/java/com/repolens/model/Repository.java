
package com.repolens.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Repository(

        long id,

        String name,

        @JsonProperty("full_name")
        String fullName,

        @JsonProperty("html_url")
        String htmlUrl,

        String description,

        @JsonProperty("stargazers_count")
        int stars,

        @JsonProperty("forks_count")
        int forks,

        @JsonProperty("watchers_count")
        int watchers,

        @JsonProperty("open_issues_count")
        int openIssues,

        @JsonProperty("default_branch")
        String defaultBranch,

        String language,

        @JsonProperty("created_at")
        String createdAt,

        @JsonProperty("private")
        boolean isPrivate,

        boolean fork

) {}
