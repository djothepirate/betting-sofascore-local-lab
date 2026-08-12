package com.bettingproject.sofascorelocal.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFormTokenServiceTest {

    private final LocalFormTokenService service = new LocalFormTokenService();

    @Test
    void issuesOneSessionBoundTokenAndConsumesItOnce() {
        MockHttpSession session = new MockHttpSession();

        String issued = service.issue(session);

        assertThat(issued).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(service.issue(session)).isEqualTo(issued);
        service.consume(session, issued);

        assertThatThrownBy(() -> service.consume(session, issued))
                .isInstanceOf(InvalidLocalFormTokenException.class)
                .hasMessage("INVALID_LOCAL_FORM_TOKEN");
        assertThat(service.issue(session)).isNotEqualTo(issued);
    }

    @Test
    void rejectsMissingWrongOversizedAndCrossSessionTokens() {
        MockHttpSession firstSession = new MockHttpSession();
        MockHttpSession secondSession = new MockHttpSession();
        String token = service.issue(firstSession);

        assertRejected(firstSession, null);
        assertRejected(firstSession, "wrong");
        assertRejected(firstSession, "x".repeat(129));
        assertRejected(secondSession, token);

        service.consume(firstSession, token);
    }

    private void assertRejected(MockHttpSession session, String token) {
        assertThatThrownBy(() -> service.consume(session, token))
                .isInstanceOf(InvalidLocalFormTokenException.class);
    }
}
