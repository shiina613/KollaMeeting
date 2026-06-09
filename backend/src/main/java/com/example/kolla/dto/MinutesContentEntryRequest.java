package com.example.kolla.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MinutesContentEntryRequest {
    private String speakerName;
    private String roleLabel;
    private String timeLabel;

    @NotBlank(message = "text must not be blank")
    private String text;
}
