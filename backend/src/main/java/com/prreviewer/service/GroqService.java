package com.prreviewer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prreviewer.dto.Finding;
import com.prreviewer.dto.ReviewResponse;
import com.prreviewer.exception.PrReviewException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls Groq's OpenAI-compatible chat completions API (https://api.groq.com/openai/v1)
 * to generate the structured PR review.
 */
@Service
public class GroqService {

    private static final String SYSTEM_PROMPT = """
            You are an expert code reviewer performing an automated pull request review.
            You will be given a unified git diff. Analyze it for bugs, security issues,
            missing error handling, risky changes (auth, migrations, secrets, infra config),
            and code quality problems.

            Respond with ONLY a JSON object matching this exact shape, no prose outside it:
            {
              "riskLevel": "LOW" | "MEDIUM" | "HIGH",
              "summary": "2-4 sentence overview of the change and overall assessment",
              "findings": [
                {
                  "file": "path/to/file",
                  "line": <integer line number from the diff, or null if not line-specific>,
                  "severity": "LOW" | "MEDIUM" | "HIGH",
                  "comment": "specific, actionable review comment"
                }
              ]
            }

            If there are no notable issues, return an empty findings array and riskLevel "LOW".
            """;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GroqService(@Value("${groq.api-base-url}") String apiBaseUrl,
                        @Value("${groq.api-key}") String apiKey,
                        @Value("${groq.model}") String model) {
        // Forcing HTTP/1.1 avoids intermittent "handshake terminated" errors the JDK's
        // default HttpClient hits negotiating HTTP/2 against Groq's TLS termination.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public ReviewResponse review(String prUrl, String diff) {
        if (!StringUtils.hasText(apiKey)) {
            throw new PrReviewException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "GROQ_API_KEY is not configured on the server");
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "temperature", 0.2,
                "max_tokens", 1500,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", "Diff to review:\n\n" + diff)
                )
        );

        String rawResponse;
        try {
            rawResponse = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            String detail = extractErrorMessage(ex.getResponseBodyAsString());
            throw new PrReviewException(HttpStatus.BAD_GATEWAY,
                    "Groq API error: " + ex.getStatusText() + (detail != null ? " — " + detail : ""), ex);
        }

        String content = extractMessageContent(rawResponse);
        return parseReview(prUrl, content);
    }

    private String extractErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("error").path("message");
            return message.isTextual() ? message.asText() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractMessageContent(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception ex) {
            throw new PrReviewException(HttpStatus.BAD_GATEWAY, "Malformed response from Groq", ex);
        }
    }

    private ReviewResponse parseReview(String prUrl, String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            String riskLevel = node.path("riskLevel").asText("MEDIUM");
            String summary = node.path("summary").asText("");

            List<Finding> findings = new ArrayList<>();
            for (JsonNode f : node.path("findings")) {
                findings.add(new Finding(
                        f.path("file").asText(null),
                        f.path("line").isNumber() ? f.path("line").asInt() : null,
                        f.path("severity").asText("MEDIUM"),
                        f.path("comment").asText("")
                ));
            }

            return new ReviewResponse(prUrl, riskLevel.toUpperCase(), summary, findings);
        } catch (Exception ex) {
            throw new PrReviewException(HttpStatus.BAD_GATEWAY,
                    "Could not parse the AI's review output as JSON", ex);
        }
    }
}
