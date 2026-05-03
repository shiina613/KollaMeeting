package com.example.kolla.responses;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Response DTO for JaaS JWT token generation.
 * Requirements: 1.18
 */
@Data
@AllArgsConstructor
public class JaasTokenResponse {

    /** The signed JaaS JWT token. */
    private String token;

    /** Room name in format {@code {appId}/{meetingCode}}. */
    private String roomName;
}
