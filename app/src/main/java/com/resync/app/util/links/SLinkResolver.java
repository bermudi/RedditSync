package com.resync.app.util.links;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import com.resync.app.auth.WebSessionManager;
import com.resync.app.ui.activities.WebViewActivity;
import com.resync.app.util.Logger;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Resolves Reddit's shortened share links ({@code https://reddit.com/r/<sub>/s/<id>}) into the
 * post they point at, in-app.
 *
 * <p>Reddit's share sheet hands out {@code /s/} links, which answer any plain GET with an
 * interstitial page rather than a redirect Sync could follow. An authorized HEAD is answered
 * with the real destination in the {@code Location} header, so that is what is asked for, with
 * the app's own web-session token attached. A resolution that fails (no token yet, Reddit
 * refusing the IP, a timeout) falls back to the in-app browser, where the page's own script
 * follows the link.
 */
public final class SLinkResolver {

    private static final Pattern SHARE_LINK = Pattern.compile(
            "^https://([a-z]+\\.)?reddit\\.com/r/[A-Za-z0-9_]+/s/[A-Za-z0-9]+/?$");

    private static final String BYPASS_FRAGMENT = "#bypass";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private SLinkResolver() {
    }

    /**
     * @param url a link about to be opened.
     * @return true when it was a share link and its resolution has been taken over; the caller
     *         should not open it further.
     */
    public static boolean handle(Context context, String url) {
        if (url == null) {
            return false;
        }
        int fragment = url.indexOf('#');
        String withoutFragment = fragment < 0 ? url : url.substring(0, fragment);
        if (!SHARE_LINK.matcher(withoutFragment).matches()) {
            return false;
        }
        Context app = context.getApplicationContext();
        String stripped = withoutFragment.endsWith("/")
                ? withoutFragment.substring(0, withoutFragment.length() - 1)
                : withoutFragment;
        // A link arriving with #bypass already failed resolution once; open it rather than retry.
        if (fragment >= 0 && BYPASS_FRAGMENT.equals(url.substring(fragment))) {
            openInAppBrowser(app, stripped);
            return true;
        }
        EXECUTOR.execute(() -> resolve(app, stripped));
        return true;
    }

    private static void resolve(Context context, String link) {
        String destination = link; // falls back to the in-app browser below
        try {
            String token = WebSessionManager.get(context).getTokenBlocking(4000);
            HttpURLConnection connection = (HttpURLConnection) new URL(link).openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                if (token != null) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }
                connection.connect();
                String location = connection.getHeaderField("location");
                if (location == null || location.isEmpty()) {
                    throw new IOException("Missing Reddit redirect location");
                }
                URL resolved = new URL(new URL(link), location);
                String host = resolved.getHost();
                if (host == null || !host.toLowerCase(Locale.ROOT).endsWith("reddit.com")) {
                    throw new IOException("Untrusted Reddit redirect destination: " + host);
                }
                destination = resolved.toString();
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            Logger.logException(e);
        }

        final String open = destination;
        MAIN.post(() -> {
            if (open.equals(link)) {
                openInAppBrowser(context, open);
            } else {
                LinkHelper.open(context, open);
            }
        });
    }

    private static void openInAppBrowser(Context context, String url) {
        Intent intent = new Intent(context, WebViewActivity.class);
        intent.putExtra("url", url);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
