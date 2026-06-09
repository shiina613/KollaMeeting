package com.example.kolla.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
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

    @Valid
    @NotEmpty(message = "contentEntries must not be empty")
    private List<MinutesContentEntryRequest> contentEntries;

    private String conclusion;
}
