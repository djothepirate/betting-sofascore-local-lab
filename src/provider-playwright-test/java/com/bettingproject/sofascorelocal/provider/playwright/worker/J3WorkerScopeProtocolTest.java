package com.bettingproject.sofascorelocal.provider.playwright.worker;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class J3WorkerScopeProtocolTest {
    static final Instant NOW=Instant.parse("2026-09-13T12:00:00Z");
    @Test void startCapabilityIsExplicitAndHistoricalStartDoesNotGrantIt() throws Exception {
        assertThat(ProviderPlaywrightWorkerProtocol.requireStart(input(new byte[]{ProviderPlaywrightWorkerProtocol.START}))).isFalse();
        assertThat(ProviderPlaywrightWorkerProtocol.requireStart(input(new byte[]{ProviderPlaywrightWorkerProtocol.START_WITH_J3_PAUSE}))).isTrue();
    }
    @Test void scopeKeepsExactIdentityDateAndDeadline() throws Exception {
        UUID id=UUID.randomUUID();Instant deadline=NOW.plusSeconds(1199);
        var scope=ProviderPlaywrightWorkerProtocol.readJ3Scope(scope(id.toString(),"2026-09-14",deadline),NOW);
        assertThat(scope.runId()).isEqualTo(id);assertThat(scope.date()).isEqualTo(LocalDate.parse("2026-09-14"));
        assertThat(scope.deadline()).isEqualTo(deadline);
    }
    @Test void expiredOrExtendedScopesAndNonCanonicalIdentifiersAreRejected() {
        for(var deadline:new Instant[]{NOW,NOW.minusSeconds(1),NOW.plusSeconds(1201)})
            assertThatThrownBy(()->ProviderPlaywrightWorkerProtocol.readJ3Scope(scope(UUID.randomUUID().toString(),"2026-09-14",deadline),NOW))
                    .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class);
        assertThatThrownBy(()->ProviderPlaywrightWorkerProtocol.readJ3Scope(scope("1-1-1-1-1","2026-09-14",NOW.plusSeconds(10)),NOW))
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class);
        assertThatThrownBy(()->ProviderPlaywrightWorkerProtocol.readJ3Scope(scope(UUID.randomUUID().toString(),"2026-02-30",NOW.plusSeconds(10)),NOW))
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class);
    }
    static DataInputStream scope(String id,String date,Instant deadline)throws IOException {
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){out.writeUTF(id);out.writeUTF(date);out.writeLong(deadline.toEpochMilli());}
        return input(bytes.toByteArray());
    }
    static DataInputStream input(byte[] bytes){return new DataInputStream(new ByteArrayInputStream(bytes));}
}
