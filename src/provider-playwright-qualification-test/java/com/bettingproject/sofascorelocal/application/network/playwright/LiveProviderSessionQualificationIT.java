package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.application.live.LiveProviderSession;
import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

/** Explicit Chromium qualification only. Excluded from standard Maven and the historical 14-test suite. */
class LiveProviderSessionQualificationIT {
    @Test @Timeout(210)
    void repeatedFourFamiliesShareOneContextAcrossThreeMinutesAnd404ReturnsNormally() throws Exception {
        try (Fixture f = new Fixture()) {
            ChildJvmPlaywrightProviderSupervisor supervisor = f.supervisor();
            UUID id=UUID.randomUUID();
            List<ProcessHandle> owned;
            try (var campaign=supervisor.open(id,LiveProviderSession.ENDPOINTS)) {
                Process worker=f.worker.get(); assertThat(worker).isNotNull();
                long initialMemory=memory(worker);
                long origin=System.nanoTime();
                for(int cycle=0;cycle<3;cycle++) {
                    long remaining=TimeUnit.SECONDS.toNanos(cycle*60L)-(System.nanoTime()-origin);
                    if(remaining>0) TimeUnit.NANOSECONDS.sleep(remaining);
                    for(long event : List.of(17_000_001L,17_000_002L)) {
                        for(var endpoint : List.of(com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_DETAILS,
                                com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_STATISTICS,
                                com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_INCIDENTS,
                                com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_LINEUPS)) {
                            var response=campaign.execute(new PlaywrightProviderRequest(endpoint,null,0,0,event),PlaywrightDispatchAdmission.UNRESTRICTED);
                            if(endpoint.name().equals("EVENT_INCIDENTS") && event==17_000_001L)
                                assertThat(response.httpStatus()).isEqualTo(cycle==0 ? 404 : 200);
                            else assertThat(response.httpStatus()).isEqualTo(200);
                            assertThat(f.worker.get()).isSameAs(worker);
                        }
                    }
                }
                assertThat(Duration.ofNanos(System.nanoTime()-origin)).isGreaterThanOrEqualTo(Duration.ofSeconds(120));
                long finalMemory=memory(worker);
                if(initialMemory>0) assertThat(finalMemory-initialMemory).isLessThan(256L*1024*1024);
                System.out.println("WO058_LIVE_LOOPBACK_REQUESTS="+f.arrivals.size());
                System.out.println("WO058_LIVE_WORKER_MEMORY_FIRST_BYTES="+initialMemory);
                System.out.println("WO058_LIVE_WORKER_MEMORY_LAST_BYTES="+finalMemory);
                owned=worker.descendants().toList();
            }
            assertThat(supervisor.activeCampaignId()).isEmpty();
            assertThat(f.worker.get().isAlive()).isFalse();
            assertThat(owned).noneMatch(ProcessHandle::isAlive);
            assertThat(f.arrivals).hasSize(24);
            for(int i=1;i<f.arrivals.size();i++) assertThat(f.arrivals.get(i)-f.arrivals.get(i-1)).isGreaterThanOrEqualTo(TimeUnit.SECONDS.toNanos(3));
            assertThat(f.offScope.get()).isZero();
            f.assertNoArtifacts();
        }
    }

    @Test @Timeout(45)
    void eventCancellationInsideFenceKeepsOtherEventUsableThenGlobalStopCleans() throws Exception {
        try(Fixture f=new Fixture()) {
            var supervisor=f.supervisor(); UUID id=UUID.randomUUID();
            try(var campaign=supervisor.open(id,LiveProviderSession.ENDPOINTS);
                var pool=Executors.newVirtualThreadPerTaskExecutor()) {
                campaign.execute(PlaywrightProviderRequest.eventDetails(17_000_001));
                AtomicBoolean cancelled=new AtomicBoolean(); CountDownLatch checked=new CountDownLatch(1);
                PlaywrightDispatchAdmission admission=new PlaywrightDispatchAdmission() {
                    public void check() { checked.countDown(); if(cancelled.get()) throw new PlaywrightDispatchCancelledException(); }
                    public Permit acquireDispatchPermit() { check(); return () -> {}; }
                };
                Future<?> pending=pool.submit(() -> campaign.execute(PlaywrightProviderRequest.eventStatistics(17_000_001),admission));
                assertThat(checked.await(2,TimeUnit.SECONDS)).isTrue(); cancelled.set(true);
                assertThatThrownBy(() -> pending.get(2,TimeUnit.SECONDS)).hasCauseInstanceOf(PlaywrightDispatchCancelledException.class);
                assertThat(f.arrivals).hasSize(1);
                assertThat(campaign.execute(PlaywrightProviderRequest.eventDetails(17_000_002)).httpStatus()).isEqualTo(200);
                var receipt=supervisor.stopCampaign(id,LiveProviderSession.ENDPOINTS);
                assertThat(receipt.acknowledgementLatency()).isLessThanOrEqualTo(Duration.ofMillis(500));
            }
            awaitCleanup(supervisor, f.worker.get());
            assertThat(f.arrivals).hasSize(2); f.assertNoArtifacts();
        }
    }

    @Test @Timeout(30)
    void slowDurableAdmissionDoesNotBlockStopAcknowledgementOrSendAGet() throws Exception {
        try (Fixture f = new Fixture()) {
            var supervisor = f.supervisor(); UUID id = UUID.randomUUID();
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            try (var campaign = supervisor.open(id, LiveProviderSession.ENDPOINTS);
                 var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> pending = pool.submit(() -> campaign.execute(PlaywrightProviderRequest.eventDetails(17_000_001),
                        new PlaywrightDispatchAdmission() {
                            public void check() { }
                            public Permit acquireDispatchPermit() {
                                entered.countDown();
                                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test admission timeout"); }
                                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                                return () -> {};
                            }
                        }));
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    long start = System.nanoTime();
                    var stop = supervisor.stopCampaign(id, LiveProviderSession.ENDPOINTS);
                    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThanOrEqualTo(Duration.ofMillis(500));
                    assertThat(stop.acknowledgementLatency()).isLessThanOrEqualTo(Duration.ofMillis(500));
                } finally { release.countDown(); }
                assertThatThrownBy(() -> pending.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(PlaywrightProviderException.class);
                assertThat(f.arrivals).isEmpty();
            }
            awaitCleanup(supervisor, f.worker.get());
            f.assertNoArtifacts();
        }
    }

    private static void awaitCleanup(ChildJvmPlaywrightProviderSupervisor supervisor, Process worker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((supervisor.activeCampaignId().isPresent() || worker.isAlive()) && System.nanoTime() < deadline) Thread.sleep(20);
        assertThat(supervisor.activeCampaignId()).isEmpty();
        assertThat(worker.isAlive()).isFalse();
    }

    private static long memory(Process process) throws Exception {
        if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) return 0;
        Process probe=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",
                "(Get-Process -Id "+process.pid()+").WorkingSet64").redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if(!probe.waitFor(3,TimeUnit.SECONDS)) throw new IllegalStateException("memory probe timeout");
            assertThat(probe.exitValue()).isZero();
            return Long.parseLong(new String(probe.getInputStream().readNBytes(100),StandardCharsets.UTF_8).trim());
        } finally { if(probe.isAlive()) probe.destroyForcibly(); }
    }

    private static final class Fixture implements AutoCloseable {
        final HttpServer server; final AtomicReference<Process> worker=new AtomicReference<>();
        final List<Long> arrivals=new CopyOnWriteArrayList<>(); final AtomicInteger offScope=new AtomicInteger();
        final AtomicInteger incidents=new AtomicInteger(); final Path sandbox;
        final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
        Fixture() throws Exception {
            sandbox=Files.createTempDirectory(Files.createDirectories(Path.of("target/provider-playwright-runtime/live-qualification")),"session-").toAbsolutePath();
            server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                String path=exchange.getRequestURI().getPath();
                if(!path.matches("/api/v1/event/1700000[12](/statistics|/incidents|/lineups)?") || !"GET".equals(exchange.getRequestMethod())) {
                    offScope.incrementAndGet(); exchange.sendResponseHeaders(403,-1); exchange.close(); return;
                }
                arrivals.add(System.nanoTime());
                int code=path.equals("/api/v1/event/17000001/incidents") && incidents.getAndIncrement()==0 ? 404 : 200;
                byte[] body=(code==404 ? "{\"error\":{\"code\":404}}" : "{\"localFixture\":true}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(code,body.length);
                try(var out=exchange.getResponseBody()) { out.write(body); } finally { exchange.close(); }
            }); server.start();
        }
        ChildJvmPlaywrightProviderSupervisor supervisor() {
            var p=new ProviderPlaywrightProperties(); p.setEnabled(true);
            p.setWorkerJar(Path.of(System.getProperty("provider.playwright.worker-jar")).toAbsolutePath());
            p.setLoopbackQualification(true); p.setLoopbackOrigin("http://127.0.0.1:"+server.getAddress().getPort());
            return new ChildJvmPlaywrightProviderSupervisor(p,Clock.systemUTC(),new SecureRandom(),builder -> {
                Path temp=Files.createDirectories(sandbox.resolve("temp"));
                builder.directory(sandbox.toFile());
                builder.environment().put("TEMP",temp.toString()); builder.environment().put("TMP",temp.toString());
                builder.environment().put("TMPDIR",temp.toString());
                Process process=builder.start(); worker.set(process); return process;
            });
        }
        void assertNoArtifacts() throws Exception {
            try(var files=Files.walk(sandbox)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    boolean forbidden = n.endsWith(".har") || n.endsWith(".webm") || n.endsWith(".png")
                            || n.endsWith(".mp4") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                            || n.endsWith(".trace") || n.endsWith(".download") || n.contains("cookies")
                            || n.contains("storage-state") || n.contains("storagestate");
                    if (forbidden) assertThat(exactPackagedResource(file))
                            .as("runtime artifact must only be an unchanged packaged resource: %s", sandbox.relativize(file)).isTrue();
                }
            }
        }
        private boolean exactPackagedResource(Path file) throws Exception {
            Path relative = sandbox.relativize(file);
            if (relative.getNameCount() < 4 || !relative.getName(0).toString().equals("temp")
                    || !relative.getName(1).toString().startsWith("playwright-java-")) return false;
            String resource = "driver/" + relative.subpath(2, relative.getNameCount()).toString().replace('\\', '/');
            if (!Set.of("driver/package/lib/server/chromium/appIcon.png",
                    "driver/package/lib/tools/dashboard/appIcon.png",
                    "driver/package/lib/tools/skills/playwright-cli/references/storage-state.md").contains(resource)) return false;
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                return input != null && Arrays.equals(Files.readAllBytes(file), input.readAllBytes());
            }
        }
        public void close() throws Exception {
            server.stop(0); executor.close();
            if (worker.get()!=null && worker.get().isAlive()) {
                worker.get().descendants().forEach(ProcessHandle::destroyForcibly);
                worker.get().destroyForcibly(); worker.get().waitFor(5, TimeUnit.SECONDS);
            }
            Path allowed = Path.of("target/provider-playwright-runtime/live-qualification").toAbsolutePath().normalize();
            if (!sandbox.toRealPath().startsWith(allowed.toRealPath()) || sandbox.equals(allowed))
                throw new IllegalStateException("isolated fixture cleanup escaped its root");
            for (int retry=0; retry<20; retry++) {
                try (var paths = Files.walk(sandbox)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                    return;
                } catch (java.io.IOException failure) {
                    if (retry==19) throw failure;
                    Thread.sleep(100);
                }
            }
        }
    }
}
