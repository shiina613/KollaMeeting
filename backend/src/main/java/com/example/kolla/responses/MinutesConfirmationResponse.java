package com.example.kolla.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned when the Host confirms and digitally signs meeting minutes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinutesConfirmationResponse {

    private MinutesResponse minutes;
    private String signedPdfFileName;
    private String signedPdfContentType;
    private String signedPdfBase64;
    private String signedPdfSha256;
}
