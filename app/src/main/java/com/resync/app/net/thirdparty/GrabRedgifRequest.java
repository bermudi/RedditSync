package com.resync.app.net.thirdparty;

import com.android.volley.Request;
import com.android.volley.Response;

/**
 * Resolves a RedGifs link to a playable MP4 URL through RedGifs' v2 API (see
 * {@link RedgifsGifRequest}). The public embedding page this used to scrape (/ifr/&lt;id&gt;)
 * now answers 404.
 */
public class GrabRedgifRequest extends RedgifsGifRequest {

    private final RedgifRequestWrapper wrapper;

    public GrabRedgifRequest(RedgifRequestWrapper redgifRequestWrapper) {
        this(redgifRequestWrapper, false);
    }

    private GrabRedgifRequest(RedgifRequestWrapper redgifRequestWrapper, boolean isRetry) {
        super(redgifRequestWrapper.getGifUrl(), redgifRequestWrapper.isForceHd(), isRetry, redgifRequestWrapper.getErrorListener());
        this.wrapper = redgifRequestWrapper;
    }

    @Override // com.resync.app.net.thirdparty.RedgifsGifRequest
    protected Request<String> retryClone() {
        return new GrabRedgifRequest(this.wrapper, true);
    }

    @Override // com.android.volley.Request
    public void deliverResponse(String str) {
        this.wrapper.deliverResponse(str);
    }
}
