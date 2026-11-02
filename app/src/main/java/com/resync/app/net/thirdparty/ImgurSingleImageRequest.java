package com.resync.app.net.thirdparty;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.resync.app.media.imgur.ImgurImageHelper;
import com.resync.app.model.media.Image;
import com.resync.app.model.Gallery;
import com.resync.app.util.Logger;

/* JADX INFO: renamed from: s8.d */
/* JADX INFO: compiled from: ImgurSingleImageRequest.java */
/* JADX INFO: loaded from: classes.dex */
public class ImgurSingleImageRequest extends Request<Gallery> {

    private final String imageId;
    private final Response.Listener<Gallery> responseListener;

    public ImgurSingleImageRequest(String str, Response.Listener<Gallery> listener, Response.ErrorListener errorListener) {
        // No Imgur API key (the API needs a Client-ID this build doesn't have). The ordinary
        // image URL is derived from the id, as sync-up does; the small square thumbnail is all
        // that is asked of the network, to tell a live image from a deleted one.
        super(0, buildUrl(str), errorListener);
        this.imageId = ImgurImageHelper.getId(ImgurImageHelper.normalizeUrl(str));
        this.responseListener = listener;
        setShouldCache(true);
        Logger.verbose(getUrl());
    }

    public static String buildUrl(String str) {
        return "https://i.imgur.com/" + ImgurImageHelper.getId(ImgurImageHelper.normalizeUrl(str)) + "s.jpg";
    }

    @Override // com.android.volley.Request
    /* renamed from: b, reason: merged with bridge method [inline-methods] */
    public void deliverResponse(Gallery gallery) {
        this.responseListener.onResponse(gallery);
    }

    @Override // com.android.volley.Request
    public Response<Gallery> parseNetworkResponse(NetworkResponse networkResponse) {
        try {
            Gallery gallery = new Gallery(1);
            Image image = new Image("https://i.imgur.com/" + this.imageId + ".jpg");
            gallery.urls[0] = image.getUrl();
            gallery.thumbnails[0] = image.getBigThumbnailUrl();
            gallery.position = 0;
            return Response.success(gallery, HttpHeaderParser.parseCacheHeaders(networkResponse));
        } catch (Exception e) {
            Logger.logException(e);
            return Response.error(new VolleyError("Could not load image"));
        }
    }
}
