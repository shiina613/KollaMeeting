package com.example.kolla.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinutesContentEntryResponse {
    private String speakerName;
    private String roleLabel;
    private String timeLabel;
    private String text;
}
