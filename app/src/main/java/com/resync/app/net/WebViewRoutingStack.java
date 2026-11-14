package com.resync.app.net;

import android.content.Context;
import android.net.Uri;

import com.android.volley.AuthFailureError;
import com.android.volley.Header;
import com.android.volley.Request;
import com.android.volley.toolbox.BaseHttpStack;
import com.android.volley.toolbox.HttpResponse;
import com.android.volley.toolbox.HurlStack;
import com.resync.app.auth.WebSessionManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * HTTP stack for the Reddit API queue. Thread reads ({@code /comments/<id>}, which also carry
 * gallery data) are blocked by reddit's edge for plain HTTP clients, so they run inside the
 * logged-in session WebView on www.reddit.com instead. Every other request uses {@link HurlStack}.
 */
public final class WebViewRoutingStack extends BaseHttpStack {

    private static final Pattern THREAD_PATH = Pattern.compile("^(/r/[^/]+)?/comments/[^/]+.*");
    private static final long FETCH_TIMEOUT_MS = 30000L;

    private final Context appContext;
    private final BaseHttpStack fallback = new HurlStack();

    public WebViewRoutingStack(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public HttpResponse executeRequest(Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
        Uri uri = Uri.parse(request.getUrl());
        String path = uri.getEncodedPath();
        if (request.getMethod() != Request.Method.GET
                || !"oauth.reddit.com".equals(uri.getHost())
                || path == null || !THREAD_PATH.matcher(path).matches()) {
            return fallback.executeRequest(request, additionalHeaders);
        }
        String query = uri.getEncodedQuery();
        String url = "https://www.reddit.com" + path + (query == null ? "" : "?" + query);
        WebSessionManager.FetchResult result =
                WebSessionManager.get(appContext).fetchBlocking(url, FETCH_TIMEOUT_MS);
        if (result == null || result.status == 0) {
            throw new IOException("Browser fetch of thread failed");
        }
        byte[] body = result.body.getBytes(StandardCharsets.UTF_8);
        return new HttpResponse(result.status,
                Collections.singletonList(new Header("Content-Type", result.contentType)),
                body.length, new ByteArrayInputStream(body));
    }
}
