package com.bettingproject.sofascorelocal.adapter.web;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

/** Production J4 renderer and local SVGs, with every browser request fulfilled locally. */
class EventDetailsBrowserQualificationIT {
    @Test @Timeout(60)
    void officialsAndCountriesRefreshSafelyWithoutReplacingAdjacentExpandedCards() throws Exception {
        String cache=System.getProperty("provider.playwright.browser-cache", "");
        assertThat(cache).isNotBlank();
        assertThat(Path.of(cache).toRealPath()).isEqualTo(Path.of(System.getenv("PLAYWRIGHT_BROWSERS_PATH")).toRealPath());
        AtomicInteger external=new AtomicInteger(), flags=new AtomicInteger();
        List<String> errors=new ArrayList<>();
        try(Playwright pw=Playwright.create();
            Browser browser=pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context=browser.newContext(new Browser.NewContextOptions()
                    .setServiceWorkers(ServiceWorkerPolicy.BLOCK).setViewportSize(390,844))) {
            context.route("**/*", route -> {
                String url=route.request().url();
                if(url.equals("http://127.0.0.1/")) route.fulfill(new Route.FulfillOptions()
                        .setContentType("text/html").setBody("<!doctype html><html lang=fr><body><section data-event-details></section><details open><summary id=neighbor>Statistiques du joueur</summary><p>Valeur conservée</p></details></body></html>"));
                else if(url.equals("http://127.0.0.1/images/flags/4x3/fr.svg")) {
                    flags.incrementAndGet();
                    try {route.fulfill(new Route.FulfillOptions().setContentType("image/svg+xml")
                            .setBodyBytes(Files.readAllBytes(Path.of("src/main/resources/static/images/flags/4x3/fr.svg"))));}
                    catch(Exception e){throw new IllegalStateException(e);}
                } else if(url.equals("http://127.0.0.1/images/flags/4x3/gb-eng.svg")) {
                    route.abort();
                } else {external.incrementAndGet();route.abort();}
            });
            Page page=context.newPage();page.onPageError(errors::add);page.navigate("http://127.0.0.1/");
            page.addStyleTag(new Page.AddStyleTagOptions().setContent(
                    Files.readString(Path.of("src/main/resources/static/css/app.css"))));
            page.evaluate(Files.readString(Path.of("src/main/resources/static/js/event-details.js")));
            page.locator("#neighbor").focus();
            Map<String,Object> view=new LinkedHashMap<>();
            view.put("homeTeam","Domicile");view.put("awayTeam","Extérieur");view.put("round","Finale");
            view.put("homeManager",Map.of("name","<img src=x onerror=alert(1)>","country",Map.of("label","France","flagPath","/images/flags/4x3/fr.svg")));
            view.put("awayManager",Map.of("name","Coach B"));view.put("referee",Map.of("name","Arbitre A"));
            page.locator("[data-event-details]").evaluate("(host, view) => window.EventDetailsView.update(host, view)",view);
            page.waitForFunction("document.querySelector('[data-event-person-country]')?.dataset.countryFlagState === 'ready'");
            assertThat(page.locator("[data-event-person=homeManager] [data-event-person-name]").textContent()).isEqualTo("<img src=x onerror=alert(1)>");
            assertThat(page.locator("[data-event-person=homeManager] img").count()).isEqualTo(1);
            assertThat(page.locator("[data-event-person=homeManager] [data-event-person-nationality]")
                    .evaluate("node => getComputedStyle(node).position")).isEqualTo("absolute");
            assertThat(page.locator("[data-event-information=round]").textContent()).isEqualTo("Finale");
            view.put("homeManager",Map.of("name","Nouveau coach","country",Map.of("label","Angleterre","flagPath","/images/flags/4x3/gb-eng.svg")));
            view.put("referee",Map.of("name","—"));
            page.locator("[data-event-details]").evaluate("(host, view) => window.EventDetailsView.update(host, view)",view);
            page.waitForFunction("document.querySelector('[data-event-person-country]')?.dataset.countryFlagState === 'fallback'");
            assertThat(page.locator("[data-event-person=homeManager] img").count()).isEqualTo(1);
            assertThat(page.locator("[data-event-person=homeManager] [data-event-person-country]").textContent()).isEqualTo("Pays : Angleterre");
            assertThat(page.locator("[data-event-person=homeManager] [data-event-person-nationality]")
                    .evaluate("node => getComputedStyle(node).position")).isEqualTo("static");
            assertThat(page.locator("details").getAttribute("open")).isNotNull();
            assertThat(page.evaluate("document.activeElement.id")).isEqualTo("neighbor");
            assertThat(errors).isEmpty();assertThat(external).hasValue(0);assertThat(flags.get()).isPositive();
        }
    }
}
