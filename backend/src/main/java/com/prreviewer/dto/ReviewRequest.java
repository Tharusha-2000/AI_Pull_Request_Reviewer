package com.prreviewer.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(
        @NotBlank(message = "prUrl is required")
        String prUrl
) {
}
