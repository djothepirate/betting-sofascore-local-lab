package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.application.live.LiveProviderSession;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Explicit native worker qualification against a synthetic loopback server only. */
class J3LivePauseWorkerQualificationIT {
    @Test @Timeout(90)
    void oneWorkerIsolatesJ3ThenRestoresLiveAuthorityAndItsOriginalContext() throws Exception {
        var observations=new CopyOnWriteArrayList<String>();
        var cookies=new CopyOnWriteArrayList<String>();
        var phases=new ArrayList<String>();
        var report=new LinkedHashMap<String,Object>();
        Instant started=Instant.now();
        try(var f=new LiveProviderSessionQualificationIT.Fixture()) {
            String j3Path="/api/v1/sport/football/scheduled-tournaments/2026-09-13/page/";
            f.server.createContext(j3Path,exchange->{
                observations.add(exchange.getRequestURI().getPath());
                cookies.add(Optional.ofNullable(exchange.getRequestHeaders().getFirst("Cookie")).orElse(""));
                f.arrivals.add(System.nanoTime());
                byte[] body=("{\"scheduled\":[],\"hasNextPage\":"+exchange.getRequestURI().getPath().endsWith("/1")+"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.getResponseHeaders().set("Set-Cookie","synthetic_j3=discard; Path=/");
                exchange.sendResponseHeaders(200,body.length);
                try(var out=exchange.getResponseBody()) {out.write(body);}finally {exchange.close();}
            });
            f.server.createContext("/api/v1/event/17000001",exchange->{
                observations.add(exchange.getRequestURI().getPath());
                cookies.add(Optional.ofNullable(exchange.getRequestHeaders().getFirst("Cookie")).orElse(""));
                f.arrivals.add(System.nanoTime());
                byte[] body="{\"localFixture\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.getResponseHeaders().set("Set-Cookie","synthetic_live=discard; Path=/");
                exchange.sendResponseHeaders(200,body.length);
                try(var out=exchange.getResponseBody()) {out.write(body);}finally {exchange.close();}
            });
            var supervisor=f.supervisor();UUID liveId=UUID.randomUUID(),j3Id=UUID.randomUUID();
            Process worker;List<ProcessHandle> descendants;
            try(var live=supervisor.openLiveGroupedV11(liveId,LiveProviderSession.ENDPOINTS)) {
                worker=f.worker.get();assertThat(worker).isNotNull();phases.add("LIVE_READY");
                var initial=new LiveProviderDispatchGroup(liveId,UUID.randomUUID(),17000001,LiveProviderDispatchGroup.Phase.CHECK);
                assertThat(live.executeGrouped(PlaywrightProviderRequest.eventDetails(17000001),initial,
                        PlaywrightDispatchAdmission.UNRESTRICTED).httpStatus()).isEqualTo(200);
                long pauseStart=System.nanoTime();
                var scope=live.openJ3SubOperation(new J3ProviderSubOperation(j3Id,LocalDate.parse("2026-09-13"),Instant.now().plusSeconds(120)));
                phases.add("J3_ACTIVE");
                assertThat(supervisor.activeCampaignId()).contains(liveId);
                assertThatThrownBy(()->live.executeGrouped(PlaywrightProviderRequest.eventDetails(17000001),initial,
                        PlaywrightDispatchAdmission.UNRESTRICTED)).isInstanceOf(RuntimeException.class);
                assertThatThrownBy(()->scope.execute(PlaywrightProviderRequest.scheduledEvents(LocalDate.parse("2026-09-14"),1)))
                        .isInstanceOf(RuntimeException.class);
                assertThatThrownBy(()->scope.execute(PlaywrightProviderRequest.eventDetails(17000001)))
                        .isInstanceOf(RuntimeException.class);
                for(int page=1;page<=2;page++)
                    assertThat(scope.execute(PlaywrightProviderRequest.scheduledEvents(LocalDate.parse("2026-09-13"),page)).httpStatus()).isEqualTo(200);
                assertThatThrownBy(()->scope.execute(PlaywrightProviderRequest.scheduledEvents(LocalDate.parse("2026-09-13"),2)))
                        .isInstanceOf(RuntimeException.class);
                scope.close();phases.add("J3_CLOSED_LIVE_CONTEXT_VERIFIED");
                assertThatThrownBy(()->scope.execute(PlaywrightProviderRequest.scheduledEvents(LocalDate.parse("2026-09-13"),3)))
                        .isInstanceOf(RuntimeException.class);
                assertThatThrownBy(()->live.executeGrouped(PlaywrightProviderRequest.eventDetails(17000001),initial,
                        PlaywrightDispatchAdmission.UNRESTRICTED)).isInstanceOf(RuntimeException.class);
                var next=new LiveProviderDispatchGroup(liveId,UUID.randomUUID(),17000001,LiveProviderDispatchGroup.Phase.CHECK);
                assertThat(live.executeGrouped(PlaywrightProviderRequest.eventDetails(17000001),next,
                        PlaywrightDispatchAdmission.UNRESTRICTED).httpStatus()).isEqualTo(200);
                report.put("pauseThroughResumedJ4Millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-pauseStart));
                phases.add("LIVE_J4_RESUMED");
                assertThat(f.worker.get()).isSameAs(worker);
                descendants=worker.descendants().toList();
            }
            assertThat(supervisor.activeCampaignId()).isEmpty();
            assertThat(worker.waitFor(5,TimeUnit.SECONDS)).isTrue();
            assertThat(descendants).noneMatch(ProcessHandle::isAlive);
            phases.add("FULL_TREE_CLOSED");
            assertThat(observations).containsExactly("/api/v1/event/17000001",j3Path+"1",j3Path+"2","/api/v1/event/17000001");
            assertThat(cookies).allSatisfy(cookie->assertThat(cookie).isEmpty());
            assertThat(f.arrivals).hasSize(4);
            var intervals=new ArrayList<Long>();
            for(int i=1;i<f.arrivals.size();i++) {
                long delay=f.arrivals.get(i)-f.arrivals.get(i-1);
                assertThat(delay).isGreaterThanOrEqualTo(TimeUnit.SECONDS.toNanos(3));
                intervals.add(TimeUnit.NANOSECONDS.toMillis(delay));
            }
            assertThat(f.offScope).hasValue(0);f.assertNoArtifacts();
            report.put("sharedStartIntervalsMillis",intervals);
            report.put("workerCount",1);report.put("requestCount",observations.size());
        }
        report.put("status","PASS");report.put("policyVersion","live-v11");report.put("workerProtocolVersion",10);
        report.put("scope","EXPERIMENTAL LOCAL_ONLY NOT_PRODUCTION_APPROVED NO_CRITICAL_DEPENDENCY");
        report.put("startedAt",started.toString());report.put("finishedAt",Instant.now().toString());
        report.put("phases",phases);report.put("realProviderCalls",0);report.put("operatorDatabaseUsed",false);
        report.put("sessionDataTransferred",false);report.put("liveBrowserRecreated",false);
        report.put("qualificationLimit","Native context and authority transition; admission/capacity and SQL are independently tested.");
        Path workerJar=Path.of(System.getProperty("provider.playwright.worker-jar"));
        report.put("workerJarSha256",Sha256.hex(Files.readAllBytes(workerJar)));
        Path output=Path.of(".tmp/wo060-j3-live-worker-qualification.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new tools.jackson.databind.json.JsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n",StandardCharsets.UTF_8);
        System.out.println("WO060_NATIVE_J3_LIVE_QUALIFICATION=PASS;REAL_PROVIDER_CALLS=0;WORKERS=1;REQUESTS=4");
    }
}
