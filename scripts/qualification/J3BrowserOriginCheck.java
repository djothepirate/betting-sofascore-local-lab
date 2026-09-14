import com.bettingproject.sofascorelocal.adapter.web.J3AutomationController;
import com.bettingproject.sofascorelocal.config.LiveCampaignLocalRequestBoundaryInterceptor;
import com.bettingproject.sofascorelocal.config.SecurityHeadersFilter;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.Mode;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import jakarta.servlet.http.HttpSession;
import org.springframework.mock.web.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Explicit native Chromium qualification; never part of standard tests.
 * All requests are fulfilled in memory, including localhost:8087: nothing reaches the running Lab.
 * Uses the production response filter and J3 request boundary with synthetic forms.
 * MVC/PostgreSQL tests separately qualify the real templates, tokens, controllers and storage.
 * Requires the test classpath, Playwright test jars and an already installed browser cache.
 */
class J3BrowserOriginCheck {
    public static void main(String[] args) throws Exception {
        String cache=System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        if(cache==null || !java.nio.file.Files.isDirectory(java.nio.file.Path.of(cache)))
            throw new IllegalStateException("An existing dedicated browser cache is required");
        var boundary=new LiveCampaignLocalRequestBoundaryInterceptor();
        var handler=new HandlerMethod(new J3AutomationController(null,null,null),
                J3AutomationController.class.getMethod("settings",String.class,long.class,boolean.class,
                        Mode.class,LocalTime.class,HttpSession.class,RedirectAttributes.class));
        var escapedRequests=new AtomicInteger();
        int cases=0;
        try(Playwright playwright=Playwright.create();
            Browser browser=playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            for(String host:List.of("localhost:8087","127.0.0.1:8087")) {
                String origin="http://"+host;
                for(String pagePath:List.of("/","/dashboard")) {
                    for(String action:List.of("/j3/settings","/j3/plans","/j3/collect","/j3/import")) {
                        for(boolean oldPolicy:List.of(true,false)) {
                            var headers=new MockHttpServletResponse();
                            new SecurityHeadersFilter().doFilter(new MockHttpServletRequest("GET",pagePath),
                                    headers,new MockFilterChain());
                            Map<String,String> pageHeaders=new LinkedHashMap<>();
                            headers.getHeaderNames().forEach(name->pageHeaders.put(name,headers.getHeader(name)));
                            if(oldPolicy) pageHeaders.put("Referrer-Policy","no-referrer");
                            String policy=pageHeaders.get("Referrer-Policy");
                            List<String> postedOrigins=new ArrayList<>();
                            List<Integer> postStatuses=new ArrayList<>();
                            try(BrowserContext context=browser.newContext(new Browser.NewContextOptions()
                                    .setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
                                context.setOffline(true);
                                context.route("**/*",route->{
                                    try {
                                        var request=route.request();
                                        URI uri=URI.create(request.url());
                                        if(!origin.equals(uri.getScheme()+"://"+uri.getRawAuthority())) {
                                            escapedRequests.incrementAndGet();
                                            route.abort();
                                            return;
                                        }
                                        if(request.method().equals("POST") && uri.getPath().equals(action)) {
                                            var local=new MockHttpServletRequest("POST",action);
                                            request.allHeaders().forEach((name,value)->local.addHeader(name,value));
                                            // Host is derived from the browser's exact URL, never from fixture input.
                                            if(local.getHeader("Host")==null) local.addHeader("Host",uri.getRawAuthority());
                                            var response=new MockHttpServletResponse();
                                            boolean accepted=boundary.preHandle(local,response,handler);
                                            // No redirect: the probe qualifies the boundary, not the controller.
                                            // Redirects may bypass route interception; the context is also offline.
                                            int status=accepted?200:response.getStatus();
                                            postedOrigins.add(local.getHeader("Origin"));
                                            postStatuses.add(status);
                                            route.fulfill(new Route.FulfillOptions().setStatus(status).setBody(""));
                                        } else if(request.method().equals("GET") && uri.getPath().equals(pagePath)) {
                                            route.fulfill(new Route.FulfillOptions().setStatus(200).setHeaders(pageHeaders)
                                                    .setContentType("text/html; charset=utf-8").setBody(
                                                            "<!doctype html><html><body><form method='post' action='"+action+"'"
                                                            +(action.equals("/j3/collect") || action.equals("/j3/import")
                                                                    ? " enctype='multipart/form-data'" : "")+">"
                                                            +"<input name='mode' value='STARTUP_OR_DAY_CHANGE'>"
                                                            +"<input name='time' type='time'><button type='submit'>Enregistrer</button>"
                                                            +"</form></body></html>"));
                                        } else if(request.method().equals("GET") && uri.getPath().equals("/favicon.ico")) {
                                            route.fulfill(new Route.FulfillOptions().setStatus(204));
                                        } else {
                                            escapedRequests.incrementAndGet();
                                            route.abort();
                                        }
                                    } catch(Exception failure) {
                                        throw new RuntimeException(failure);
                                    }
                                });
                                Page page=context.newPage();
                                page.navigate(origin+pagePath);
                                Response post=page.waitForResponse(r->r.request().method().equals("POST"),
                                        ()->page.locator("button[type=submit]").click());
                                int expectedStatus=oldPolicy?403:200;
                                String expectedOrigin=oldPolicy?"null":origin;
                                if(post.status()!=expectedStatus || !postedOrigins.equals(List.of(expectedOrigin))
                                        || !postStatuses.equals(List.of(expectedStatus)))
                                    throw new AssertionError("Browser origin qualification failed for "+host+pagePath+" -> "+action);
                                System.out.println("host="+host+" page="+pagePath+" action="+action
                                        +" policy="+policy+" origin="+postedOrigins.getFirst()+" status="+post.status());
                                cases++;
                            }
                        }
                    }
                }
            }
        }
        if(escapedRequests.get()!=0) throw new AssertionError("Unexpected browser requests: "+escapedRequests.get());
        System.out.println("WO060_J3_BROWSER_ORIGIN=PASS;CASES="+cases+";PROVIDER_CALLS=0;LAB_REQUESTS=0");
    }
}
