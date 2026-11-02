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
import com.resync.app.model.Award;
import com.resync.app.util.Logger;
import com.resync.app.util.WebsafeUtils;
import com.resync.app.net.RedditRequest;

/* JADX INFO: renamed from: y8.c */
/* JADX INFO: compiled from: OAuthAwardsRequest.java */
/* JADX INFO: loaded from: classes.dex */
public class OAuthAwardsRequest extends RedditRequest<Award[]> {

    private final boolean isComment;

    public OAuthAwardsRequest(String typePrefix, String id, String linkId, Response.Listener<Award[]> listener, Response.ErrorListener errorListener) {
        super(RedditApplication.getAppContext(), 0, buildUrl(typePrefix, id, linkId), listener, errorListener);
        this.isComment = "t1_".equals(typePrefix);
        setShouldCache(true);
    }

    public static String buildUrl(String typePrefix, String id, String linkId) {
        // Reddit no longer answers /api/info for third-party clients, so the awards are read from
        // the comments thread instead, which carries the same object with all_awardings: for a
        // post the thread's first listing is the post; for a comment, /comments/{link}/_/{comment}
        // focuses the thread on that comment, which is the first child of the second listing.
        if ("t1_".equals(typePrefix)) {
            String bareLinkId = StringUtils.removeStart(linkId, "t3_");
            return "https://oauth.reddit.com/comments/" + bareLinkId + "/_/" + id + ".json?limit=1&context=0";
        }
        return "https://oauth.reddit.com/comments/" + id + ".json?limit=1";
    }

    @Override // com.android.volley.Request
    public Response<Award[]> parseNetworkResponse(NetworkResponse networkResponse) {
        try {
            JSONArray listings = new JSONArray(new String(networkResponse.data));
            int listingIndex = this.isComment ? 1 : 0;
            JSONArray children = listings.getJSONObject(listingIndex).getJSONObject("data").getJSONArray("children");
            if (children.length() == 0) {
                return Response.error(new VolleyError("Could not load awards"));
            }
            JSONArray jSONArray = children.getJSONObject(0).getJSONObject("data").getJSONArray("all_awardings");
            int length = jSONArray.length();
            Award[] awardArr = new Award[length];
            for (int index = 0; index < length; index++) {
                JSONObject jSONObject = jSONArray.getJSONObject(index);
                awardArr[index] = new Award();
                awardArr[index].count = jSONObject.getInt("count");
                awardArr[index].name = jSONObject.getString("name");
                awardArr[index].description = jSONObject.getString("description");
                awardArr[index].iconUrl = jSONObject.getString("icon_url");
                JSONArray resizedIcons = jSONObject.getJSONArray("resized_icons");
                awardArr[index].resizedIconUrl = WebsafeUtils.unescapeHtml(resizedIcons.getJSONObject(resizedIcons.length() - 1).getString("url"));
            }
            return Response.success(awardArr, CustomHttpHeaderParser.parseCacheEntry(networkResponse, DateUtils.MILLIS_PER_HOUR));
        } catch (Exception e) {
            Logger.logException(e);
            return Response.error(new VolleyError("Could not load awards"));
        }
    }
}
