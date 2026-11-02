package com.resync.app.net.thirdparty;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import com.resync.app.util.Logger;

/**
 * RedGifs temporary tokens: minted from {@code /v2/auth/temporary}, good for about a day, and
 * tied to the user agent they were issued to — so one agent is used everywhere here. Shared by
 * the RedGifs and (redirected) Gfycat gif requests.
 */
public final class RedgifsTokens {

    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private static final String TEMPORARY_TOKEN_URL = "https://api.redgifs.com/v2/auth/temporary";

    /** Redgifs tokens last a day; refresh an hour early. */
    private static final long TOKEN_LIFETIME_MILLIS = 23L * 60L * 60L * 1000L;

    private static final Object LOCK = new Object();
    private static String cachedToken;
    private static long cachedTokenExpiresAt;

    private RedgifsTokens() {
    }

    /** A valid bearer token, or null when RedGifs would not give one. Blocking; call off the main thread. */
    public static String token() {
        synchronized (LOCK) {
            if (cachedToken != null && cachedTokenExpiresAt > System.currentTimeMillis()) {
                return cachedToken;
            }
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(TEMPORARY_TOKEN_URL).openConnection();
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "application/json");
                connection.setUseCaches(false);
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Logger.verbose("RedGifs token request returned " + connection.getResponseCode());
                    return null;
                }
                String token = new JSONObject(read(connection.getInputStream())).getString("token");
                cachedToken = token;
                cachedTokenExpiresAt = System.currentTimeMillis() + TOKEN_LIFETIME_MILLIS;
                return token;
            } catch (Exception e) {
                Logger.logException(e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    /** Drops the cached token; the next {@link #token()} mints a fresh one. */
    public static void clear() {
        synchronized (LOCK) {
            cachedToken = null;
            cachedTokenExpiresAt = 0;
        }
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try {
            for (int n; (n = in.read(buffer)) >= 0; ) {
                out.write(buffer, 0, n);
            }
        } finally {
            in.close();
        }
        return out.toString("UTF-8");
    }
}
