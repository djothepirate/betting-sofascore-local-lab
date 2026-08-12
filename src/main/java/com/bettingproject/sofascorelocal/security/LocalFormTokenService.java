package com.bettingproject.sofascorelocal.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

@Component
public class LocalFormTokenService {

    private static final String SESSION_ATTRIBUTE =
            LocalFormTokenService.class.getName() + ".TOKEN";

    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(HttpSession session) {
        Objects.requireNonNull(session, "session");
        synchronized (session) {
            Object existing = session.getAttribute(SESSION_ATTRIBUTE);
            if (existing instanceof String token && !token.isBlank()) {
                return token;
            }

            byte[] entropy = new byte[32];
            secureRandom.nextBytes(entropy);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
            session.setAttribute(SESSION_ATTRIBUTE, token);
            return token;
        }
    }

    public void consume(HttpSession session, String submittedToken) {
        Objects.requireNonNull(session, "session");
        synchronized (session) {
            Object stored = session.getAttribute(SESSION_ATTRIBUTE);
            if (!(stored instanceof String expected)
                    || submittedToken == null
                    || submittedToken.length() > 128
                    || !MessageDigest.isEqual(
                            expected.getBytes(StandardCharsets.US_ASCII),
                            submittedToken.getBytes(StandardCharsets.US_ASCII))) {
                throw new InvalidLocalFormTokenException();
            }
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }
}
