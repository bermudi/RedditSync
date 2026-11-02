package com.resync.app.net.oauth;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.resync.app.RedditApplication;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import com.resync.app.net.CustomHttpHeaderParser;
import com.resync.app.model.Gallery;
import com.resync.app.util.Logger;
import com.resync.app.util.WebsafeUtils;
import com.resync.app.net.RedditRequest;
import com.resync.app.util.links.LinkHandler;

/* JADX INFO: renamed from: y8.q */
/* JADX INFO: compiled from: OAuthGalleryRequest.java */
/* JADX INFO: loaded from: classes.dex */
public class OAuthGalleryRequest extends RedditRequest<Gallery> {
    public OAuthGalleryRequest(String str, Response.Listener<Gallery> listener, Response.ErrorListener errorListener) {
        super(RedditApplication.getAppContext(), 0, buildUrl(str), listener, errorListener);
        setShouldCache(true);
    }

    public static String buildUrl(String str) {
        // Reddit no longer answers /api/info for third-party clients (it serves the block page
        // instead), which is what this request used to resolve a gallery post. /comments/{id}
        // publishes the same post object including gallery_data and media_metadata, so the
        // request goes there and the reply is unwrapped from its two-listing thread shape below.
        return "https://oauth.reddit.com/comments/" + LinkHandler.getCommentsId(str.replace("?is_gallery=true", "")) + ".json?limit=1";
    }

    @Override // com.android.volley.Request
    public Response<Gallery> parseNetworkResponse(NetworkResponse networkResponse) {
        try {
            String str = new String(networkResponse.data);
            Logger.verbose(str);
            // A thread replies with [postListing, commentsListing]; the post is first.
            JSONObject jSONObject = new JSONArray(str).getJSONObject(0).getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data");
            JSONObject jSONObject2 = jSONObject.getJSONObject("media_metadata");
            JSONArray jSONArray = jSONObject.getJSONObject("gallery_data").getJSONArray("items");
            Gallery gallery = new Gallery(jSONArray.length());
            for (int index = 0; index < jSONArray.length(); index++) {
                JSONObject jSONObject3 = jSONObject2.getJSONObject(jSONArray.getJSONObject(index).getString("media_id"));
                if (jSONObject3.has("s")) {
                    JSONObject jSONObject4 = jSONObject3.getJSONObject("s");
                    if (jSONObject4.has("mp4")) {
                        gallery.urls[index] = WebsafeUtils.unescapeHtml(jSONObject4.getString("mp4"));
                    } else {
                        gallery.urls[index] = WebsafeUtils.unescapeHtml(jSONObject4.getString("u"));
                    }
                    try {
                        gallery.thumbnails[index] = WebsafeUtils.unescapeHtml(jSONObject3.getJSONArray("p").getJSONObject(0).getString("u"));
                    } catch (Exception unused) {
                        gallery.thumbnails[index] = gallery.urls[index];
                    }
                    StringBuilder sb2 = new StringBuilder();
                    String strOptString = jSONArray.getJSONObject(index).optString("caption");
                    if (StringUtils.isNotEmpty(strOptString)) {
                        sb2.append(strOptString);
                        sb2.append("\n\n");
                    }
                    String strOptString2 = jSONArray.getJSONObject(index).optString("outbound_url");
                    if (StringUtils.isNotEmpty(strOptString2)) {
                        sb2.append(strOptString2);
                    }
                    gallery.captions[index] = sb2.toString();
                } else {
                    gallery.urls[index] = "";
                    gallery.thumbnails[index] = "";
                    gallery.captions[index] = "";
                }
            }
            // The old /api/info reply never carried a "position" field either, so this was always 0.
            gallery.position = 0;
            return Response.success(gallery, CustomHttpHeaderParser.parseCacheEntry(networkResponse, DateUtils.MILLIS_PER_HOUR));
        } catch (Exception e) {
            Logger.logException(e);
            return Response.error(new VolleyError("Could not load gallery"));
        }
    }
}
