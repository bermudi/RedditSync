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
import com.resync.app.util.GalleryPositions;
import com.resync.app.util.Logger;
import com.resync.app.util.WebsafeUtils;
import com.resync.app.net.RedditRequest;
import com.resync.app.util.links.LinkHandler;

/* JADX INFO: renamed from: y8.q */
/* JADX INFO: compiled from: OAuthGalleryRequest.java */
/* JADX INFO: loaded from: classes.dex */
public class OAuthGalleryRequest extends RedditRequest<Gallery> {
    private final String contentUrl;

    public OAuthGalleryRequest(String str, Response.Listener<Gallery> listener, Response.ErrorListener errorListener) {
        super(RedditApplication.getAppContext(), 0, buildUrl(str), listener, errorListener);
        this.contentUrl = str;
        setShouldCache(true);
    }

    public static String buildUrl(String str) {
        // Reddit no longer answers /api/info for third-party clients (it serves the block page
        // instead), which is what this request used to resolve a gallery post. /comments/{id}
        // publishes the same post object including gallery_data and media_metadata, so the
        // request goes there and the reply is unwrapped from its two-listing thread shape below.
        return "https://oauth.reddit.com/comments/" + LinkHandler.getCommentsId(str.replace("?is_gallery=true", "")) + ".json?limit=1";
    }

    /**
     * Edge-wall fallback: the thread JSON endpoints are 403-walled for every client now, but the
     * server-rendered thread page carries the gallery's full-res i.redd.it images in the
     * shreddit-post DOM. The JS rebuilds the exact [postListing] shape parseNetworkResponse
     * expects (media_metadata + gallery_data), synthesized from those image elements.
     */
    @Override
    protected String[] webProxySpec() {
        String id = LinkHandler.getCommentsId(this.contentUrl.replace("?is_gallery=true", ""));
        String page = "https://www.reddit.com/comments/" + id + "/";
        String js =
            "var p=document.querySelector('shreddit-post');"
          + "if(!p){return '[]';}"
          + "var urls=[],seen={};"
          + "p.querySelectorAll('img,a').forEach(function(e){"
          + "  var u=(e.tagName==='IMG')?(e.getAttribute('src')||''):(e.getAttribute('href')||'');"
          + "  var m=u&&u.match(/i\\.redd\\.it\\/([a-z0-9]+)\\.(jpe?g|png|gif|webp)/i);"
          + "  if(m&&!seen[m[1]]){seen[m[1]]=1;urls.push('https://i.redd.it/'+m[1]+'.'+m[2].toLowerCase());}"
          + "});"
          + "var mm={},items=[];"
          + "urls.forEach(function(u,i){var id='m'+i;items.push({media_id:id,caption:''});"
          + "  mm[id]={m:'image',s:{u:u},p:[{u:u,y:0,x:0}]};});"
          + "return JSON.stringify([{data:{children:[{data:{is_gallery:true,"
          + "  gallery_data:{items:items},media_metadata:mm}}}]}}]);";
        return new String[]{page, "shreddit-post", js};
    }

    @Override // com.android.volley.Request
    public Response<Gallery> parseNetworkResponse(NetworkResponse networkResponse) {
        String str = new String(networkResponse.data);
        try {
            // A thread replies with [postListing, commentsListing]; the post is first.
            JSONObject jSONObject = new JSONArray(str).getJSONObject(0).getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data");
            JSONObject jSONObject2 = jSONObject.optJSONObject("media_metadata");
            JSONObject galleryData = jSONObject.optJSONObject("gallery_data");
            JSONArray jSONArray = galleryData == null ? null : galleryData.optJSONArray("items");
            if (jSONObject2 == null || jSONArray == null || jSONArray.length() == 0) {
                return Response.error(new VolleyError("post has no gallery (is_gallery="
                        + jSONObject.optBoolean("is_gallery") + ", crosspost="
                        + jSONObject.has("crosspost_parent_list") + ")"));
            }
            Gallery gallery = new Gallery(jSONArray.length());
            for (int index = 0; index < jSONArray.length(); index++) {
                // A deleted image leaves its id in gallery_data but absent from media_metadata;
                // reddit's web client shows a gap there, so blank the slot instead of failing
                // the whole album.
                JSONObject meta = jSONObject2.optJSONObject(jSONArray.getJSONObject(index).optString("media_id"));
                JSONObject s = meta == null ? null : meta.optJSONObject("s");
                if (s != null) {
                    if (s.has("mp4")) {
                        gallery.urls[index] = WebsafeUtils.unescapeHtml(s.getString("mp4"));
                    } else if (s.has("u")) {
                        gallery.urls[index] = WebsafeUtils.unescapeHtml(s.getString("u"));
                    } else if (s.has("gif")) {
                        gallery.urls[index] = WebsafeUtils.unescapeHtml(s.getString("gif"));
                    } else {
                        gallery.urls[index] = "";
                    }
                    try {
                        gallery.thumbnails[index] = WebsafeUtils.unescapeHtml(meta.getJSONArray("p").getJSONObject(0).getString("u"));
                    } catch (Exception unused) {
                        gallery.thumbnails[index] = gallery.urls[index];
                    }
                } else {
                    gallery.urls[index] = "";
                    gallery.thumbnails[index] = "";
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
            }
            // Where the viewer last left this album (used to ride in the cached reply body).
            gallery.position = GalleryPositions.get(getUrl());
            return Response.success(gallery, CustomHttpHeaderParser.parseCacheEntry(networkResponse, DateUtils.MILLIS_PER_HOUR));
        } catch (Exception e) {
            android.util.Log.i("OAuthGalleryRequest",
                    "gallery parse failed: " + e + " body=" + head(str));
            return Response.error(new VolleyError("parse failed: " + e + "; body=" + head(str)));
        }
    }

    private static String head(String str) {
        return str.substring(0, Math.min(100, str.length())).replace('\n', ' ');
    }
}
