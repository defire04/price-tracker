package com.price.tracker.infrastructure.client.hotline;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The full Hotline offer list is server-rendered only inside window.__NUXT__ -
 * an IIFE where repeated values are hoisted into function arguments. Field
 * values are therefore either literals or parameter names that must be
 * resolved through the parameter-to-argument map.
 */
@Component
public class HotlineStateExtractor {

    public record HotlineOffer(String firmTitle, BigDecimal price, String conversionUrl, String condition) {
    }

    private static final String STATE_MARKER = "window.__NUXT__=(function(";
    private static final String OFFER_NODE_MARKER = "node:{_id:";
    private static final int OFFER_NODE_SCAN_LENGTH = 2000;
    private static final Pattern CONVERSION_URL = Pattern.compile("conversionUrl:(\"(?:[^\"\\\\]|\\\\.)*\")");
    private static final Pattern FIRM_TITLE = Pattern.compile("firmTitle:(\"(?:[^\"\\\\]|\\\\.)*\"|[\\w$]+)");
    private static final Pattern PRICE = Pattern.compile("price:(\"(?:[^\"\\\\]|\\\\.)*\"|[\\w$.]+)");
    private static final Pattern CONDITION = Pattern.compile("condition:(\"(?:[^\"\\\\]|\\\\.)*\"|[\\w$]+)");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<HotlineOffer> extractOffers(String html) {
        int stateStart = html.indexOf(STATE_MARKER);
        if (stateStart < 0) {
            return List.of();
        }
        String state = html.substring(stateStart, scriptEnd(html, stateStart));
        Map<String, Object> arguments = argumentValues(state);
        List<HotlineOffer> offers = new ArrayList<>();
        int nodeStart = state.indexOf(OFFER_NODE_MARKER);
        while (nodeStart >= 0) {
            String segment = state.substring(nodeStart,
                    Math.min(nodeStart + OFFER_NODE_SCAN_LENGTH, state.length()));
            HotlineOffer offer = parseOffer(segment, arguments);
            if (offer != null) {
                offers.add(offer);
            }
            nodeStart = state.indexOf(OFFER_NODE_MARKER, nodeStart + OFFER_NODE_MARKER.length());
        }
        return offers;
    }

    private int scriptEnd(String html, int stateStart) {
        int end = html.indexOf("</script>", stateStart);
        return end < 0 ? html.length() : end;
    }

    private HotlineOffer parseOffer(String segment, Map<String, Object> arguments) {
        Matcher url = CONVERSION_URL.matcher(segment);
        Matcher firm = FIRM_TITLE.matcher(segment);
        Matcher price = PRICE.matcher(segment);
        Matcher condition = CONDITION.matcher(segment);
        if (!url.find() || !firm.find() || !price.find()) {
            return null;
        }
        Object firmValue = resolve(firm.group(1), arguments);
        Object priceValue = resolve(price.group(1), arguments);
        Object conditionValue = condition.find() ? resolve(condition.group(1), arguments) : null;
        BigDecimal priceNumber = asPrice(priceValue);
        if (!(firmValue instanceof String firmTitle) || firmTitle.isBlank() || priceNumber == null) {
            return null;
        }
        return new HotlineOffer(firmTitle.trim(), priceNumber,
                (String) resolve(url.group(1), arguments),
                conditionValue instanceof String conditionText ? conditionText : null);
    }

    private BigDecimal asPrice(Object value) {
        String text = switch (value) {
            case Number number -> number.toString();
            case String string -> string;
            case null, default -> null;
        };
        if (text == null || !DIGITS.matcher(text).matches()) {
            return null;
        }
        return new BigDecimal(text);
    }

    private Object resolve(String token, Map<String, Object> arguments) {
        if (token.startsWith("\"")) {
            return decodeStringLiteral(token);
        }
        if (arguments.containsKey(token)) {
            return arguments.get(token);
        }
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> argumentValues(String state) {
        int paramsStart = state.indexOf('(', state.indexOf("(function(") + 9) + 1;
        int paramsEnd = state.indexOf(')', paramsStart);
        String[] parameters = state.substring(paramsStart, paramsEnd).split(",");
        int callStart = state.lastIndexOf("}(") + 2;
        int callEnd = state.lastIndexOf("));");
        List<String> rawArguments = splitTopLevel(state.substring(callStart, callEnd));
        Map<String, Object> values = new HashMap<>();
        for (int index = 0; index < parameters.length && index < rawArguments.size(); index++) {
            values.put(parameters[index].trim(), literal(rawArguments.get(index)));
        }
        return values;
    }

    private List<String> splitTopLevel(String source) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (inString) {
                current.append(ch);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            switch (ch) {
                case '"' -> {
                    inString = true;
                    current.append(ch);
                }
                case '(', '[', '{' -> {
                    depth++;
                    current.append(ch);
                }
                case ')', ']', '}' -> {
                    depth--;
                    current.append(ch);
                }
                case ',' -> {
                    if (depth == 0) {
                        parts.add(current.toString());
                        current.setLength(0);
                    } else {
                        current.append(ch);
                    }
                }
                default -> current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private Object literal(String raw) {
        String value = raw.trim();
        if (value.startsWith("\"")) {
            return decodeStringLiteral(value);
        }
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String decodeStringLiteral(String quoted) {
        try {
            return objectMapper.readTree(quoted).asString();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
