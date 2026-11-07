package com.resync.app.net.thirdparty;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * org.json boundary for RedGifs API response bodies: the temporary-token body and the gif
 * metadata body. No Android or Volley types, so it is testable offline against a real org.json
 * runtime. Bodies that do not match the v2 API shape fail explicitly; there is no HTML scraper
 * or guessed-filename fallback.
 */
public final class RedgifsResponse {

    private RedgifsResponse() {
    }

    /**
     * Reads the required string {@code token} from a {@code /v2/auth/temporary} response.
     *
     * @throws JSONException     on malformed JSON, a missing token, or a non-string token.
     * @throws IllegalArgumentException on a blank/whitespace/control-character token.
     */
    public static String token(String body) throws JSONException {
        JSONObject object = new JSONObject(body);
        Object raw = object.opt("token");
        if (!(raw instanceof String)) {
            throw new JSONException("RedGifs token response has no string token");
        }
        return RedgifsApi.validateTokenValue((String) raw);
    }

    /**
     * Reads {@code gif.urls.hd}/{@code gif.urls.sd} from a {@code /v2/gifs/<id>} response and
     * selects the media URL via {@link RedgifsApi#mediaUrl}. HTML-only bodies (or anything
     * without a usable gif.urls object) are rejected.
     */
    public static String media(String body, boolean preferHd) throws JSONException {
        JSONObject object = new JSONObject(body);
        JSONObject gif = object.optJSONObject("gif");
        if (gif == null) {
            throw new JSONException("RedGifs metadata response has no gif object");
        }
        JSONObject urls = gif.optJSONObject("urls");
        if (urls == null) {
            throw new JSONException("RedGifs metadata response has no urls object");
        }
        Object hd = urls.opt("hd");
        Object sd = urls.opt("sd");
        return RedgifsApi.mediaUrl(asStringOrNull(hd), asStringOrNull(sd), preferHd);
    }

    private static String asStringOrNull(Object value) {
        return value instanceof String ? (String) value : null;
    }
}
