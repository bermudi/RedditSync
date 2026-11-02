package com.resync.app.auth;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import com.resync.app.util.Logger;

/**
 * Drop-in replacement for Sync's dead OAuth-app token flow
 * (<code>https://ssl.reddit.com/api/v1/access_token</code>).
 *
 * <p>Reddit retired third-party API app keys, so that endpoint returns 401. Instead we authenticate
 * exactly like a logged-in browser: a reddit.com web session (cookies set by logging in) can mint a
 * normal OAuth bearer JWT from <code>https://www.reddit.com/svc/shreddit/token</code>, and that JWT
 * works unchanged against <code>oauth.reddit.com/*</code> (validated: /api/v1/me 200, /best 200).
 *
 * <p><b>Why a WebView and not OkHttp:</b> the token endpoint is protected by Fastly TLS
 * fingerprinting — a plain Android HTTP client gets a stable <b>403 Forbidden</b> even with valid
 * cookies. Only a real browser engine is accepted. So we keep a hidden, long-lived {@link WebView}
 * that holds the reddit.com session and mint tokens by running <code>fetch('/svc/shreddit/token')</code>
 * <i>inside</i> it (Chromium TLS + the cookie jar), reading the JSON back over a JS bridge.
 *
 * <p>The JWT lasts ~24h; refresh = re-fetch (the session cookies are long-lived). On cookie expiry
 * the fetch returns 403/empty and the user must re-login via {@code WebSessionLoginFragment}.
 */
public final class WebSessionManager {

    private static final String TAG = "WebSessionManager";
    private static final String TOKEN_URL = "https://www.reddit.com/svc/shreddit/token";
    /** Refresh when the cached token is within this many ms of expiry. */
    private static final long EXPIRY_SKEW_MS = 5 * 60 * 1000L;
    // The minted bearer survives app restarts here (the in-memory cache dies with the process,
    // and every cold start re-minting means riding reddit's edge bot-challenge each time).
    private static final String PREFS_NAME = "web_session";
    private static final String PREF_TOKEN = "bearer_token";
    private static final String PREF_EXPIRY = "bearer_expiry";

    private static WebSessionManager instance;

    private final Context appContext;
    private WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());

    // Cached token
    private volatile String cachedToken;
    private volatile long cachedExpiryMs;

    // In-flight token requests waiting for the JS bridge to call back.
    private final List<TokenCallback> pending = new ArrayList<>();
    private boolean fetchInFlight;

    // Mint bookkeeping: the hidden WebView must finish loading a reddit.com page before the
    // same-origin fetch can run (fetching from about:blank is a null-origin CORS failure), and a
    // failed mint is retried once through the full home page so reddit's edge JS challenge can run.
    private volatile boolean pageLoaded;
    private int mintAttempt;

    public interface TokenCallback {
        void onToken(String jwt);
        void onError(String reason);
    }

    /** Result of {@link #fetchUsername}; {@code name} is null on any failure. */
    public interface NameCallback {
        void onName(String name);
    }

    private WebSessionManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized WebSessionManager get(Context context) {
        if (instance == null) {
            instance = new WebSessionManager(context);
        }
        return instance;
    }

    /** Only reddit.com (and subdomains) may load in the token WebView — the JS bridge guard. */
    private static boolean isRedditHost(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        return host != null && (host.equals("reddit.com") || host.endsWith(".reddit.com"));
    }

    /** True if a reddit.com web session cookie is present (i.e. the user has logged in). */
    public boolean hasSession() {
        String cookies = CookieManager.getInstance().getCookie("https://www.reddit.com");
        return cookies != null && cookies.contains("reddit_session");
    }

    /** Returns a non-expired cached token without any network, or null. */
    public String getCachedToken() {
        if (cachedToken == null) {
            restorePersistedToken();
        }
        if (cachedToken != null && System.currentTimeMillis() < cachedExpiryMs - EXPIRY_SKEW_MS) {
            return cachedToken;
        }
        return null;
    }

    /** Loads the last minted token from prefs; the volatile copy is gone after process death. */
    private void restorePersistedToken() {
        try {
            android.content.SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, 0);
            String token = prefs.getString(PREF_TOKEN, null);
            long expiry = prefs.getLong(PREF_EXPIRY, 0L);
            if (token != null && !token.isEmpty()
                    && System.currentTimeMillis() < expiry - EXPIRY_SKEW_MS) {
                cachedToken = token;
                cachedExpiryMs = expiry;
            }
        } catch (Exception e) {
            android.util.Log.i(TAG, "restorePersistedToken failed: " + e);
        }
    }

    private void persistToken(String jwt, long expiryMs) {
        try {
            appContext.getSharedPreferences(PREFS_NAME, 0).edit()
                    .putString(PREF_TOKEN, jwt)
                    .putLong(PREF_EXPIRY, expiryMs)
                    .apply();
        } catch (Exception e) {
            android.util.Log.i(TAG, "persistToken failed: " + e);
        }
    }

    /**
     * Get a valid bearer JWT, asynchronously. Serves the cached token if fresh, otherwise mints a new
     * one inside the WebView. The callback fires on the main thread.
     */
    public void getToken(final TokenCallback callback) {
        String fresh = getCachedToken();
        if (fresh != null) {
            callback.onToken(fresh);
            return;
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                // No hasSession() gate any more: we mint whether or not the user is logged in.
                // With a reddit_session cookie -> a user bearer; without one -> an ANONYMOUS bearer
                // (reddit's own logged-out web client mints the same), so the front page and all
                // public listings/threads load for signed-out users instead of erroring with 401.
                synchronized (pending) {
                    pending.add(callback);
                    if (fetchInFlight) {
                        return; // a fetch is already running; this callback will be served by it
                    }
                    fetchInFlight = true;
                    mintAttempt = 0;
                }
                ensureWebView();
                mintToken(0);
            }
        });
    }

    /** Blocking variant for use from Volley worker threads. Returns null on failure/timeout. */
    public String getTokenBlocking(long timeoutMs) {
        String fresh = getCachedToken();
        if (fresh != null) {
            return fresh;
        }
        final Object lock = new Object();
        final String[] result = new String[1];
        final boolean[] done = new boolean[1];
        getToken(new TokenCallback() {
            @Override public void onToken(String jwt) {
                synchronized (lock) { result[0] = jwt; done[0] = true; lock.notifyAll(); }
            }
            @Override public void onError(String reason) {
                android.util.Log.i(TAG, "Token error: " + reason);
                synchronized (lock) { done[0] = true; lock.notifyAll(); }
            }
        });
        synchronized (lock) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (!done[0]) {
                long wait = deadline - System.currentTimeMillis();
                if (wait <= 0) break;
                try { lock.wait(wait); } catch (InterruptedException e) { break; }
            }
        }
        return result[0];
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void ensureWebView() {
        if (webView != null) {
            return;
        }
        webView = new WebView(appContext);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // Hardening (Android WebView security best practices): the bridge below is injected into
        // every frame, so we must guarantee only reddit.com can ever run in this WebView. Lock down
        // local file access (token-exfil vector) and block navigation to any non-reddit origin.
        s.setAllowFileAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setAllowContentAccess(false);
        // Present as a mobile browser; an empty UA gets 403, default library UAs get 429.
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; SM-G950F) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isRedditHost(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // The token fetch must run from a loaded reddit.com document; about:blank is a
                // null origin and the same-origin fetch fails CORS from there.
                if (isRedditHost(Uri.parse(url))) {
                    pageLoaded = true;
                }
            }
        });
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        // The bridge is only safe because this WebView is pinned to reddit.com (above).
        webView.addJavascriptInterface(new Bridge(), "SyncTokenBridge");
    }

    /**
     * Runs the token fetch inside the WebView. We must be on a reddit.com origin for the same-origin
     * POST + cookie attach to work, so we load the home page, wait for it to finish loading AND the
     * CSRF cookie to be present, then fetch.
     *
     * <p>The full home page — never a bare resource — because reddit's edge serves its 403
     * bot-challenge to plain fetches, and only a real page executes the challenge JS that clears
     * it (logged-in or not). Anonymous mints additionally need the page for the {@code loid}+
     * {@code csrf_token} cookies.
     */
    private void mintToken(int attempt) {
        // Always the full home page. Reddit's edge now serves its 403 bot-challenge even to
        // logged-in sessions, and a bare robots.txt page runs no JS — the challenge can never
        // clear there (observed live: fast path 403, home-page retry minted ~2s later). The
        // home page executes the challenge, so it is the only reliable origin for the fetch.
        pageLoaded = false;
        webView.loadUrl("https://www.reddit.com/");
        waitForPageThenFetch(System.currentTimeMillis(), 20000L);
    }

    /**
     * Polls until the loaded page has finished AND the {@code csrf_token} cookie exists, then runs
     * the token fetch. On timeout runs the fetch anyway (best effort — the retry in {@link #deliver}
     * handles a second failure through the full home page).
     */
    private void waitForPageThenFetch(final long startMs, final long timeoutMs) {
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                String cookies = CookieManager.getInstance().getCookie("https://www.reddit.com");
                boolean haveCsrf = cookies != null && cookies.contains("csrf_token=");
                if ((pageLoaded && haveCsrf) || System.currentTimeMillis() - startMs >= timeoutMs) {
                    runTokenFetch();
                } else {
                    main.postDelayed(this, 400);
                }
            }
        }, 400);
    }

    /** Same-origin POST to the token endpoint, reading the CSRF token from the WebView's cookie jar. */
    private void runTokenFetch() {
        String js =
            "(function(){try{"
          + "var m=document.cookie.split(';').map(function(c){return c.trim();})"
          + ".find(function(c){return c.indexOf('csrf_token=')===0;});"
          + "var csrf=m?m.split('=')[1]:'';"
          + "fetch('" + TOKEN_URL + "',{method:'POST',credentials:'include',"
          + "headers:{'Content-Type':'application/json','Accept':'application/json'},"
          + "body:JSON.stringify({csrf_token:csrf})})"
          + ".then(function(r){var s=r.status;return r.text().then(function(t){SyncTokenBridge.onToken(t,s);});})"
          + ".catch(function(e){SyncTokenBridge.onError(String(e));});"
          + "}catch(e){SyncTokenBridge.onError(String(e));}})();";
        webView.evaluateJavascript(js, null);
    }

    /**
     * Serves all pending callbacks. On failure with the fast path (attempt 0), retries once through
     * the full home page before giving up, so a single edge bot-check doesn't fail the login.
     */
    private void deliver(String jwt, String reason) {
        if (jwt == null && mintAttempt < 1) {
            mintAttempt++;
            android.util.Log.i(TAG, "Token mint failed (" + reason + "); retrying via home page");
            mintToken(mintAttempt);
            return;
        }
        List<TokenCallback> toCall;
        synchronized (pending) {
            toCall = new ArrayList<>(pending);
            pending.clear();
            fetchInFlight = false;
        }
        for (TokenCallback cb : toCall) {
            if (jwt != null) cb.onToken(jwt); else cb.onError(reason);
        }
    }

    /** JS → Java bridge. Called by the in-WebView fetch. */
    private final class Bridge {
        @JavascriptInterface
        public void onToken(String responseBody, int status) {
            String jwt = null;
            String reason = null;
            try {
                JSONObject o = new JSONObject(responseBody);
                jwt = o.optString("token", null);
                long expires = o.optLong("expires", 0L);
                if (jwt != null && !jwt.isEmpty()) {
                    cachedToken = jwt;
                    cachedExpiryMs = expires > 0 ? expires : System.currentTimeMillis() + 23 * 60 * 60 * 1000L;
                    persistToken(jwt, cachedExpiryMs);
                    android.util.Log.i(TAG, "Minted web-session token, expires " + cachedExpiryMs);
                } else {
                    reason = "HTTP " + status + ": " + snippet(responseBody);
                    android.util.Log.i(TAG, "Token endpoint returned no token: " + reason);
                    jwt = null;
                }
            } catch (Exception e) {
                reason = "HTTP " + status + " (" + e + "): " + snippet(responseBody);
                android.util.Log.i(TAG, "Token parse error: " + reason);
                jwt = null;
            }
            final String result = jwt;
            final String why = reason;
            main.post(new Runnable() {
                @Override public void run() { deliver(result, why); }
            });
        }

        @JavascriptInterface
        public void onError(String reason) {
            android.util.Log.i(TAG, "In-WebView fetch error: " + reason);
            final String why = "fetch error: " + reason;
            main.post(new Runnable() {
                @Override public void run() { deliver(null, why); }
            });
        }

        private String snippet(String s) {
            return s.substring(0, Math.min(80, s.length())).replace('\n', ' ');
        }
    }

    /**
     * Fetches the logged-in account's real username from {@code oauth.reddit.com/api/v1/me} using the
     * given bearer JWT. This is the authoritative source — unlike scraping {@code window.___r} from the
     * login page DOM (which returns empty on the post-login SPA and forced the bogus "Reddit user"
     * fallback whose embedded space then 400/404s every {@code /user/<name>/about} request).
     *
     * <p>Runs on a background thread; {@code callback.onName} fires on the main thread with the
     * username, or {@code null} on any failure (caller keeps whatever it had).
     */
    public void fetchUsername(final String jwt, final NameCallback callback) {
        if (jwt == null || jwt.isEmpty()) {
            main.post(new Runnable() { @Override public void run() { callback.onName(null); } });
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String name = null;
                try {
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                    okhttp3.Request req = new okhttp3.Request.Builder()
                            .url("https://oauth.reddit.com/api/v1/me")
                            .header("Authorization", "bearer " + jwt)
                            // Reddit rejects generic UAs; use Sync's required UA format.
                            .header("User-Agent", USER_AGENT)
                            .get()
                            .build();
                    okhttp3.Response resp = client.newCall(req).execute();
                    try {
                        if (resp.isSuccessful() && resp.body() != null) {
                            JSONObject me = new JSONObject(resp.body().string());
                            String n = me.optString("name", null);
                            if (n != null && !n.isEmpty()) {
                                name = n;
                                android.util.Log.i(TAG, "fetchUsername: /api/v1/me -> " + n);
                            }
                        } else {
                            android.util.Log.i(TAG, "fetchUsername: /api/v1/me HTTP " + resp.code());
                        }
                    } finally {
                        resp.close();
                    }
                } catch (Exception e) {
                    android.util.Log.i(TAG, "fetchUsername error: " + e);
                }
                final String result = name;
                main.post(new Runnable() { @Override public void run() { callback.onName(result); } });
            }
        }, "sync-fetch-username").start();
    }

    /** Reddit-required User-Agent (matches AccountSingleton.getUserAgent()'s format). */
    private static final String USER_AGENT =
            "android:com.resync.app:v1.0";

    /** Clear the cached token (e.g. on logout or 401). */
    public void invalidate() {
        cachedToken = null;
        cachedExpiryMs = 0L;
        try {
            appContext.getSharedPreferences(PREFS_NAME, 0).edit().clear().apply();
        } catch (Exception unused) {
        }
    }

    /** Full logout: clear token + reddit.com cookies + web storage (both layers, then flush). */
    public void logout() {
        invalidate();
        android.webkit.WebStorage.getInstance().deleteAllData();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
    }
}
