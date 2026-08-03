package com.spendwise.repository.cloud;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.CloudConnectionState;
import com.spendwise.auth.registration.FinanceApiGateway;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CloudFinanceClient {

    private static final int PAGE_SIZE = 100;
    private final FinanceApiGateway gateway;

    public CloudFinanceClient(FinanceApiGateway gateway) {
        this.gateway = Objects.requireNonNull(
                gateway, "Finance API gateway is required.");
    }

    public Map<String, Object> get(String path) {
        return object(request("GET", path, null));
    }

    public Map<String, Object> post(String path, Map<String, ?> body) {
        return object(request("POST", path, body));
    }

    public Map<String, Object> put(String path, Map<String, ?> body) {
        return object(request("PUT", path, body));
    }

    public void delete(String path) {
        gateway.requestFinance("DELETE", path, "");
    }

    public List<Map<String, Object>> getAll(String path) {
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        int page = 0;
        int totalPages;
        do {
            String separator = path.contains("?") ? "&" : "?";
            Map<String, Object> response = get(path + separator + "page="
                    + page + "&size=" + PAGE_SIZE);
            Object content = response.get("content");
            if (!(content instanceof List<?> list)) throw invalidResponse();
            for (Object item : list) result.add(object(item));
            totalPages = integer(response, "totalPages");
            page++;
        } while (page < totalPages);
        return List.copyOf(result);
    }

    public CloudConnectionState getConnectionState() {
        return gateway.getCloudConnectionState();
    }

    public static String segment(String value) {
        return URLEncoder.encode(Objects.requireNonNull(value),
                StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String text(Map<String, Object> map, String name) {
        Object value = map.get(name);
        if (!(value instanceof String text)) throw invalidResponse();
        return text;
    }

    static String nullableText(Map<String, Object> map, String name) {
        Object value = map.get(name);
        if (value == null) return null;
        if (!(value instanceof String text)) throw invalidResponse();
        return text;
    }

    static java.math.BigDecimal decimal(
            Map<String, Object> map, String name) {
        Object value = map.get(name);
        if (!(value instanceof java.math.BigDecimal number)) {
            throw invalidResponse();
        }
        return number;
    }

    static java.math.BigDecimal nullableDecimal(
            Map<String, Object> map, String name) {
        return map.get(name) == null ? null : decimal(map, name);
    }

    static boolean bool(Map<String, Object> map, String name) {
        Object value = map.get(name);
        if (!(value instanceof Boolean state)) throw invalidResponse();
        return state;
    }

    static int integer(Map<String, Object> map, String name) {
        return decimal(map, name).intValueExact();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw invalidResponse();
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) throw invalidResponse();
        }
        return (Map<String, Object>) map;
    }

    static List<String> strings(Map<String, Object> map, String name) {
        Object value = map.get(name);
        if (!(value instanceof List<?> list)) throw invalidResponse();
        ArrayList<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text)) throw invalidResponse();
            result.add(text);
        }
        return List.copyOf(result);
    }

    private Object request(String method, String path, Map<String, ?> body) {
        String response = gateway.requestFinance(method, path,
                body == null ? "" : JsonSupport.stringify(body));
        return JsonSupport.parse(response);
    }

    private static AuthException invalidResponse() {
        return new AuthException(
                "The cloud finance server returned an invalid response.");
    }
}
