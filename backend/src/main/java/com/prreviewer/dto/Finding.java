package com.prreviewer.dto;

public record Finding(
        String file,
        Integer line,
        String severity,
        String comment
) {
}
