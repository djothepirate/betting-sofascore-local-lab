import com.bettingproject.sofascorelocal.config.SecurityHeadersFilter;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Trigger;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3DatePolicy;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import org.springframework.mock.web.*;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Explicit Chromium check of the real dashboard fragment with synthetic model values.
 * A fixed Paris date makes leap-year and range boundaries reproducible.
 * Every request is fulfilled in memory; the context is also offline. No Lab, DB or provider.
 */
class J3DateFormCheck {
    private static final Instant NOW=Instant.parse("2027-03-01T12:00:00Z");
    private static final LocalDate TODAY=LocalDate.of(2027,3,1);
    private static final String ORIGIN="http://localhost:8087";

    public static void main(String[] args) throws Exception {
        String cache=System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        if(cache==null || !java.nio.file.Files.isDirectory(java.nio.file.Path.of(cache)))
            throw new IllegalStateException("An existing dedicated browser cache is required");
        String html=render();
        var headers=new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(new MockHttpServletRequest("GET","/"),headers,new MockFilterChain());
        Map<String,String> responseHeaders=new HashMap<>();
        headers.getHeaderNames().forEach(name->responseHeaders.put(name,headers.getHeader(name)));
        var unexpected=new AtomicInteger();
        int cases=0;
        try(Playwright playwright=Playwright.create();
            Browser browser=playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context=browser.newContext(new Browser.NewContextOptions().setLocale("fr-FR")
                    .setTimezoneId("Europe/Paris").setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
            context.setOffline(true);
            var posts=new AtomicInteger();
            context.route("**/*",route->{
                var request=route.request();
                var uri=URI.create(request.url());
                if(!ORIGIN.equals(uri.getScheme()+"://"+uri.getRawAuthority())) {
                    unexpected.incrementAndGet();route.abort();
                } else if(request.method().equals("GET") && uri.getPath().equals("/")) {
                    route.fulfill(new Route.FulfillOptions().setStatus(200).setHeaders(responseHeaders)
                            .setContentType("text/html; charset=utf-8").setBody(html));
                } else if(request.method().equals("POST") && Set.of("/j3/plans","/j3/collect","/j3/import").contains(uri.getPath())) {
                    posts.incrementAndGet();
                    // No redirect: native submit only; controller/persistence are qualified by J3WebApplicationIT.
                    route.fulfill(new Route.FulfillOptions().setStatus(200).setBody("synthetic submission accepted"));
                } else if(request.method().equals("GET") && uri.getPath().equals("/favicon.ico")) {
                    route.fulfill(new Route.FulfillOptions().setStatus(204));
                } else {
                    unexpected.incrementAndGet();route.abort();
                }
            });
            Page page=context.newPage();
            for(boolean edit:List.of(false,true)) {
                for(String[] scenario:List.of(
                        new String[]{"first-date","2000-01-01","2027-03-03T16:15","true"},
                        new String[]{"last-date-minute","2028-03-01","2028-03-01T23:59","true"},
                        new String[]{"collection-year-9999","9999-01-31","2027-03-03T16:15","false"},
                        new String[]{"collection-before-2000","1999-12-31","2027-03-03T16:15","false"},
                        new String[]{"collection-after-horizon","2028-03-02","2027-03-03T16:15","false"},
                        new String[]{"trigger-year-9999","2027-03-01","9999-09-14T15:00","false"},
                        new String[]{"trigger-after-horizon","2027-03-01","2028-03-02T00:00","false"},
                        new String[]{"valid-leap-day","2027-03-01","2028-02-29T10:00","true"},
                        new String[]{"typed-31-february","2027-03-01","2028-02-29T10:00","false"})) {
                    page.navigate(ORIGIN+"/");posts.set(0);
                    Locator form;
                    if(edit) {
                        page.locator("details summary").click();
                        form=page.locator("details form");
                    } else form=page.locator("#j3-plan-date").locator("..");
                    var date=form.locator("input[name=date]");
                    var at=form.locator("input[name=at]");
                    date.fill(scenario[1]);at.fill(scenario[2]);
                    if(scenario[0].equals("typed-31-february")) {
                        at.focus();
                        for(int i=0;i<6;i++)at.press("ArrowLeft");
                        at.pressSequentially("31");
                        if(!at.inputValue().isEmpty())throw new AssertionError("Invalid civil date was normalized");
                    }
                    boolean valid=Boolean.parseBoolean(scenario[3]);
                    assertSubmission(page,form,form.locator("button[type=submit]"),posts,valid);
                    System.out.println((edit?"edit":"create")+" "+scenario[0]+"="+(valid?"ACCEPTED":"BLOCKED"));
                    cases++;
                }
            }
            for(boolean imported:List.of(false,true)) {
                for(String date:List.of("9999-01-31","2000-01-01")) {
                    page.navigate(ORIGIN+"/");posts.set(0);
                    var form=page.locator("#j3-date").locator("..");
                    form.locator("#j3-date").fill(date);
                    // The probe tests native date validation only; the synthetic POST never imports a payload.
                    var button=imported?form.locator("button[formaction='/j3/import']"):form.locator(".real-call-button");
                    assertSubmission(page,form,button,posts,date.equals("2000-01-01"));
                    System.out.println((imported?"manual-import":"manual-provider")+" "+date+"="+(date.equals("2000-01-01")?"ACCEPTED":"BLOCKED"));
                    cases++;
                }
            }
        }
        if(unexpected.get()!=0)throw new AssertionError("Unexpected browser requests: "+unexpected.get());
        if(cases!=22)throw new AssertionError("Incomplete native date qualification");
        System.out.println("WO060_J3_DATE_FORMS=PASS;CASES=22;PROVIDER_CALLS=0;LAB_REQUESTS=0");
    }

    private static void assertSubmission(Page page,Locator form,Locator button,AtomicInteger posts,boolean valid) {
        boolean actual=(Boolean)form.evaluate("form => Array.from(form.elements).every(field => !field.validity || field.validity.valid)");
        if(actual!=valid)throw new AssertionError("Unexpected native form validity");
        if(valid)page.waitForResponse(r->r.request().method().equals("POST"),button::click);
        else button.click();
        if(posts.get()!=(valid?1:0))throw new AssertionError("Unexpected native POST count");
    }

    private static String render() {
        var resolver=new ClassLoaderTemplateResolver();resolver.setPrefix("templates/");resolver.setSuffix(".html");
        var engine=new SpringTemplateEngine();engine.setTemplateResolver(resolver);
        var request=new MockHttpServletRequest("GET","/");request.setServerName("localhost");request.setServerPort(8087);
        var exchange=JakartaServletWebApplication.buildApplication(request.getServletContext())
                .buildExchange(request,new MockHttpServletResponse());
        Map<String,Object> model=new HashMap<>();
        model.put("j3Settings",new Settings(true,Mode.STARTUP_OR_DAY_CHANGE,null,1,NOW));
        model.put("j3Date",TODAY);model.put("j3MinimumDate",J3DatePolicy.MINIMUM_COLLECTION_DATE);
        model.put("j3MaximumDate",J3DatePolicy.maximumDate(NOW));model.put("j3PlanMinimumTime",TODAY.atStartOfDay());
        model.put("j3PlanMaximumTime",J3DatePolicy.maximumDate(NOW).atTime(23,59));
        model.put("j3RuleId",UUID.randomUUID());model.put("j3OrderId",UUID.randomUUID());
        UUID rule=UUID.randomUUID();
        model.put("j3Orders",List.of(new Order(UUID.randomUUID(),"SCHEDULED|"+rule+"|1",rule,1,TODAY,Trigger.SCHEDULED,
                TODAY.plusDays(2).atTime(16,15).atZone(ZoneId.of("Europe/Paris")).toInstant(),NOW,
                OrderState.FUTURE,null,null,null,"NONE",null,null)));
        model.put("j3Dates",List.of());model.put("j3HistoryUnavailable",false);
        model.put("j3ProviderReady",true);model.put("j3CleanupPending",false);
        model.put("j3RuntimeReason","");model.put("j3ProviderReason","");
        model.put("localFormToken","synthetic-browser-probe");
        return engine.process("dashboard",Set.of("#manual-call-control"),new WebContext(exchange,Locale.FRANCE,model));
    }
}
