package com.bifrostconnect.api_geo.dto;

public record ProcessoDashboardResponse(
        String id,
        String dateTime,
        String dataset,
        String stage,
        String status,
        String source,
        String year,
        String epsg,
        String pauseReason,
        String assignedEditor
) {
}