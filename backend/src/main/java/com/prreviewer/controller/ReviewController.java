package com.prreviewer.controller;

import com.prreviewer.dto.ReviewRequest;
import com.prreviewer.dto.ReviewResponse;
import com.prreviewer.service.GitHubService;
import com.prreviewer.service.GroqService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final GitHubService gitHubService;
    private final GroqService groqService;

    public ReviewController(GitHubService gitHubService, GroqService groqService) {
        this.gitHubService = gitHubService;
        this.groqService = groqService;
    }

    @PostMapping
    public ReviewResponse review(@Valid @RequestBody ReviewRequest request) {
        GitHubService.PullRequestRef ref = gitHubService.parsePrUrl(request.prUrl());
        String diff = gitHubService.fetchDiff(ref);
        return groqService.review(request.prUrl(), diff);
    }
}
