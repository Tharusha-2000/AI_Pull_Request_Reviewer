package com.prreviewer.service;

import com.prreviewer.exception.PrReviewException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitHubService {

    private static final Pattern PR_URL_PATTERN =
            Pattern.compile("github\\.com/(?<owner>[^/]+)/(?<repo>[^/]+)/pull/(?<number>\\d+)");

    private final RestClient restClient;
    private final String githubToken;
    private final int maxDiffChars;

    public GitHubService(@Value("${github.api-base-url}") String apiBaseUrl,
                          @Value("${github.token}") String githubToken,
                          @Value("${review.max-diff-chars}") int maxDiffChars) {
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).build();
        this.githubToken = githubToken;
        this.maxDiffChars = maxDiffChars;
    }

    public record PullRequestRef(String owner, String repo, int number) {
    }

    public PullRequestRef parsePrUrl(String prUrl) {
        Matcher matcher = PR_URL_PATTERN.matcher(prUrl);
        if (!matcher.find()) {
            throw new PrReviewException(HttpStatus.BAD_REQUEST,
                    "prUrl must look like https://github.com/{owner}/{repo}/pull/{number}");
        }
        return new PullRequestRef(matcher.group("owner"), matcher.group("repo"),
                Integer.parseInt(matcher.group("number")));
    }

    public String fetchDiff(PullRequestRef ref) {
        try {
            String diff = restClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{number}", ref.owner(), ref.repo(), ref.number())
                    .headers(headers -> {
                        headers.add("Accept", "application/vnd.github.v3.diff");
                        headers.add("X-GitHub-Api-Version", "2022-11-28");
                        if (StringUtils.hasText(githubToken)) {
                            headers.setBearerAuth(githubToken);
                        }
                    })
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(diff)) {
                throw new PrReviewException(HttpStatus.NOT_FOUND, "No diff content returned for this pull request");
            }
            return truncate(diff);
        } catch (RestClientResponseException ex) {
            throw new PrReviewException(mapStatus(ex), "GitHub API error: " + ex.getStatusText(), ex);
        }
    }

    private String truncate(String diff) {
        if (diff.length() <= maxDiffChars) {
            return diff;
        }
        return diff.substring(0, maxDiffChars) + "\n\n[diff truncated for review — showing first "
                + maxDiffChars + " characters]";
    }

    private HttpStatus mapStatus(RestClientResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        return status != null ? status : HttpStatus.BAD_GATEWAY;
    }
}
