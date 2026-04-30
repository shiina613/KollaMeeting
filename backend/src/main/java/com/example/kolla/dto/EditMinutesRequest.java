package com.example.kolla.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for the Secretary edit endpoint.
 * PUT /api/v1/meetings/{id}/minutes/edit
 *
 * Requirements: 25.5
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditMinutesRequest {

    /**
     * Rich-text HTML content produced by the Secretary's editor (TipTap / Quill).
     * Will be rendered to PDF via PDFBox + jsoup.
     */
    @NotBlank(message = "contentHtml must not be blank")
    private String contentHtml;
}
