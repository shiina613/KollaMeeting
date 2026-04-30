package com.example.kolla.dto;

import com.example.kolla.enums.MeetingMode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /meetings/{id}/mode.
 * Requirements: 21.1–21.8
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SwitchModeRequest {

    @NotNull(message = "mode is required")
    private MeetingMode mode;
}
