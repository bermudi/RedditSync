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
    /** Per-job timeout for proxied JSON fetches. */
    private static final long FETCH_TIMEOUT_MS = 20000L;
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

    // ---- WebView JSON proxy (late-2026 edge bot-wall) ----
    // Reddit's edge 403-walls thread-shaped endpoints (/comments/{id}.json etc.) for non-browser
    // HTTP stacks while listings still pass, so RedditRequest falls back to fetching through this
    // WebView (a real engine with cookies that clears the edge challenge). One fetch at a time —
    // a WebView executes JS on a single main thread.
    private static final class FetchJob {
        final String url;
        final FetchCallback callback;
        final String accept;
        final boolean withAuth;
        /** When set, navigate to {@link #url} and evaluate this JS instead of fetch()-ing. */
        final String evalJs;
        /** Selector the loaded page must contain before {@link #evalJs} runs (hydration guard). */
        final String evalWaitFor;
        boolean retried;
        FetchJob(String url, String accept, boolean withAuth, FetchCallback callback) {
            this(url, accept, withAuth, null, null, callback);
        }
        FetchJob(String url, String accept, boolean withAuth, String evalJs, String evalWaitFor,
                 FetchCallback callback) {
            this.url = url; this.accept = accept; this.withAuth = withAuth;
            this.evalJs = evalJs; this.evalWaitFor = evalWaitFor; this.callback = callback;
        }
    }
    private final java.util.ArrayDeque<FetchJob> fetchQueue = new java.util.ArrayDeque<>();
    private boolean proxyFetchInFlight;
    private FetchJob activeJob;
    private Runnable proxyTimeout;

    public interface TokenCallback {
        void onToken(String jwt);
        void onError(String reason);
    }

    /**
     * Result of {@link #fetchViaWebView}: {@code onFetched} carries the HTTP status and body of the
     * proxied request (body is null/empty never; status 0 means the fetch itself failed).
     */
    public interface FetchCallback {
        void onFetched(int status, String body);
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

    /** True for the edge bot-challenge interstitial URLs (…?solution=…&js_challenge=…). */
    private static boolean isChallengeUrl(String url) {
        return url != null && (url.contains("js_challenge=") || url.contains("solution="));
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
                // null origin and the same-origin fetch fails CORS from there. The edge bot
                // challenge redirects to ...?solution=…&js_challenge=… — still a reddit.com URL,
                // so a naive "any page finished" flag would fire on the UNCLEARED interstitial
                // and every fetch from it 403s. Only a page outside the challenge flow counts.
                if (isRedditHost(Uri.parse(url)) && !isChallengeUrl(url)) {
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

    // ---- WebView JSON proxy ----

    /**
     * Fetches a www.reddit.com URL from inside the session WebView (same-origin browser fetch with
     * the full cookie jar and a real Chromium TLS stack) and hands back status+body. Used as the
     * fallback when Reddit's edge 403-walls an endpoint for the plain HTTP stack. Jobs are
     * serialized; the callback fires on the main thread.
     */
    public void fetchViaWebView(final String url, final FetchCallback callback) {
        fetchViaWebView(url, "application/json", false, callback);
    }

    /** Full-control variant: custom Accept header, optionally the cached bearer as Authorization. */
    public void fetchViaWebView(final String url, final String accept, final boolean withAuth,
                                final FetchCallback callback) {
        main.post(new Runnable() {
            @Override public void run() {
                fetchQueue.add(new FetchJob(url, accept, withAuth, callback));
                pumpFetchQueue();
            }
        });
    }

    /**
     * Navigates the WebView to {@code url} (a real page load — passes the edge that walls
     * fetch()-shaped requests), waits for {@code waitForSelector} to appear (server-rendered
     * content; guards against the cache serving a mid-hydration SPA shell), then evaluates
     * {@code js} in the page and returns its stringified result via the callback's body.
     */
    public void evalOnPage(final String url, final String js, final String waitForSelector,
                           final FetchCallback callback) {
        main.post(new Runnable() {
            @Override public void run() {
                fetchQueue.add(new FetchJob(url, null, false, js, waitForSelector, callback));
                pumpFetchQueue();
            }
        });
    }

    private void pumpFetchQueue() {
        if (proxyFetchInFlight || fetchQueue.isEmpty()) {
            return;
        }
        proxyFetchInFlight = true;
        activeJob = fetchQueue.poll();
        ensurePageThenFetch(activeJob);
    }

    /**
     * A cleared reddit.com page (not the challenge interstitial) with the csrf cookie present —
     * the same bar the token mint waits for. Fetching from an uncleared page 403s.
     */
    private boolean pageReady() {
        if (!pageLoaded) {
            return false;
        }
        String cookies = CookieManager.getInstance().getCookie("https://www.reddit.com");
        return cookies != null && cookies.contains("csrf_token=");
    }

    private void ensurePageThenFetch(final FetchJob job) {
        ensureWebView();
        if (webView == null) {
            finishProxyJob(job, 0, null, "no webview");
            return;
        }
        if (job.evalJs != null) {
            // Eval jobs must load the TARGET page — its DOM is the data source.
            pageLoaded = false;
            webView.loadUrl(job.url);
            waitUntilPageReady(new Runnable() {
                @Override public void run() { runProxyFetch(job); }
            }, System.currentTimeMillis(), 25000L, new Runnable() {
                @Override public void run() { finishProxyJob(job, 0, null, "page never loaded"); }
            });
            return;
        }
        if (pageReady()) {
            runProxyFetch(job);
            return;
        }
        // Need a reddit.com document first: same-origin fetch + the edge challenge only
        // clears by executing a full page's JS (same lesson as the token mint).
        webView.loadUrl("https://www.reddit.com/");
        waitUntilPageReady(new Runnable() {
            @Override public void run() { runProxyFetch(job); }
        }, System.currentTimeMillis(), 20000L, new Runnable() {
            @Override public void run() { finishProxyJob(job, 0, null, "page never loaded"); }
        });
    }

    private void runProxyFetch(final FetchJob job) {
        if (job.evalJs != null) {
            // The result rides the bridge (the eval may poll asynchronously inside the page).
            // Body must be a string — callers return JSON.stringify(...).
            String wait = job.evalWaitFor == null ? "" : jsEscape(job.evalWaitFor);
            String js =
                "(function(){var t0=Date.now();(function poll(){"
              + "try{if(!" + (wait.isEmpty() ? "0" : "document.querySelector('" + wait + "')")
              + " && Date.now()-t0<10000){setTimeout(poll,300);return;}"
              + "var r=(function(){" + job.evalJs + "})();"
              + "SyncTokenBridge.onFetch(200,typeof r==='undefined'?'':String(r));"
              + "}catch(e){SyncTokenBridge.onFetch(200,'EVALERR '+e);}})();})();";
            webView.evaluateJavascript(js, null);
            proxyTimeout = new Runnable() {
                @Override public void run() {
                    finishProxyJob(job, 0, null, "eval timeout (" + FETCH_TIMEOUT_MS + "ms)");
                }
            };
            main.postDelayed(proxyTimeout, FETCH_TIMEOUT_MS);
            return;
        }
        String headers = "{'Accept':'" + jsEscape(job.accept) + "'";
        if (job.withAuth && cachedToken != null) {
            headers += ",'Authorization':'Bearer '+" + JSONObject.quote(cachedToken);
        }
        headers += "}";
        String js =
            "(function(){try{"
          + "fetch('" + jsEscape(job.url) + "',{credentials:'include',headers:" + headers + "})"
          + ".then(function(r){var s=r.status;return r.text().then(function(t){"
          + "SyncTokenBridge.onFetch(s,t);});})"
          + ".catch(function(e){SyncTokenBridge.onFetch(0,String(e));});"
          + "}catch(e){SyncTokenBridge.onFetch(0,String(e));}})();";
        webView.evaluateJavascript(js, null);
        proxyTimeout = new Runnable() {
            @Override public void run() {
                finishProxyJob(job, 0, null, "timeout (" + FETCH_TIMEOUT_MS + "ms)");
            }
        };
        main.postDelayed(proxyTimeout, FETCH_TIMEOUT_MS);
    }

    /** Polls until {@link #pageReady()}, then runs {@code then}; {@code or} on timeout. */
    private void waitUntilPageReady(final Runnable then, final long startMs, final long timeoutMs, final Runnable or) {
        main.postDelayed(new Runnable() {
            @Override public void run() {
                if (activeJob == null) {
                    return; // job was finished/cancelled meanwhile
                }
                if (pageReady()) {
                    then.run();
                } else if (System.currentTimeMillis() - startMs >= timeoutMs) {
                    or.run();
                } else {
                    main.postDelayed(this, 400);
                }
            }
        }, 400);
    }

    private void finishProxyJob(FetchJob job, int status, String body, String error) {
        if (proxyTimeout != null) {
            main.removeCallbacks(proxyTimeout);
            proxyTimeout = null;
        }
        proxyFetchInFlight = false;
        if (activeJob == job) {
            activeJob = null;
        }
        if (error != null) {
            android.util.Log.i(TAG, "proxy fetch failed for " + job.url + ": " + error);
            job.callback.onError(error);
        } else {
            android.util.Log.i(TAG, "proxy fetch " + status + " (" + (body == null ? 0 : body.length())
                    + " bytes) for " + job.url);
            job.callback.onFetched(status, body == null ? "" : body);
        }
        pumpFetchQueue();
    }

    private static String jsEscape(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    /** JS → Java bridge. Called by the in-WebView fetch. */
    private final class Bridge {        @JavascriptInterface
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

        /** Proxy-fetch callback: status + body of a {@link #fetchViaWebView} job. */
        @JavascriptInterface
        public void onFetch(final int status, final String body) {
            main.post(new Runnable() {
                @Override public void run() {
                    FetchJob job = activeJob;
                    if (job == null) {
                        return;
                    }
                    // A 403 with the web-app HTML means the page we fetched from was uncleared
                    // (or its clearance just expired). One more time through a fresh home page —
                    // the same recovery the token mint uses.
                    if (status == 403 && isHtmlBody(body) && !job.retried) {
                        job.retried = true;
                        android.util.Log.i(TAG, "proxy 403 HTML; reloading home page and retrying once: " + job.url);
                        if (proxyTimeout != null) {
                            main.removeCallbacks(proxyTimeout);
                            proxyTimeout = null;
                        }
                        pageLoaded = false;
                        ensurePageThenFetch(job);
                        return;
                    }
                    finishProxyJob(job, status, body, null);
                }
            });
        }

        private String snippet(String s) {
            return s.substring(0, Math.min(80, s.length())).replace('\n', ' ');
        }
    }

    private static boolean isHtmlBody(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        String head = body.substring(0, Math.min(64, body.length())).trim();
        return head.startsWith("<body") || head.startsWith("<!DOCTYPE") || head.startsWith("<html");
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
