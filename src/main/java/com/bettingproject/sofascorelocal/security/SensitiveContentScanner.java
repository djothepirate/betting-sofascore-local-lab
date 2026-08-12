package com.bettingproject.sofascorelocal.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SensitiveContentScanner {

    private static final List<Rule> RULES = List.of(
            new Rule(
                    "AUTHORIZATION_HEADER",
                    Pattern.compile(
                            "(?im)^\\s*authorization\\s*:\\s*(?:bearer|basic)\\s+\\S+")),
            new Rule(
                    "COOKIE_HEADER",
                    Pattern.compile("(?im)^\\s*(?:set-cookie|cookie)\\s*:\\s*\\S+")),
            new Rule(
                    "CREDENTIAL_FIELD",
                    Pattern.compile(
                            "(?i)[\"'](?:access[_-]?token|refresh[_-]?token|api[_-]?key|"
                                    + "password|session[_-]?id|cookie|secret)[\"']"
                                    + "\\s*:\\s*[\"'][^\"'\\r\\n]+[\"']")),
            new Rule(
                    "JWT_LIKE_TOKEN",
                    Pattern.compile(
                            "\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"
                                    + "\\.[A-Za-z0-9_-]{10,}\\b")),
            new Rule(
                    "PRIVATE_KEY_BLOCK",
                    Pattern.compile("-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----")));

    private SensitiveContentScanner() {
    }

    public static List<String> findings(byte[] content) {
        Objects.requireNonNull(content, "content");
        String text = new String(content, StandardCharsets.UTF_8);
        return RULES.stream()
                .filter(rule -> rule.pattern().matcher(text).find())
                .map(Rule::code)
                .toList();
    }

    private record Rule(String code, Pattern pattern) {
    }
}
