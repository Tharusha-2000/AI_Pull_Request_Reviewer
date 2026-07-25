package com.prreviewer.dto;

import java.util.List;

public record ReviewResponse(
        String prUrl,
        String riskLevel,
        String summary,
        List<Finding> findings
) {
}
