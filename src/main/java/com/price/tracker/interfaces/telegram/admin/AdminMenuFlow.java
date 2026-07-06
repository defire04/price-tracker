package com.price.tracker.interfaces.telegram.admin;

import com.price.tracker.config.TrackerProperties;
import com.price.tracker.domain.enums.Site;
import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.domain.service.check.PriceCheckService;
import com.price.tracker.domain.service.parser.SiteParserRegistry;
import com.price.tracker.interfaces.telegram.i18n.ChatLocales;
import com.price.tracker.interfaces.telegram.view.TelegramReplySender;
import com.price.tracker.interfaces.telegram.view.TrackerMessages;
import com.price.tracker.util.PriceFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminMenuFlow {

    private static final int TEST_TITLE_LENGTH = 40;
    private static final int TEST_ERROR_LENGTH = 120;

    private final AdminAccess adminAccess;
    private final SiteParserRegistry parserRegistry;
    private final PriceCheckService priceCheckService;
    private final TelegramReplySender replySender;
    private final TrackerMessages messages;
    private final ChatLocales chatLocales;
    private final TrackerProperties trackerProperties;
    private final AtomicBoolean testRunning = new AtomicBoolean();
    private final AtomicBoolean checkRunning = new AtomicBoolean();

    public void showMenu(long chatId, int messageId) {
        if (!adminAccess.isAdmin(chatId)) {
            return;
        }
        Locale locale = chatLocales.localeOf(chatId);
        replySender.editMessage(chatId, messageId, messages.adminMenuText(locale), messages.adminMenuKeyboard(locale));
    }

    public void runParserTest(long chatId, int messageId) {
        if (!adminAccess.isAdmin(chatId)) {
            return;
        }
        Locale locale = chatLocales.localeOf(chatId);
        Map<Site, String> testUrls = testUrlsInSiteOrder();
        if (!testRunning.compareAndSet(false, true)) {
            replySender.editMessage(chatId, messageId, messages.adminBusy(locale), messages.adminMenuKeyboard(locale));
            return;
        }
        replySender.editMessage(chatId, messageId,
                messages.adminTestRunning(locale, testUrls.size()), messages.adminMenuKeyboard(locale));
        Thread.ofVirtual().name("admin-parser-test").start(() -> {
            try {
                String report = testUrls.entrySet().stream()
                        .map(entry -> testSite(entry.getKey(), entry.getValue()))
                        .collect(Collectors.joining("\n"));
                replySender.editMessage(chatId, messageId,
                        messages.adminTestReport(locale, report), messages.adminMenuKeyboard(locale));
            } finally {
                testRunning.set(false);
            }
        });
    }

    public void runCheckNow(long chatId, int messageId) {
        if (!adminAccess.isAdmin(chatId)) {
            return;
        }
        Locale locale = chatLocales.localeOf(chatId);
        if (!checkRunning.compareAndSet(false, true)) {
            replySender.editMessage(chatId, messageId, messages.adminBusy(locale), messages.adminMenuKeyboard(locale));
            return;
        }
        replySender.editMessage(chatId, messageId, messages.adminCheckRunning(locale), messages.adminMenuKeyboard(locale));
        Thread.ofVirtual().name("admin-check-now").start(() -> {
            try {
                priceCheckService.runCheckCycle();
                replySender.editMessage(chatId, messageId, messages.adminCheckDone(locale), messages.adminMenuKeyboard(locale));
            } catch (Exception e) {
                log.error("Manual check cycle failed", e);
                replySender.editMessage(chatId, messageId,
                        messages.adminTestReport(locale, "❌ " + truncate(e.toString(), TEST_ERROR_LENGTH)),
                        messages.adminMenuKeyboard(locale));
            } finally {
                checkRunning.set(false);
            }
        });
    }

    private Map<Site, String> testUrlsInSiteOrder() {
        Map<Site, String> testUrls = trackerProperties.testUrls();
        return testUrls == null ? new EnumMap<>(Site.class) : new EnumMap<>(testUrls);
    }

    private String testSite(Site site, String url) {
        try {
            ProductSnapshot product = parserRegistry.parserFor(site).fetch(url);
            long sizesInStock = product.sizes().stream().filter(SizeOffer::inStock).count();
            return "✅ " + site.name() + " — " + PriceFormat.format(product.price())
                    + " (" + sizesInStock + "/" + product.sizes().size() + ") "
                    + truncate(product.title(), TEST_TITLE_LENGTH);
        } catch (Exception e) {
            log.warn("Parser test failed for {}", site, e);
            return "❌ " + site.name() + " — " + truncate(e.toString(), TEST_ERROR_LENGTH);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return String.valueOf(value);
        }
        return value.substring(0, maxLength) + "…";
    }
}
