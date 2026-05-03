package com.example.kolla.services.impl;

import com.example.kolla.config.JaasProperties;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.exceptions.ServiceUnavailableException;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.responses.JaasTokenResponse;
import com.example.kolla.services.JaasTokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of {@link JaasTokenService} that builds and signs JaaS JWTs
 * using JJWT 0.11.5 and an RSA private key from configuration.
 * Requirements: 1.1–1.19, 2.4, 6.1, 6.2, 6.3, 6.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JaasTokenServiceImpl implements JaasTokenService {

    private final JaasProperties jaasProperties;
    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;

    @Override
    public JaasTokenResponse generateToken(Long meetingId, User currentUser) {
        // 1. Check JaaS is configured
        if (!jaasProperties.isEnabled()) {
            throw new ServiceUnavailableException("JaaS is not configured");
        }

        // 2. Find meeting
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found: " + meetingId));

        // 3. Check membership
        if (!memberRepository.existsByMeetingIdAndUserId(meetingId, currentUser.getId())) {
            throw new ForbiddenException("User is not a member of this meeting");
        }

        // 4. Determine moderator status: host or secretary
        boolean isModerator = false;
        User host = meeting.getHost();
        User secretary = meeting.getSecretary();
        if (host != null && host.getId() != null && host.getId().equals(currentUser.getId())) {
            isModerator = true;
        } else if (secretary != null && secretary.getId() != null && secretary.getId().equals(currentUser.getId())) {
            isModerator = true;
        }

        // 5. Parse RSA private key
        PrivateKey privateKey = parsePrivateKey(jaasProperties.getPrivateKey());

        // 6. Build JWT claims
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date nbf = new Date(nowMillis - 10_000L);       // now - 10 seconds
        Date exp = new Date(nowMillis + 3_600_000L);    // now + 1 hour

        String appId = jaasProperties.getAppId();
        String kid = "vpaas-magic-cookie-" + appId + "/" + jaasProperties.extractKeyId();

        // Build context.user map
        Map<String, Object> contextUser = new HashMap<>();
        contextUser.put("id", String.valueOf(currentUser.getId()));
        contextUser.put("name", currentUser.getFullName());
        contextUser.put("email", currentUser.getEmail() != null ? currentUser.getEmail() : "");
        contextUser.put("avatar", "");
        contextUser.put("moderator", isModerator);

        // Build context.features map
        Map<String, Object> contextFeatures = new HashMap<>();
        contextFeatures.put("livestreaming", false);
        contextFeatures.put("outbound-call", false);
        contextFeatures.put("sip-outbound-call", false);
        contextFeatures.put("transcription", false);

        // Build context map
        Map<String, Object> context = new HashMap<>();
        context.put("user", contextUser);
        context.put("features", contextFeatures);

        // 7. Build and sign JWT
        String token = Jwts.builder()
                .setHeaderParam("alg", "RS256")
                .setHeaderParam("kid", kid)
                .claim("iss", "chat")
                .claim("aud", "jitsi")
                .setSubject(appId)
                .claim("room", meeting.getCode())
                .setIssuedAt(now)
                .setNotBefore(nbf)
                .setExpiration(exp)
                .claim("context", context)
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();

        // 8. Return response
        return new JaasTokenResponse(token, appId + "/" + meeting.getCode());
    }

    /**
     * Parses a PEM-encoded RSA private key string into a {@link PrivateKey}.
     * <p>
     * Handles literal {@code \n} sequences (backslash + n) in the PEM string,
     * strips PEM headers/footers, and decodes the Base64 body.
     *
     * @param pemKey the PEM-encoded private key string
     * @return the parsed {@link PrivateKey}
     * @throws ServiceUnavailableException if the key cannot be parsed
     */
    private PrivateKey parsePrivateKey(String pemKey) {
        try {
            // Replace literal \n (two chars: backslash + n) with actual newline
            String normalized = pemKey.replace("\\n", "\n");

            // Strip PEM headers/footers and all whitespace
            String base64 = normalized
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            // Log a generic message — never log the private key value
            log.error("Failed to parse JaaS RSA private key: {}", e.getMessage());
            throw new ServiceUnavailableException("JaaS is not configured");
        }
    }
}
