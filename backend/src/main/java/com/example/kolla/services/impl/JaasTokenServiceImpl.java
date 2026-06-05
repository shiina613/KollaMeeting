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
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of {@link JaasTokenService} that generates JaaS JWT tokens
 * signed with RS256 using the configured RSA private key.
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

        // 4. Determine moderator flag — host or secretary
        boolean isModerator = (meeting.getHost() != null
                && currentUser.getId().equals(meeting.getHost().getId()))
                || (meeting.getSecretary() != null
                && currentUser.getId().equals(meeting.getSecretary().getId()));

        // 5. Build and sign JWT
        String token = buildJwt(meeting, currentUser, isModerator);
        String roomName = jaasProperties.getAppId() + "/" + meeting.getCode();

        return new JaasTokenResponse(token, roomName);
    }

    /**
     * Builds and signs the JaaS JWT.
     * Requirements: 1.5–1.17, 6.1
     */
    private String buildJwt(Meeting meeting, User user, boolean isModerator) {
        String appId = jaasProperties.getAppId();
        String keyId = jaasProperties.extractKeyId();
        String kid = jaasProperties.getApiKey();

        long nowMillis = System.currentTimeMillis();
        long nowSec = nowMillis / 1000;
        Date iat = new Date(nowMillis);
        Date nbf = new Date((nowSec - 10) * 1000);
        Date exp = new Date((nowSec + 3600) * 1000);

        // Build context.user map
        Map<String, Object> contextUser = new HashMap<>();
        contextUser.put("id", String.valueOf(user.getId()));
        contextUser.put("name", user.getFullName());
        contextUser.put("email", user.getEmail() != null ? user.getEmail() : "");
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

        PrivateKey privateKey = parsePrivateKey();

        return Jwts.builder()
                .setHeaderParam("typ", "JWT")
                .setHeaderParam("kid", kid)
                .setIssuer("chat")
                .setAudience("jitsi")
                .setSubject(appId)
                .claim("room", meeting.getCode())
                .setIssuedAt(iat)
                .setNotBefore(nbf)
                .setExpiration(exp)
                .claim("context", context)
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    /**
     * Parses the RSA private key from the PEM string stored in {@link JaasProperties}.
     * Replaces literal {@code \n} sequences with real newlines, strips PEM headers,
     * Base64-decodes the body, and constructs a {@link PrivateKey} via PKCS8EncodedKeySpec.
     *
     * <p>SECURITY: The private key value is NEVER logged or included in any exception message.
     * Requirements: 6.1
     */
    private PrivateKey parsePrivateKey() {
        try {
            String pem = jaasProperties.getPrivateKey();
            if (pem == null || pem.isBlank()) {
                throw new ServiceUnavailableException("JaaS private key is not configured");
            }

            // Replace literal \n with real newlines (env var encoding)
            pem = pem.replace("\\n", "\n");

            // Strip PEM headers/footers
            pem = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(pem);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);

        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (java.security.spec.InvalidKeySpecException e) {
            // SECURITY: never log the private key value
            log.error("Failed to parse JaaS private key: invalid key format");
            throw new ServiceUnavailableException("JaaS private key is invalid");
        } catch (Exception e) {
            // SECURITY: never log the private key value
            log.error("Failed to parse JaaS private key: {}", e.getClass().getSimpleName());
            throw new ServiceUnavailableException("JaaS private key could not be loaded");
        }
    }
}
