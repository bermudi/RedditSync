package com.resync.app.ui.activities.media;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.ViewPager;
import butterknife.BindView;
import com.android.volley.Cache;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.resync.app.R;
import com.resync.app.RedditApplication;
import com.resync.app.singleton.SettingsSingleton;
import com.resync.app.ui.fragments.ImageViewerFragment;
import com.resync.app.ui.views.hacky.HackyViewPager;
import com.squareup.otto.Subscribe;
import com.resync.app.helpers.ui.StaticToast;
import com.resync.app.fragments.AdFragment;
import com.resync.app.fragments.ImageGridFragment;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import com.resync.app.media.imgur.ImgurImageHelper;
import com.resync.app.model.media.CommaGallery;
import com.resync.app.events.WindowAlphaEvent;
import com.resync.app.premium.SyncIapHelper;
import com.resync.app.net.OAuthRequestManager;
import com.resync.app.model.Gallery;
import com.resync.app.events.GridItemSelected;
import com.resync.app.net.thirdparty.EroshareGalleryRequest;
import com.resync.app.net.thirdparty.TumblrRequest;
import com.resync.app.net.thirdparty.ImgurGalleryHelper;
import com.resync.app.net.thirdparty.ImgurGalleryRequest;
import com.resync.app.net.thirdparty.ImgurSingleImageRequest;
import com.resync.app.dialogs.FragmentUtils;
import com.resync.app.dialogs.DownloadAlbumAlertDialogBottomSheet;
import com.resync.app.util.GalleryPositions;
import com.resync.app.util.Logger;
import com.resync.app.util.TextUtils;
import com.resync.app.util.links.LinkTypeHelper;
import com.resync.app.net.oauth.OAuthGalleryRequest;

/* JADX INFO: loaded from: classes.dex */
public class MultiImageActivity extends AbstractImageActivity {

    private ImagePagerAdapter pagerAdapter;
    private String[] urls;
    private String[] captions;

    private String[] thumbnails;

    @BindView(R.id.fragment_multi_image_progress)
    CircularProgressIndicator mAlbumProgressBar;

    @BindView(R.id.fragment_multi_image_pager)
    public HackyViewPager mViewPager;
    private int startPosition;
    private boolean scrolling;

    private SparseArray<Fragment> fragments = new SparseArray<>();

    class TumblrGalleryListener implements Response.Listener<Gallery> {
        TumblrGalleryListener() {
        }

        @Override // com.android.volley.Response.Listener
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public void onResponse(Gallery gallery) {
            if (MultiImageActivity.this.getActivity() == null) {
                return;
            }
            if (gallery == null) {
                MultiImageActivity.this.showLoadError();
            } else {
                MultiImageActivity.this.applyGallery(gallery);
                MultiImageActivity.this.setupPager();
            }
        }
    }

    class TumblrGalleryErrorListener implements Response.ErrorListener {
        TumblrGalleryErrorListener() {
        }

        @Override // com.android.volley.Response.ErrorListener
        public void onErrorResponse(VolleyError volleyError) {
            if (MultiImageActivity.this.getActivity() == null) {
                return;
            }
            com.resync.app.util.links.LinkHandler.openExternalOrInternal(MultiImageActivity.this.getActivity(), MultiImageActivity.this.getContentUrl());
            MultiImageActivity.this.finish();
        }
    }

    class EroshareGalleryListener implements Response.Listener<Gallery> {
        EroshareGalleryListener() {
        }

        @Override // com.android.volley.Response.Listener
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public void onResponse(Gallery gallery) {
            if (MultiImageActivity.this.getActivity() == null) {
                return;
            }
            if (gallery == null) {
                MultiImageActivity.this.showLoadError();
            } else {
                MultiImageActivity.this.applyGallery(gallery);
                MultiImageActivity.this.setupPager();
            }
        }
    }

    class EroshareGalleryErrorListener implements Response.ErrorListener {

        class SingleImageListener implements Response.Listener<Gallery> {
            SingleImageListener() {
            }

            @Override // com.android.volley.Response.Listener
            /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
            public void onResponse(Gallery gallery) {
                if (MultiImageActivity.this.getActivity() == null) {
                    return;
                }
                if (gallery == null) {
                    MultiImageActivity.this.showLoadError();
                } else {
                    MultiImageActivity.this.applyGallery(gallery);
                    MultiImageActivity.this.setupPager();
                }
            }
        }

        class SingleImageErrorListener implements Response.ErrorListener {
            SingleImageErrorListener() {
            }

            @Override // com.android.volley.Response.ErrorListener
            public void onErrorResponse(VolleyError volleyError) {
                if (MultiImageActivity.this.getActivity() == null) {
                    return;
                }
                MultiImageActivity.this.showLoadError();
            }
        }

        EroshareGalleryErrorListener() {
        }

        @Override // com.android.volley.Response.ErrorListener
        public void onErrorResponse(VolleyError volleyError) {
            if (MultiImageActivity.this.getActivity() == null) {
                return;
            }
            if (volleyError instanceof ImgurGalleryRequest.AlbumNotFoundError) {
                RedditApplication.thirdPartyRequestQueue.add(new ImgurSingleImageRequest(MultiImageActivity.this.getContentUrl(), new SingleImageListener(), new SingleImageErrorListener()));
            } else {
                MultiImageActivity.this.showLoadError();
            }
        }
    }

    class OAuthGalleryListener implements Response.Listener<Gallery> {
        OAuthGalleryListener() {
        }

        @Override // com.android.volley.Response.Listener
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public void onResponse(Gallery gallery) {
            if (MultiImageActivity.this.getActivity() == null) {
                return;
            }
            if (gallery == null) {
                MultiImageActivity.this.showErrorAndFinish("This gallery had no images");
            } else {
                MultiImageActivity.this.applyGallery(gallery);
                MultiImageActivity.this.setupPager();
            }
        }
    }

    class OAuthGalleryErrorListener implements Response.ErrorListener {
        OAuthGalleryErrorListener() {
        }

        @Override // com.android.volley.Response.ErrorListener
        public void onErrorResponse(VolleyError volleyError) {
            if (MultiImageActivity.this.getActivity() == null) {
                return;
            }
            // Name the actual failure (HTTP status + body head, or the parse exception) so a
            // broken album can be diagnosed from the toast / logcat instead of a fixed string.
            String reason = null;
            if (volleyError != null && volleyError.networkResponse != null) {
                String body = new String(volleyError.networkResponse.data);
                reason = "HTTP " + volleyError.networkResponse.statusCode + ": "
                        + body.substring(0, Math.min(100, body.length()));
            } else if (volleyError != null && volleyError.getMessage() != null) {
                reason = volleyError.getMessage();
            }
            android.util.Log.i("MultiImageActivity", "gallery load error: " + reason);
            MultiImageActivity.this.showErrorAndFinish(reason == null ? "Error loading album" : "Error loading album: " + reason);
        }
    }

    class ImgurGalleryListener implements Response.Listener<Gallery> {
        ImgurGalleryListener() {
        }

        @Override // com.android.volley.Response.Listener
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public void onResponse(Gallery gallery) {
            if (MultiImageActivity.this.getActivity() == null) {
                return;
            }
            if (gallery == null) {
                MultiImageActivity.this.openInBrowserAndFinish();
            } else {
                MultiImageActivity.this.applyGallery(gallery);
                MultiImageActivity.this.setupPager();
            }
        }
    }

    class ImgurGalleryErrorListener implements Response.ErrorListener {

        class SingleImageListener implements Response.Listener<Gallery> {
            SingleImageListener() {
            }

            @Override // com.android.volley.Response.Listener
            /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
            public void onResponse(Gallery gallery) {
                if (MultiImageActivity.this.getActivity() == null) {
                    return;
                }
                if (gallery == null) {
                    MultiImageActivity.this.openInBrowserAndFinish();
                } else {
                    MultiImageActivity.this.applyGallery(gallery);
                    MultiImageActivity.this.setupPager();
                }
            }
        }

        class SingleImageErrorListener implements Response.ErrorListener {
            SingleImageErrorListener() {
            }

            @Override // com.android.volley.Response.ErrorListener
            public void onErrorResponse(VolleyError volleyError) {
                if (MultiImageActivity.this.getActivity() == null) {
                    return;
                }
                MultiImageActivity.this.openInBrowserAndFinish();
            }
        }

        ImgurGalleryErrorListener() {
        }

        @Override // com.android.volley.Response.ErrorListener
        public void onErrorResponse(VolleyError volleyError) {
            if (MultiImageActivity.this.getActivity() == null) {
                return;
            }
            if (volleyError instanceof ImgurGalleryRequest.AlbumNotFoundError) {
                RedditApplication.thirdPartyRequestQueue.add(new ImgurSingleImageRequest(MultiImageActivity.this.getContentUrl(), new SingleImageListener(), new SingleImageErrorListener()));
            } else {
                MultiImageActivity.this.openInBrowserAndFinish();
            }
        }
    }

    class PageChangeListener implements ViewPager.OnPageChangeListener {

        private int previousPage = -1;
        private int currentPage = 0;

        PageChangeListener() {
        }

        @Override // androidx.viewpager.widget.ViewPager.OnPageChangeListener
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override // androidx.viewpager.widget.ViewPager.OnPageChangeListener
        public void onPageScrollStateChanged(int state) {
            MultiImageActivity.this.scrolling = state != 0;
            if (state == 1) {
                Fragment fragment = (Fragment) MultiImageActivity.this.fragments.get(MultiImageActivity.this.position);
                if (fragment instanceof ImageViewerFragment) {
                    ((ImageViewerFragment) fragment).pauseVideo();
                }
            } else if (state == 0) {
                Fragment fragment2 = (Fragment) MultiImageActivity.this.fragments.get(MultiImageActivity.this.position);
                if (fragment2 instanceof ImageViewerFragment) {
                    ((ImageViewerFragment) fragment2).resumeVideo();
                }
            }
            if (state == 0) {
                int currentPage = this.currentPage;
                if (currentPage >= 0 && currentPage < MultiImageActivity.this.urls.length && MultiImageActivity.this.urls[this.currentPage].equals("ad")) {
                    Fragment adFragment = MultiImageActivity.this.getSupportFragmentManager().findFragmentByTag(MultiImageActivity.this.fragmentTag(this.currentPage));
                    if (adFragment != null && (adFragment instanceof AdFragment)) {
                        ((AdFragment) adFragment).onAdShown();
                    }
                }
                int previousPage = this.previousPage;
                if (previousPage < 0 || previousPage >= MultiImageActivity.this.urls.length || !MultiImageActivity.this.urls[this.previousPage].equals("ad")) {
                    return;
                }
                Fragment previousAdFragment = MultiImageActivity.this.getSupportFragmentManager().findFragmentByTag(MultiImageActivity.this.fragmentTag(this.previousPage));
                if (previousAdFragment == null || !(previousAdFragment instanceof AdFragment)) {
                    return;
                }
                ((AdFragment) previousAdFragment).onAdHidden();
            }
        }

        @Override // androidx.viewpager.widget.ViewPager.OnPageChangeListener
        public void onPageSelected(int position) {
            this.previousPage = this.currentPage;
            this.currentPage = position;
            MultiImageActivity.this.setPosition(position);
            MultiImageActivity.this.invalidateOptionsMenu();
        }
    }

    class ImagePagerAdapter extends FragmentStatePagerAdapter {
        public ImagePagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override // androidx.fragment.app.FragmentStatePagerAdapter, androidx.viewpager.widget.PagerAdapter
        public void destroyItem(ViewGroup viewGroup, int position, Object obj) {
            MultiImageActivity.this.fragments.remove(position);
            Logger.verbose("DESTROY: " + position);
            super.destroyItem(viewGroup, position, obj);
        }

        @Override // androidx.viewpager.widget.PagerAdapter
        public int getCount() {
            return (MultiImageActivity.this.thumbnails == null || MultiImageActivity.this.urls.length <= 1) ? MultiImageActivity.this.urls.length : MultiImageActivity.this.urls.length + 1;
        }

        @Override // androidx.fragment.app.FragmentStatePagerAdapter, androidx.viewpager.widget.PagerAdapter
        public void restoreState(Parcelable parcelable, ClassLoader classLoader) {
            super.restoreState(parcelable, classLoader);
        }

        @Override // androidx.fragment.app.FragmentStatePagerAdapter, androidx.viewpager.widget.PagerAdapter
        public Parcelable saveState() {
            return null;
        }

        @Override // androidx.fragment.app.FragmentStatePagerAdapter
        public Fragment getItem(int position) {
            Fragment pageFragment;
            if (position >= MultiImageActivity.this.urls.length) {
                pageFragment = ImageGridFragment.newInstance(MultiImageActivity.this.thumbnails);
            } else if (MultiImageActivity.this.urls[position].equals("ad")) {
                pageFragment = AdFragment.newInstance();
            } else {
                Logger.verbose("CREATING: " + MultiImageActivity.this.position + StringUtils.SPACE + position);
                pageFragment = ImageViewerFragment.newInstance(MultiImageActivity.this.urls.length, position, MultiImageActivity.this.getSubreddit(), MultiImageActivity.this.urls[position], MultiImageActivity.this.getTitle2(), MultiImageActivity.this.captions[position], MultiImageActivity.this.getCommentsUrl(), 0, MultiImageActivity.this.position != position);
            }
            MultiImageActivity.this.fragments.put(position, pageFragment);
            return pageFragment;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void openInBrowserAndFinish() {
        StaticToast.showWithContext("Error loading album, opening in browser", getContext());
        if (SettingsSingleton.getInstance().getCurrentSettings().defaultBrowser == 0) {
            com.resync.app.util.links.LinkHandler.openCustomTab(getActivity(), getContentUrl());
        } else {
            com.resync.app.util.links.LinkHandler.openWebView(getActivity(), getContentUrl());
        }
        finish();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void applyGallery(Gallery gallery) {
        Logger.verbose("MultiImageActivity", "Using cached position: " + gallery.position);
        this.startPosition = gallery.position;
        String[] strArr = gallery.urls;
        this.urls = strArr;
        this.captions = gallery.captions;
        this.thumbnails = gallery.thumbnails;
        if (strArr.length > 3) {
            SyncIapHelper.areAdsEnabled(getActivity());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String fragmentTag(int position) {
        return "android:switcher:R.id.fragment_multi_image_pager:" + position;
    }

    private boolean hasUrls() {
        String[] strArr = this.urls;
        return strArr != null && strArr.length > 0;
    }

    private void loadGallery() {
        if (getIntent().hasExtra("urls_content")) {
            this.urls = getIntent().getStringArrayExtra("urls_content");
            this.captions = getIntent().getStringArrayExtra("descriptions");
            this.startPosition = getIntent().getIntExtra("position", 0);
            setupPager();
        }
        if (TextUtils.isEmpty(getContentUrl())) {
            return;
        }
        if (LinkTypeHelper.isTumblr(getContentUrl())) {
            RedditApplication.thirdPartyRequestQueue.add(new TumblrRequest(getContentUrl(), new TumblrGalleryListener(), new TumblrGalleryErrorListener()));
            return;
        }
        if (getContentUrl().contains("eroshare.com")) {
            RedditApplication.thirdPartyRequestQueue.add(new EroshareGalleryRequest(getContentUrl(), new EroshareGalleryListener(), new EroshareGalleryErrorListener()));
            return;
        }
        if (!ImgurImageHelper.isMultiImage(getContentUrl())) {
            if (LinkTypeHelper.isGallery(getContentUrl())) {
                OAuthRequestManager.sendWithAppContext(new OAuthGalleryRequest(getContentUrl(), new OAuthGalleryListener(), new OAuthGalleryErrorListener()));
                return;
            } else {
                ImgurGalleryHelper.load(getContentUrl(), new ImgurGalleryListener(), new ImgurGalleryErrorListener());
                return;
            }
        }
        String[] imageIds = ImgurImageHelper.getImageIds(getContentUrl());
        Gallery gallery = new Gallery(imageIds.length);
        CommaGallery commaGallery = new CommaGallery(imageIds);
        for (int i = 0; i < commaGallery.size(); i++) {
            gallery.urls[i] = commaGallery.getImage(i).getUrl();
            gallery.thumbnails[i] = commaGallery.getImage(i).getBigThumbnailUrl();
        }
        applyGallery(gallery);
        setupPager();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setupPager() {
        if (getSupportFragmentManager() == null || isFinishing() || isClosing() || getSupportFragmentManager() == null) {
            return;
        }
        setPosition(this.startPosition);
        invalidateOptionsMenu();
        this.mAlbumProgressBar.setVisibility(8);
        this.mViewPager.setCurrentItem(1);
        ImagePagerAdapter adapter = new ImagePagerAdapter(getSupportFragmentManager());
        this.pagerAdapter = adapter;
        this.mViewPager.setAdapter(adapter);
        this.mViewPager.setOnPageChangeListener(new PageChangeListener());
        this.mViewPager.setOffscreenPageLimit(this.startPosition);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showLoadError() {
        showErrorAndFinish("Error loading album");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showErrorAndFinish(String str) {
        StaticToast.showWithContext(str, getContext());
        finish();
    }

    @Override // com.resync.app.ui.activities.BaseActivity
    protected int getAdMode() {
        return 0;
    }

    @Override // com.resync.app.ui.activities.media.AbstractImageActivity, com.resync.app.ui.activities.BaseActivity, androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Logger.verbose("MultiImageActivity", "onCreated called");
        this.mRootView.setBackground(new ColorDrawable(-16777216));
        overridePendingTransition(R.anim.video_enter, R.anim.video_exit);
        this.mAlbumProgressBar.setIndicatorColor(-1);
        this.mAlbumProgressBar.setTrackColor(ColorUtils.setAlphaComponent(-1, 102));
        Logger.verbose("MultiImageActivity", "Loading gallery");
        loadGallery();
    }

    @Override // com.resync.app.ui.activities.media.AbstractImageActivity, androidx.activity.ComponentActivity, android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.findItem(R.id.menu_grid).setVisible(false);
        menu.findItem(R.id.menu_download).setVisible(false);
        if (hasUrls() && getPosition() < this.urls.length && this.urls.length > 1 && this.thumbnails != null) {
            menu.findItem(R.id.menu_download).setVisible(true);
            this.toolbar.setNavigationIcon(new ColorDrawable(0));
        } else if (hasUrls() && getPosition() >= this.urls.length) {
            menu.findItem(R.id.menu_download).setVisible(false);
            menu.findItem(R.id.menu_grid).setVisible(true);
        }
        com.resync.app.helpers.ui.MonetUtil.tintMenu(menu, -1);
        return true;
    }

    @Override // com.resync.app.ui.activities.media.AbstractImageActivity, com.resync.app.ui.activities.BaseActivity, androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    public void onDestroy() {
        super.onDestroy();
        Logger.verbose("MultiImageActivity", "Views: " + this.mViewPager.getChildCount());
        if (this.fragments.size() > 0) {
            if (!TextUtils.isEmpty(getContentUrl())) {
                if (getContentUrl().contains("imgur")) {
                    try {
                        // Remember where the album was left; the request reads it back on reopen.
                        GalleryPositions.save(ImgurGalleryRequest.buildUrl(getContentUrl()), this.mViewPager.getCurrentItem());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (LinkTypeHelper.isGallery(getContentUrl())) {
                    try {
                        GalleryPositions.save(OAuthGalleryRequest.buildUrl(getContentUrl()), this.mViewPager.getCurrentItem());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (getContentUrl().contains("eroshare")) {
                    try {
                        int currentItem = this.mViewPager.getCurrentItem();
                        Cache.Entry entry3 = RedditApplication.thirdPartyRequestQueue.getCache().get(EroshareGalleryRequest.buildUrl(getContentUrl()));
                        JSONObject jSONObject3 = new JSONObject(new String(entry3.data));
                        if (jSONObject3.has("position")) {
                            jSONObject3.remove("position");
                        }
                        jSONObject3.put("position", currentItem);
                        entry3.data = jSONObject3.toString().getBytes();
                        RedditApplication.thirdPartyRequestQueue.getCache().put(EroshareGalleryRequest.buildUrl(getContentUrl()), entry3);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            boolean fmAlive = !getSupportFragmentManager().isDestroyed();
            FragmentTransaction fragmentTransactionM = fmAlive ? getSupportFragmentManager().beginTransaction() : null;
            for (int size = this.fragments.size() - 1; size >= 0; size--) {
                Fragment fragment = this.fragments.get(size);
                if (fragment instanceof ImageViewerFragment) {
                    ((ImageViewerFragment) fragment).cleanup();
                }
                Logger.verbose("MultiImageActivity", "Removing: " + fragment);
                this.fragments.remove(size);
                if (fragment != null && fragmentTransactionM != null) {
                    fragmentTransactionM.remove(fragment);
                }
            }
            if (fragmentTransactionM != null) {
                fragmentTransactionM.commitAllowingStateLoss();
            }
        }
    }

    @Subscribe
    public void onGridItemSelected(GridItemSelected gridItemSelected) {
        this.mViewPager.setCurrentItem(gridItemSelected.position, false);
    }

    @Override // com.resync.app.ui.activities.BaseActivity, androidx.activity.ComponentActivity, android.app.Activity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        ImagePagerAdapter adapter;
        if (menuItem.getItemId() == R.id.menu_grid) {
            HackyViewPager hackyViewPager = this.mViewPager;
            if (hackyViewPager != null && (adapter = this.pagerAdapter) != null) {
                hackyViewPager.setCurrentItem(adapter.getCount(), false);
            }
            return true;
        }
        if (menuItem.getItemId() != R.id.menu_download) {
            return super.onOptionsItemSelected(menuItem);
        }
        if (this.mViewPager != null && this.pagerAdapter != null) {
            FragmentUtils.showWithArguments(DownloadAlbumAlertDialogBottomSheet.class, getSupportFragmentManager(), DownloadAlbumAlertDialogBottomSheet.buildArguments(getTitle2(), getSubreddit(), StringUtils.join(this.urls, ",")));
        }
        return true;
    }

    @Subscribe
    public void onWindowAlphaChanged(WindowAlphaEvent windowAlphaEvent) {
        RelativeLayout relativeLayout = this.mRootView;
        if (relativeLayout == null || relativeLayout.getBackground() == null) {
            return;
        }
        this.mRootView.getBackground().setAlpha(windowAlphaEvent.alpha);
    }

    @Override // com.resync.app.ui.activities.media.AbstractImageActivity, com.resync.app.ui.activities.BaseActivity
    protected void setupContentView() {
        setContentView(R.layout.activity_multiple_image);
        super.setupContentView();
    }

    public String getUrl(int position) {
        return this.urls[position];
    }
}
