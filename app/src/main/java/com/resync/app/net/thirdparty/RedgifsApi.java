package com.resync.app.net.thirdparty;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure RedGifs protocol rules: public-link to API-URL routing, request headers, media-URL
 * selection and token-refresh policy. java.net/java.util only, so it compiles and tests offline
 * without Android, Volley or org.json.
 */
public final class RedgifsApi {

    public static final String TOKEN_URL = "https://api.redgifs.com/v2/auth/temporary";

    private static final String API_HOST = "api.redgifs.com";
    private static final String SITE_SUFFIX = "redgifs.com";

    private RedgifsApi() {
    }

    /**
     * Maps a public redgifs.com {@code /watch/<id>} or {@code /ifr/<id>} link (root, www or m
     * host, http or https with the scheme's default port tolerated, query/fragment ignored,
     * exactly one trailing slash tolerated) to the v2 gif metadata API URL. Route and id
     * validation uses the raw (still percent-encoded) path, so encoded delimiters cannot
     * rewrite routing. No network, no /info dependency.
     *
     * @throws IllegalArgumentException on a non-RedGifs host, user-info, a non-default port, or
     *                                  a missing/malformed id.
     */
    public static String metadataUrl(String input) {
        URI uri = parse(input);
        String scheme = uri.getScheme();
        boolean https = "https".equalsIgnoreCase(scheme);
        boolean http = "http".equalsIgnoreCase(scheme);
        if (!https && !http) {
            throw new IllegalArgumentException("Not an http(s) redgifs URL");
        }
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (https && port == 443) || (http && port == 80);
        if (uri.getUserInfo() != null || !defaultPort) {
            throw new IllegalArgumentException("Unexpected user-info or port");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Missing host");
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (!"redgifs.com".equals(lowerHost) && !"www.redgifs.com".equals(lowerHost)
            && !"m.redgifs.com".equals(lowerHost)) {
            throw new IllegalArgumentException("Not a redgifs.com host");
        }
        String rawPath = uri.getRawPath();
        if (rawPath == null) {
            throw new IllegalArgumentException("Missing path");
        }
        if (rawPath.endsWith("/") && rawPath.length() > 1) {
            rawPath = rawPath.substring(0, rawPath.length() - 1);
        }
        // split with limit -1: after stripping one trailing slash, any further empty segment
        // (double slash) must be a mismatch, not silently discarded.
        String[] segments = rawPath.split("/", -1);
        if (segments.length != 3 || (!"watch".equals(segments[1]) && !"ifr".equals(segments[1]))) {
            throw new IllegalArgumentException("Not a /watch or /ifr path");
        }
        String id = segments[2];
        if (id.isEmpty() || !isAsciiAlphanumeric(id)) {
            throw new IllegalArgumentException("Malformed redgifs id");
        }
        return "https://" + API_HOST + "/v2/gifs/" + id.toLowerCase(Locale.ROOT);
    }

    /**
     * Headers for RedGifs API calls. RedGifs ties a temporary token to the user agent it was
     * issued for, so the same User-Agent must mint and spend the token; callers pass theirs
     * through unchanged. A null token omits Authorization entirely. Never Reddit/default auth.
     */
    public static Map<String, String> headers(String userAgent, String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Accept", "application/json");
        if (token != null) {
            headers.put("Authorization", "Bearer " + validateTokenValue(token));
        }
        return headers;
    }

    /**
     * Picks the preferred quality and falls back to the other when missing or invalid. Returns
     * the API-provided URL unchanged; a valid URL is HTTPS on redgifs.com or a subdomain, no
     * user-info, default/443 port, path ending .mp4 (query allowed). -silent/-mobile file names
     * are preserved as given, never synthesized or stripped.
     *
     * @throws IllegalArgumentException when neither candidate is usable.
     */
    public static String mediaUrl(String hd, String sd, boolean preferHd) {
        String preferred = preferHd ? hd : sd;
        String fallback = preferHd ? sd : hd;
        if (isUsableMediaUrl(preferred)) {
            return preferred;
        }
        if (isUsableMediaUrl(fallback)) {
            return fallback;
        }
        throw new IllegalArgumentException("No usable redgifs media URL");
    }

    /**
     * One token refresh is worthwhile only for an auth refusal on a first attempt: 401/403 and
     * not already retried. 404 (gone gif), 429, 5xx and transport failures (no status) are
     * terminal.
     */
    public static boolean shouldRefresh(int status, boolean alreadyRetried) {
        return !alreadyRetried && (status == 401 || status == 403);
    }

    /** Rejects blank tokens and tokens containing whitespace or control characters. */
    static String validateTokenValue(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Empty token");
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c <= ' ' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                throw new IllegalArgumentException("Invalid token characters");
            }
        }
        return token;
    }

    private static boolean isUsableMediaUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (!SITE_SUFFIX.equals(lowerHost) && !lowerHost.endsWith("." + SITE_SUFFIX)) {
            return false;
        }
        if (uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String path = uri.getPath();
        return path != null && path.toLowerCase(Locale.ROOT).endsWith(".mp4");
    }

    private static URI parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Null redgifs URL");
        }
        try {
            return new URI(input.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed redgifs URL");
        }
    }

    private static boolean isAsciiAlphanumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'z';
            boolean upper = c >= 'A' && c <= 'Z';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }
}
