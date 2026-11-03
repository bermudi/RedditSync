package com.resync.app.net;

import android.content.Context;
import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.errors.OAuthError;
import com.resync.app.RedditApplication;
import com.resync.app.singleton.AccountSingleton;
import java.util.Map;
import com.resync.app.net.OAuthRateHelper;
import com.resync.app.helpers.CrashlyticsHelper;
import com.resync.app.helpers.GoogleAnalyticsHelper;
import com.resync.app.util.Logger;

/* JADX INFO: renamed from: x8.a */
/* JADX INFO: compiled from: RedditRequest.java */
/* JADX INFO: loaded from: classes.dex */
public abstract class RedditRequest<T> extends Request<T> {

    public final String URL_KEY;
    public final String tag;
    protected final Context context;

    protected final Response.Listener<T> responseListener;

    protected String token;

    protected Map<String, String> params;

    // Late-2026 edge wall: reddit 403s thread-shaped endpoints (/comments/{id}.json, …) for the
    // plain HTTP stack, answering with the shreddit web-app HTML instead of JSON, while listings
    // still pass. When that signature arrives we re-fetch the same path through the session
    // WebView (real browser engine + cookies, clears the edge challenge) and feed the body to
    // this request's own parser. One attempt per request.
    private boolean webProxyAttempted;

    public RedditRequest(Context context, int method, String str, Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(method, str, errorListener);
        this.URL_KEY = "URL";
        this.tag = getClass().getSimpleName();
        this.context = context;
        this.responseListener = listener;
        setShouldCache(false);
        Logger.verbose("URL", getUrl());
    }

    public void setToken(String str) {
        this.token = str;
    }

    @Override // com.android.volley.Request
    public void deliverResponse(T t) {
        if (this.responseListener == null || isCanceled()) {
            return;
        }
        this.responseListener.onResponse(t);
    }

    /**
     * Intercepts error delivery: a 403 whose body is the shreddit web-app HTML means the edge
     * bot-walled this endpoint for the plain HTTP stack — thread-shaped URLs no longer serve
     * JSON to ANY client (verified: fetch() from a cleared real page still 403s), but the
     * server-rendered page itself loads fine. Subclasses return a {@link #webProxySpec()} and
     * the fallback loads that page in the session WebView and evaluates the spec's JS, which
     * rebuilds the legacy JSON this request's parser expects.
     */
    @Override // com.android.volley.Request
    public void deliverError(VolleyError error) {
        if (!webProxyAttempted && isWebApp403(error) && getMethod() == Method.GET) {
            String[] spec = webProxySpec();
            if (spec != null) {
                webProxyAttempted = true;
                final String pageUrl = spec[0];
                final String waitFor = spec[1];
                final String js = spec[2];
                android.util.Log.i("WebView403", "edge 403 for " + getUrl() + " -> scraping " + pageUrl);
                try {
                    com.resync.app.auth.WebSessionManager.get(this.context).evalOnPage(pageUrl, js, waitFor,
                            new com.resync.app.auth.WebSessionManager.FetchCallback() {
                                @Override public void onFetched(int status, String body) {
                                    String json = body == null ? "" : body.trim();
                                    // evalOnPage delivers the JS return value; webProxySpec JS
                                    // returns JSON.stringify(...), so this is the document itself.
                                    if (status == 200 && json.startsWith("[")) {
                                        try {
                                            Response<T> parsed = parseNetworkResponse(new NetworkResponse(200,
                                                    json.getBytes("UTF-8"), new java.util.HashMap<String, String>(), false));
                                            if (parsed.isSuccess()) {
                                                android.util.Log.i("WebView403", "scrape ok for " + getUrl());
                                                deliverResponse(parsed.result);
                                                return;
                                            }
                                            android.util.Log.i("WebView403", "scrape parse failed: " + parsed.error);
                                            RedditRequest.this.deliverError(parsed.error != null ? parsed.error
                                                    : new VolleyError("web scrape parse failed"));
                                        } catch (Exception e) {
                                            android.util.Log.i("WebView403", "scrape dispatch: " + e
                                                    + " body=" + json.substring(0, Math.min(800, json.length())));
                                            RedditRequest.this.deliverError(new VolleyError("web scrape: " + e));
                                        }
                                    } else {
                                        android.util.Log.i("WebView403", "scrape HTTP " + status + ": "
                                                + (json.length() > 120 ? json.substring(0, 120) : json));
                                        RedditRequest.this.deliverError(new VolleyError("web scrape HTTP " + status));
                                    }
                                }
                                @Override public void onError(String reason) {
                                    android.util.Log.i("WebView403", "scrape error: " + reason);
                                    RedditRequest.this.deliverError(new VolleyError("web scrape: " + reason));
                                }
                            });
                    return; // swallowed the 403; the scrape callbacks deliver the real outcome
                } catch (Exception e) {
                    android.util.Log.i("WebView403", "dispatch failed: " + e);
                    // fall through and deliver the original error
                }
            }
        }
        super.deliverError(error);
    }

    /**
     * Subclass hook for the edge-wall fallback: {@code {pageUrl, waitForSelector, js}} where js
     * runs in the loaded page and returns JSON.stringify(...) of the legacy response body this
     * request's parser expects. Null (default) = no fallback; the original error is delivered.
     */
    protected String[] webProxySpec() {
        return null;
    }

    /** True for the edge bot-wall signature: HTTP 403 whose body is HTML (the web-app shell). */
    private static boolean isWebApp403(VolleyError error) {
        if (error == null || error.networkResponse == null) {
            return false;
        }
        if (error.networkResponse.statusCode != 403 || error.networkResponse.data == null
                || error.networkResponse.data.length == 0) {
            return false;
        }
        int n = Math.min(64, error.networkResponse.data.length);
        String head = new String(error.networkResponse.data, 0, n).trim();
        return head.startsWith("<body") || head.startsWith("<!DOCTYPE") || head.startsWith("<html");
    }

    @Override // com.android.volley.Request
    public Map<String, String> getHeaders() throws AuthFailureError {
        return AccountSingleton.getAuthHeaders(this.token);
    }

    @Override // com.android.volley.Request
    protected Map<String, String> getParams() throws AuthFailureError {
        return this.params;
    }

    public boolean isRateLimited() {
        return !OAuthRateHelper.isWithinRateLimit(getUrl());
    }

    @Override // com.android.volley.Request
    public VolleyError parseNetworkError(VolleyError volleyError) {
        if (volleyError == null) {
            CrashlyticsHelper.logEvent(6, "Volley", "No error returned!");
            return super.parseNetworkError(volleyError);
        }
        try {
            Logger.verbose("RedditRequest", new String(volleyError.networkResponse.data));
        } catch (Exception unused) {
            Logger.verbose("RedditRequest", "No data returned");
        }
        NetworkResponse networkResponse = volleyError.networkResponse;
        if (networkResponse != null) {
            Map<String, String> map = networkResponse.headers;
            if (map.get("www-authenticate") != null && map.get("www-authenticate").equals("Bearer realm=\"reddit\", error=\"invalid_token\"")) {
                GoogleAnalyticsHelper.trackEvent("Errors", "OAuth", "Invalid token");
                RequestQueue requestQueue = RedditApplication.oauthRequestQueue;
                if (requestQueue != null && requestQueue.getCache() != null) {
                    RedditApplication.mainRequestQueue.getCache().clear();
                }
                return new OAuthError(volleyError);
            }
        }
        return super.parseNetworkError(volleyError);
    }
}
