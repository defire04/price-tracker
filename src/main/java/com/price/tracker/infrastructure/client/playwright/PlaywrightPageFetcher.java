package com.price.tracker.infrastructure.client.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.price.tracker.config.TrackerProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Headless-browser fetch for sites that block plain HTTP (Cloudflare and similar).
 * Playwright objects are not thread-safe, so all access is synchronized.
 */
@Component
@RequiredArgsConstructor
public class PlaywrightPageFetcher {

    private static final Pattern CHROME_MAJOR_VERSION = Pattern.compile("Chrome/(\\d+)");

    private final TrackerProperties trackerProperties;

    private Playwright playwright;
    private Browser browser;

    public synchronized String fetchHtml(String url, String readyExpression) {
        ensureBrowser();
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(trackerProperties.http().userAgent())
                .setLocale("uk-UA"))) {
            context.setExtraHTTPHeaders(clientHintHeaders());
            Page page = context.newPage();
            page.navigate(url, new Page.NavigateOptions().setTimeout(trackerProperties.http().timeoutMs()));
            page.waitForFunction(readyExpression, null,
                    new Page.WaitForFunctionOptions().setTimeout(trackerProperties.http().timeoutMs()));
            return page.content();
        }
    }

    /**
     * Chromium sends "HeadlessChrome" in the sec-ch-ua client hints even when the
     * user agent is overridden; Akamai (Stradivarius) hard-denies on that, so the
     * hints are rewritten to match the configured Chrome UA.
     */
    private Map<String, String> clientHintHeaders() {
        Matcher matcher = CHROME_MAJOR_VERSION.matcher(trackerProperties.http().userAgent());
        String majorVersion = matcher.find() ? matcher.group(1) : "136";
        return Map.of(
                "sec-ch-ua", "\"Chromium\";v=\"%1$s\", \"Google Chrome\";v=\"%1$s\", \"Not.A/Brand\";v=\"99\"".formatted(majorVersion),
                "sec-ch-ua-mobile", "?0",
                "sec-ch-ua-platform", "\"Linux\"");
    }

    private void ensureBrowser() {
        if (playwright == null) {
            playwright = Playwright.create();
        }
        if (browser == null || !browser.isConnected()) {
            // Full Chromium in new headless mode: Akamai on Zara denies the lighter headless shell
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setChannel("chromium"));
        }
    }

    @PreDestroy
    public synchronized void close() {
        if (playwright != null) {
            playwright.close();
        }
    }
}
