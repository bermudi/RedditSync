package com.resync.app.presenters;

import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentManager;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.bumptech.glide.RedditHelper;
import com.resync.app.R;
import com.resync.app.RedditApplication;
import com.resync.app.provider.RedditProvider;
import com.resync.app.singleton.AccountSingleton;
import com.resync.app.singleton.SettingsSingleton;
import com.resync.app.ui.activities.BaseActivity;
import com.resync.app.ui.activities.CommentsActivity;
import com.resync.app.ui.fragment_dialogs.EditFragment;
import com.resync.app.ui.fragment_dialogs.ImagePeekDialogFragment;
import com.resync.app.ui.fragment_dialogs.ParentCommentFragment;
import com.resync.app.helpers.ui.StaticToast;
import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;
import com.resync.app.events.CommentPostUpdated;
import com.resync.app.events.SearchCommentSelected;
import com.resync.app.events.StartAutoRefreshCountdownEvent;
import com.resync.app.singletons.BusSingleton;
import com.resync.app.net.OAuthRequestManager;
import com.resync.app.managers.HideManager;
import com.resync.app.managers.PostsManager;
import com.resync.app.managers.SaveManager;
import com.resync.app.events.ScrollToPosition;
import com.resync.app.dialogs.FragmentUtils;
import com.resync.app.helpers.ConnectivityHelper;
import com.resync.app.helpers.ContextHelper;
import com.resync.app.helpers.CrashlyticsHelper;
import com.resync.app.helpers.DatabaseIdHelper;
import com.resync.app.helpers.ModHelper;
import com.resync.app.helpers.PostMoreHelper;
import com.resync.app.helpers.PostsHelper;
import com.resync.app.helpers.RecentHelper;
import com.resync.app.dialogs.DistinguishCommentAlertDialogBottomSheet;
import com.resync.app.dialogs.SharePostDialogBottomSheet;
import com.resync.app.dialogs.UndistinguishCommentAlertDialogBottomSheet;
import com.resync.app.helpers.CommentsStateHelper;
import com.resync.app.util.Logger;
import com.resync.app.util.TestUtils;
import com.resync.app.model.Post;
import com.resync.app.util.links.LinkHelper;
import com.resync.app.net.oauth.OAuthCommentsMoreRequest;
import com.resync.app.net.oauth.OAuthCommentsRequest;
import com.resync.app.presenters.CommentsPresenter;
import com.resync.app.util.CommentNavigation;
import com.resync.app.presenters.CommentsView;

/* JADX INFO: compiled from: CommentsPresenterImpl.java */
/* JADX INFO: loaded from: classes.dex */
public class CommentsPresenterImpl implements CommentsPresenter {

    Context context;
    CommentsView view;
    String postId;
    String commentId;

    String sort;

    String subreddit;
    boolean np;
    boolean continueInstance;

    boolean search;
    String userFilter;
    Post post;

    Post selectedComment;

    Post highlightedComment;
    int newCommentCount;
    String keywordFilter;
    private CommentNavigation navigation = new CommentNavigation();

    private CursorLoader cursorLoader;

    private Loader.OnLoadCompleteListener loaderListener;

    private Cursor cursor;
    OAuthCommentsRequest commentsRequest;

    OAuthCommentsMoreRequest moreRequest;
    long lastLinkClickTime;

    /* JADX INFO: compiled from: CommentsPresenterImpl.java */
    class RefreshLoadListener implements Loader.OnLoadCompleteListener<Cursor> {

        final /* synthetic */ boolean refresh;

        RefreshLoadListener(boolean z) {
            this.refresh = z;
        }

        @Override // android.content.Loader.OnLoadCompleteListener
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
            loader.unregisterListener(this);
            loader.abandon();
            boolean z = (cursor != null ? cursor.getCount() : 0) >= 1;
            if (cursor != null) {
                cursor.close();
            }
            if (this.refresh || !TextUtils.isEmpty(CommentsPresenterImpl.this.commentId)) {
                Logger.verbose("CommentsPresenterImpl", "Refreshing so loading from the network");
                CommentsPresenterImpl.this.loadFromNetwork(true, z);
                return;
            }
            if (z && CommentsStateHelper.hasState(CommentsPresenterImpl.this.postId) && TextUtils.isEmpty(CommentsPresenterImpl.this.commentId)) {
                CommentsPresenterImpl presenter = CommentsPresenterImpl.this;
                presenter.view.saveCommentState(CommentsStateHelper.popState(presenter.postId));
                CommentsPresenterImpl.this.loadCursor();
                return;
            }
            if (ConnectivityHelper.isConnected() && !SettingsSingleton.getSettings().cachedComments) {
                Logger.verbose("CommentsPresenterImpl", "Caching disabled so loading straight from the network");
                CommentsPresenterImpl.this.loadFromNetwork(true, z);
                return;
            }
            if (!z) {
                Logger.verbose("CommentsPresenterImpl", "No comments found so loading from the network");
                CommentsPresenterImpl.this.loadFromNetwork(true, z);
                return;
            }
            Logger.verbose("CommentsPresenterImpl", "Marking previous comments as viewed");
            ContentResolver contentResolver = CommentsPresenterImpl.this.context.getContentResolver();
            Uri uri = RedditProvider.URI_COMMENTS_VIEWED;
            CommentsPresenterImpl presenter = CommentsPresenterImpl.this;
            contentResolver.update(uri, null, DatabaseIdHelper.buildId(presenter.postId, presenter.commentId), null);
            Logger.verbose("CommentsPresenterImpl", "Comments found so loading from the db");
            CommentsPresenterImpl.this.loadCursor();
        }
    }

    /* JADX INFO: compiled from: CommentsPresenterImpl.java */
    class CommentsResponseListener implements Response.Listener<Void> {

        final /* synthetic */ boolean wasCached;

        CommentsResponseListener(boolean z) {
            this.wasCached = z;
        }

        @Override // com.android.volley.Response.Listener
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public void onResponse(Void response) {
            CommentsPresenterImpl.this.commentsRequest = null;
            Logger.verbose("CommentsPresenterImpl", "Live posts loaded!");
            if (CommentsPresenterImpl.this.cursorLoader == null && CommentsPresenterImpl.this.loaderListener == null) {
                if (!this.wasCached) {
                    Logger.verbose("CommentsPresenterImpl", "Mark all existing comments as not new");
                    ContentResolver contentResolver = CommentsPresenterImpl.this.context.getContentResolver();
                    Uri uri = RedditProvider.URI_COMMENTS_VIEWED;
                    CommentsPresenterImpl presenter = CommentsPresenterImpl.this;
                    contentResolver.update(uri, null, DatabaseIdHelper.buildId(presenter.postId, presenter.commentId), null);
                }
                CommentsPresenterImpl.this.loadCursor();
            }
        }
    }

    /* JADX INFO: compiled from: CommentsPresenterImpl.java */
    class CommentsErrorListener implements Response.ErrorListener {
        CommentsErrorListener() {
        }

        @Override // com.android.volley.Response.ErrorListener
        public void onErrorResponse(VolleyError volleyError) {
            OAuthCommentsRequest oAuthCommentsRequest = CommentsPresenterImpl.this.commentsRequest;
            if (oAuthCommentsRequest == null || !oAuthCommentsRequest.isCanceled()) {
                CommentsPresenterImpl presenter = CommentsPresenterImpl.this;
                presenter.commentsRequest = null;
                presenter.onLoadError(volleyError);
            }
        }
    }

    /* JADX INFO: renamed from: com.resync.app.presenters.CommentsPresenterImpl$d, reason: collision with other inner class name */
    /* JADX INFO: compiled from: CommentsPresenterImpl.java */
    class LoaderOnLoadCompleteListenerC0139d implements Loader.OnLoadCompleteListener<Cursor> {
        LoaderOnLoadCompleteListenerC0139d() {
        }

        @Override // android.content.Loader.OnLoadCompleteListener
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
            CommentsPresenterImpl.this.onCursorLoaded(cursor);
        }
    }

    /* JADX INFO: compiled from: CommentsPresenterImpl.java */
    class MoreCommentsResponseListener implements Response.Listener<Void> {
        MoreCommentsResponseListener() {
        }

        @Override // com.android.volley.Response.Listener
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public void onResponse(Void response) {
            CommentsPresenterImpl.this.moreRequest = null;
        }
    }

    /* JADX INFO: compiled from: CommentsPresenterImpl.java */
    class MoreCommentsErrorListener implements Response.ErrorListener {
        MoreCommentsErrorListener() {
        }

        @Override // com.android.volley.Response.ErrorListener
        public void onErrorResponse(VolleyError volleyError) {
            CommentsPresenterImpl presenter = CommentsPresenterImpl.this;
            presenter.moreRequest = null;
            StaticToast.showWithContext(presenter.context, "Error loading more comments");
            CommentsPresenterImpl.this.view.notifyDataSetChanged();
        }
    }

    /* JADX INFO: compiled from: CommentsPresenterImpl.java */
    class AutoRefreshResponseListener implements Response.Listener<Void> {
        AutoRefreshResponseListener() {
        }

        @Override // com.android.volley.Response.Listener
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public void onResponse(Void response) {
            CommentsPresenterImpl.this.commentsRequest = null;
            BusSingleton.getBus().post(new StartAutoRefreshCountdownEvent());
        }
    }

    /* JADX INFO: compiled from: CommentsPresenterImpl.java */
    class AutoRefreshErrorListener implements Response.ErrorListener {
        AutoRefreshErrorListener() {
        }

        @Override // com.android.volley.Response.ErrorListener
        public void onErrorResponse(VolleyError volleyError) {
            OAuthCommentsRequest oAuthCommentsRequest = CommentsPresenterImpl.this.commentsRequest;
            if (oAuthCommentsRequest == null || !oAuthCommentsRequest.isCanceled()) {
                CommentsPresenterImpl.this.commentsRequest = null;
                BusSingleton.getBus().post(new StartAutoRefreshCountdownEvent());
            }
        }
    }

    public CommentsPresenterImpl(Context context, Bundle bundle, CommentsView commentsView) {
        this.context = context;
        this.view = commentsView;
    }

    public static Bundle buildArgs(String str, String str2, String str3, String str4, boolean z) {
        Bundle bundle = new Bundle();
        bundle.putString("post_id", str);
        bundle.putString("comment_id", str2);
        bundle.putString("subreddit", str3);
        bundle.putString("access", str4);
        bundle.putBoolean("np", z);
        return bundle;
    }

    public static Bundle buildArgsFromPost(Post post, String str) {
        Bundle bundle = new Bundle();
        bundle.putSerializable("Post", post);
        if (post.getKind() == 1) {
            bundle.putString("post_id", post.getLinkId());
            bundle.putString("comment_id", post.getId());
        } else {
            bundle.putString("post_id", post.getId());
        }
        bundle.putString("subreddit", post.getSubreddit());
        bundle.putString("access", str);
        return bundle;
    }

    public static Bundle buildSearchArgs(Post post) {
        Bundle bundle = new Bundle();
        bundle.putSerializable("Post", post);
        bundle.putString("subreddit", post.getSubreddit());
        bundle.putString("post_id", post.getId());
        bundle.putBoolean("search", true);
        return bundle;
    }

    private void loadCommentsCursor() {
        if (this.cursorLoader != null) {
            return;
        }
        if (this.search && TextUtils.isEmpty(this.userFilter)) {
            return;
        }
        Logger.verbose("CommentsPresenterImpl", "Instructing the cursor to load data (X0)");
        CommentsView commentsView = this.view;
        if (commentsView != null) {
            commentsView.setSubredditTitle(this.subreddit);
            if (getPost() != null) {
                this.view.setSortLabel(this.sort, getPost().getSuggestedSort());
            } else {
                this.view.setSortLabel(this.sort, null);
            }
        }
        this.cursorLoader = PostsManager.createSearchLoader(this.context, DatabaseIdHelper.buildId(this.postId, this.commentId), this.search, this.userFilter);
        registerLoaderListener();
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public boolean isHighlighted(Post post) {
        return post.equals(this.highlightedComment);
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void viewParentComment(Post post) {
        Cursor cursor = this.cursor;
        if (cursor == null || cursor.isClosed() || post == null) {
            return;
        }
        int i = 0;
        if (SettingsSingleton.getSettings().commentsParentButton == 2) {
            int parentIndex = 0;
            while (i < this.cursor.getCount()) {
                this.cursor.moveToPosition(i);
                Cursor cursor2 = this.cursor;
                if (cursor2.getInt(cursor2.getColumnIndex("level")) == post.getLevel() - 1) {
                    parentIndex = i;
                }
                Cursor cursor3 = this.cursor;
                if (cursor3.getString(cursor3.getColumnIndex("_id")).equals(post.getId())) {
                    Post postZ = Post.fromCursorPosition(this.cursor, parentIndex);
                    if (postZ.getSyncType() == 11) {
                        ParentCommentFragment.newInstance(postZ).show(((BaseActivity) this.context).getSupportFragmentManager(), "ParentCommentFragment");
                        return;
                    } else {
                        StaticToast.showWithContext(this.context, "Parent post not in scope");
                        return;
                    }
                }
                i++;
            }
            return;
        }
        int rootIndex = 0;
        while (i < this.cursor.getCount()) {
            this.cursor.moveToPosition(i);
            Cursor cursor4 = this.cursor;
            if (cursor4.getInt(cursor4.getColumnIndex("level")) == 0) {
                rootIndex = i;
            }
            Cursor cursor5 = this.cursor;
            if (cursor5.getString(cursor5.getColumnIndex("_id")).equals(post.getId())) {
                Post postZ2 = Post.fromCursorPosition(this.cursor, rootIndex);
                if (postZ2.getSyncType() == 11) {
                    ParentCommentFragment.newInstance(postZ2).show(((BaseActivity) this.context).getSupportFragmentManager(), "ParentCommentFragment");
                    return;
                } else {
                    StaticToast.showWithContext(this.context, "Parent post not in scope");
                    return;
                }
            }
            i++;
        }
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public String getPostId() {
        return this.postId;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public String getEmptyMessage() {
        Cursor cursor = this.cursor;
        if (cursor == null || cursor.getCount() <= 1) {
            return "There are no comments";
        }
        return null;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void filterByUser(String str) {
        reset();
        this.userFilter = str;
        loadCommentsCursor();
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void replyToPost() {
        openEditFragment(getPost(), null);
    }

    @Override // com.resync.app.presenters.BaseCursorPresenter
    public void showPostMore(Post post) {
        PostMoreHelper.showMoreSheet(this.context, 7, post);
    }

    @Override // com.resync.app.presenters.BaseCursorPresenter
    public void showModOptions(Post post) {
        ModHelper.showModSheet(this.context, post);
    }

    @Override // com.resync.app.presenters.BasePresenter
    public void reset() {
        Loader.OnLoadCompleteListener onLoadCompleteListener;
        Logger.verbose("CommentsPresenterImpl", "Resetting the presenter");
        cancelAllRequests();
        CursorLoader cursorLoader = this.cursorLoader;
        if (cursorLoader != null && (onLoadCompleteListener = this.loaderListener) != null) {
            cursorLoader.unregisterListener(onLoadCompleteListener);
            this.cursorLoader = null;
            this.loaderListener = null;
        }
        resetAnimation();
        this.view.setCursor(null);
        closeCursor();
        this.navigation.clear();
        this.selectedComment = null;
        this.highlightedComment = null;
    }

    public boolean canOpenLink() {
        if (System.currentTimeMillis() - getLastLinkClickTime() <= 750) {
            return false;
        }
        setLastLinkClickTime(System.currentTimeMillis());
        return true;
    }

    public void cancelContinueRequest() {
        Logger.verbose("CommentsPresenterImpl", "Cancelling continue request");
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public boolean isLoadingMoreFor(Post post) {
        if (post == null || this.moreRequest == null) {
            return false;
        }
        return StringUtils.equalsIgnoreCase(post.getId(), this.moreRequest.getFullname());
    }

    public void cancelMoreRequest() {
        Logger.verbose("CommentsPresenterImpl", "Cancelling more request");
        OAuthCommentsMoreRequest oAuthCommentsMoreRequest = this.moreRequest;
        if (oAuthCommentsMoreRequest != null) {
            oAuthCommentsMoreRequest.cancel();
            this.moreRequest = null;
            Logger.verbose("CommentsPresenterImpl", "More request cancelled");
        }
    }

    public void cancelAllRequests() {
        if (this.commentsRequest != null) {
            Logger.verbose("CommentsPresenterImpl", "Cancelling comments request");
            this.commentsRequest.cancel();
        }
        cancelMoreRequest();
        cancelContinueRequest();
    }

    @Override // com.resync.app.presenters.BasePresenter
    public void refresh() {
        reset();
        load(true);
    }

    public void closeCursor() {
        Cursor cursor = this.cursor;
        if (cursor == null || cursor.isClosed()) {
            return;
        }
        Logger.verbose("CommentsPresenterImpl", "Closing cursor: " + this.cursor);
        this.cursor.close();
        setCursor(null);
    }

    public void collapseComment(Post post) {
        this.view.onCommentCollapsed(post);
        collapseChildren(post, true);
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public boolean isPostLocked() {
        Post post = this.post;
        return post != null && post.isLocked();
    }

    public void collapseChildren(Post post, boolean z) {
        cancelMoreRequest();
        cancelContinueRequest();
        this.selectedComment = null;
        this.view.setCollapseAnimation();
        if (this.cursor != null) {
            int i = 0;
            while (i < this.cursor.getCount()) {
                this.cursor.moveToPosition(i);
                Cursor cursor = this.cursor;
                if (post.getId().equals(cursor.getString(cursor.getColumnIndex("_id")))) {
                    ArrayList arrayList = new ArrayList();
                    while (true) {
                        i++;
                        if (i >= this.cursor.getCount()) {
                            break;
                        }
                        Post postZ = Post.fromCursorPosition(this.cursor, i);
                        if (postZ.getLevel() <= post.getLevel()) {
                            break;
                        } else {
                            arrayList.add(postZ.getId());
                        }
                    }
                    if (SettingsSingleton.getSettings().collapseParent) {
                        arrayList.add(0, post.getId());
                    }
                    if (arrayList.size() > 0) {
                        this.context.getContentResolver().update(RedditProvider.URI_HIDE_COMMENTS, null, post.getId(), (String[]) arrayList.toArray(new String[arrayList.size()]));
                        if (z) {
                            this.context.getContentResolver().notifyChange(RedditProvider.URI_MULTIPLE_POSTS, null);
                            return;
                        }
                        return;
                    }
                    return;
                }
                i++;
            }
        }
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public boolean isContinueInstance() {
        return this.continueInstance;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public boolean isNp() {
        return this.np;
    }

    public String buildCommentPermalink(Post post) {
        String postId = getPost().getId();
        return "https://www.reddit.com/r/" + getPost().getSubreddit() + "/comments/" + postId + "/_/" + post.getId();
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void loadAllComments(Bundle bundle) {
        bundle.remove("comment_id");
        this.commentId = null;
        reset();
        load(true);
    }

    public void expandCollapsedChildren(Post post) {
        expandChildren(post, true);
    }

    public void expandChildren(Post post, boolean z) {
        cancelMoreRequest();
        cancelContinueRequest();
        this.selectedComment = null;
        this.view.setExpandAnimation();
        String postId = post.getId();
        String[] strArrSplit = TextUtils.split(post.getChildIds(), ",");
        if (strArrSplit != null) {
            this.context.getContentResolver().update(RedditProvider.URI_SHOW_COMMENTS, null, postId, strArrSplit);
            if (z) {
                this.context.getContentResolver().notifyChange(RedditProvider.URI_MULTIPLE_POSTS, null);
            }
        }
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void loadMoreComments(Post post) {
        Logger.verbose("CommentsPresenterImpl", "Requesting more: " + post.getId());
        cancelMoreRequest();
        cancelContinueRequest();
        this.view.setMoreCommentsAnimation();
        OAuthCommentsMoreRequest oAuthCommentsMoreRequest = new OAuthCommentsMoreRequest(this.context, DatabaseIdHelper.buildId(this.postId, this.commentId), this.postId, post.getParentId(), post.getId(), post.getCommentMoreIds(), post.getListsTime(), post.getLevel(), getPost().getSuggestedSort(), new MoreCommentsResponseListener(), new MoreCommentsErrorListener());
        this.moreRequest = oAuthCommentsMoreRequest;
        OAuthRequestManager.send(this.context, oAuthCommentsMoreRequest);
    }

    public FragmentManager getFragmentManager() {
        return ContextHelper.getFragmentManager(this.context);
    }

    @Override // com.resync.app.presenters.BaseCursorPresenter
    public void sharePost(Post post) {
        FragmentUtils.showWithArguments(SharePostDialogBottomSheet.class, ((BaseActivity) this.context).getSupportFragmentManager(), SharePostDialogBottomSheet.buildArguments(post));
    }

    @Override // com.resync.app.presenters.BaseCursorPresenter
    public void unhidePost(Post post) {
        HideManager.unhide(this.context, post);
    }

    public long getLastLinkClickTime() {
        return this.lastLinkClickTime;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public boolean isSelected(Post post) {
        return post.equals(this.selectedComment);
    }

    @Override // com.resync.app.presenters.BaseCursorPresenter
    public void peekImage(Post post, ImageView imageView) {
        ImagePeekDialogFragment.show(this.context, post, imageView.getWidth(), imageView.getHeight());
    }

    public void loadFromNetwork(boolean refresh, boolean wasCached) {
        Logger.verbose("CommentsPresenterImpl", "Loading from network");
        Logger.verbose("CommentsPresenterImpl", "Refresh: " + refresh);
        Logger.verbose("CommentsPresenterImpl", "Was cached: " + wasCached);
        if (TestUtils.isRobolectric()) {
            return;
        }
        if (!ConnectivityHelper.isConnected(this.context)) {
            this.view.showOffline();
            return;
        }
        setLoading(true);
        OAuthCommentsRequest oAuthCommentsRequest = new OAuthCommentsRequest(this.context, this.postId, this.subreddit, this.commentId, this.sort, refresh, wasCached, this.continueInstance, new CommentsResponseListener(wasCached), new CommentsErrorListener());
        this.commentsRequest = oAuthCommentsRequest;
        OAuthRequestManager.send(this.context, oAuthCommentsRequest);
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void shareComment(Post post) {
        RedditHelper.shareTextWithSubject(this.context, getPost().getTitle(), buildCommentPermalink(post) + "?context=1000");
    }

    public void updateViewCursor(Cursor cursor, boolean z) {
        this.view.setCursor(cursor);
    }

    @Override // com.resync.app.presenters.BasePresenter
    public void onStart() {
        Logger.verbose("CommentsPresenterImpl", "onStart");
        registerLoaderListener();
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public boolean isSearch() {
        return this.search;
    }

    public void resetAnimation() {
        this.view.setEnterExitAnimation();
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void load(boolean z) {
        CommentsView commentsView = this.view;
        if (commentsView != null) {
            commentsView.setSubredditTitle(this.subreddit);
            if (getPost() != null) {
                this.view.setSortLabel(this.sort, getPost().getSuggestedSort());
            } else {
                this.view.setSortLabel(this.sort, null);
            }
        }
        if (this.search) {
            loadCommentsCursor();
            return;
        }
        CursorLoader cursorLoaderA = PostsManager.createSearchLoader(this.context, DatabaseIdHelper.buildId(this.postId, this.commentId), this.search, this.userFilter);
        cursorLoaderA.registerListener(0, new RefreshLoadListener(z));
        cursorLoaderA.startLoading();
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void autoRefresh() {
        cancelAllRequests();
        OAuthCommentsRequest oAuthCommentsRequest = new OAuthCommentsRequest(this.context, this.postId, this.subreddit, null, this.sort, true, true, this.continueInstance, new AutoRefreshResponseListener(), new AutoRefreshErrorListener());
        this.commentsRequest = oAuthCommentsRequest;
        OAuthRequestManager.send(this.context, oAuthCommentsRequest);
    }

    public void onCursorLoaded(Cursor cursor) {
        Logger.verbose("CommentsPresenterImpl", "Cursor loaded: " + cursor);
        if (this.context == null) {
            return;
        }
        if (!TextUtils.isEmpty(this.commentId) && getCursor() == null && cursor != null) {
            int i = 0;
            while (true) {
                if (i >= cursor.getCount()) {
                    break;
                }
                Post postZ = Post.fromCursorPosition(cursor, i);
                if (postZ.getId().equals(this.commentId)) {
                    this.selectedComment = postZ;
                    break;
                }
                i++;
            }
        }
        if (cursor != null && !cursor.isClosed() && cursor.getCount() > 1) {
            this.newCommentCount = 0;
            for (int i = 1; i < cursor.getCount(); i++) {
                cursor.moveToPosition(i);
                if (cursor.getInt(cursor.getColumnIndex("new_comment")) == 1) {
                    this.newCommentCount++;
                }
            }
            Logger.verbose("CommentsPresenterImpl", "New comment count: " + this.newCommentCount);
        }
        updateViewCursor(cursor, setCursor(cursor));
        setLoading(false);
        if (this.search) {
            return;
        }
        if (!TextUtils.isEmpty(this.commentId)) {
            this.view.showSingleThreadNotification();
            return;
        }
        Cursor cursor2 = this.cursor;
        if (cursor2 == null || cursor2.getCount() > 1) {
            return;
        }
        this.view.showNoComments();
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public String getSort() {
        return this.sort;
    }

    public void onLoadError(VolleyError volleyError) {
        if (this.search) {
            return;
        }
        this.view.showError(volleyError);
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void onCommentClicked(Post post) {
        Logger.verbose("CommentsPresenterImpl", "Comment clicked: " + post);
        if (this.search) {
            BusSingleton.getBus().post(new SearchCommentSelected(post));
            return;
        }
        if (SettingsSingleton.getSettings().commentsSingle) {
            if (post.getChildCount() > 0) {
                expandCollapsedChildren(post);
                return;
            } else {
                collapseComment(post);
                return;
            }
        }
        Post post2 = this.selectedComment;
        if (post2 == null || !post2.equals(post)) {
            selectCommentDefault(post);
        } else {
            deselectComment(true);
        }
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void selectComment(Post post) {
        Cursor cursor = this.cursor;
        if (cursor == null || cursor.isClosed() || post == null) {
            return;
        }
        int rootIndex = 0;
        for (int i = 0; i < this.cursor.getCount(); i++) {
            this.cursor.moveToPosition(i);
            Cursor cursor2 = this.cursor;
            if (cursor2.getInt(cursor2.getColumnIndex("level")) == 0) {
                rootIndex = i;
            }
            Cursor cursor3 = this.cursor;
            if (cursor3.getString(cursor3.getColumnIndex("_id")).equals(post.getId())) {
                this.selectedComment = Post.fromCursorPosition(this.cursor, rootIndex);
                this.view.setNoAnimation();
                this.view.notifyDataSetChanged();
                this.view.scrollToPosition(rootIndex);
                return;
            }
        }
    }

    public void setLoading(boolean z) {
        this.view.setRefreshing(z);
    }

    @Override // com.resync.app.presenters.BasePresenter
    public void recover() {
        load(true);
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public Post getPost() {
        return this.post;
    }

    public void registerLoaderListener() {
        if (this.cursorLoader == null || this.loaderListener != null) {
            StringBuilder sb2 = new StringBuilder();
            sb2.append("The LoaderListener was not registered (");
            sb2.append(this.cursor == null ? "CursorLoader was null" : "LoaderListener wasn't null");
            sb2.append(")");
            Logger.verbose("CommentsPresenterImpl", sb2.toString());
            return;
        }
        Logger.verbose("CommentsPresenterImpl", "Registering the LoaderListener");
        LoaderOnLoadCompleteListenerC0139d loaderOnLoadCompleteListenerC0139d = new LoaderOnLoadCompleteListenerC0139d();
        this.loaderListener = loaderOnLoadCompleteListenerC0139d;
        this.cursorLoader.registerListener(0, loaderOnLoadCompleteListenerC0139d);
        this.cursorLoader.startLoading();
    }

    public void openEditFragment(Post post, Post post2) {
        Post post3;
        if (post2 == null && (post3 = this.post) != null && post3.isLocked() && !this.post.isMod()) {
            StaticToast.showWithContext(this.context, "This post has been locked");
            return;
        }
        if (!AccountSingleton.getInstance().isLoggedIn()) {
            StaticToast.show(this.context, R.string.common_generic_error_logged_out);
            return;
        }
        this.view.setDefaultAnimation();
        Context context = this.context;
        if (!(context instanceof BaseActivity) || ((BaseActivity) context).isClosing()) {
            return;
        }
        try {
            EditFragment.newCommentInstance(DatabaseIdHelper.buildId(this.postId, this.commentId), post, post2).show(((AppCompatActivity) this.context).getSupportFragmentManager(), "EditFragment");
        } catch (Exception e) {
            CrashlyticsHelper.logException(e);
            Logger.logException(e);
        }
    }

    @Override // com.resync.app.presenters.BaseCursorPresenter
    public void hidePost(Post post) {
        HideManager.hide(this.context, post);
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void deselectComment(boolean z) {
        this.selectedComment = null;
        if (z) {
            this.view.setActionsAnimation();
        } else {
            this.view.setNoAnimation();
        }
        this.view.notifyDataSetChanged();
    }

    public boolean setCursor(Cursor cursor) {
        Logger.verbose("CommentsPresenterImpl", "Setting cursor...");
        Cursor cursor2 = this.cursor;
        boolean z = cursor2 == null && cursor != null;
        if (cursor2 != null && cursor != null && !cursor2.equals(cursor) && !this.cursor.isClosed()) {
            Logger.verbose("CommentsPresenterImpl", "Automatically closed cursor");
            this.cursor.close();
        }
        this.cursor = cursor;
        if (cursor != null && !cursor.isClosed() && this.cursor.getCount() > 0) {
            setPost(Post.fromCursor(this.cursor));
            this.navigation.process(cursor, getPost());
        }
        return z;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void changeSort(Bundle bundle, String str) {
        bundle.remove("access");
        bundle.putString("access", str);
        reset();
        this.sort = str;
        load(true);
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void setKeywordFilter(String str) {
        this.keywordFilter = str;
        this.view.notifyDataSetChanged();
    }

    public void setLastLinkClickTime(long j) {
        this.lastLinkClickTime = j;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void continueThread(Post post) {
        Logger.verbose("CommentsPresenterImpl", "Continuing thread: " + post);
        cancelMoreRequest();
        cancelContinueRequest();
        Context context = this.context;
        context.startActivity(CommentsActivity.createContinueIntent(context, this.postId, post.getParentId().split("_")[1], this.subreddit));
    }

    public void setPost(Post post) {
        this.post = post;
        if (post != null) {
            this.subreddit = post.getSubreddit();
        }
        if (!this.search) {
            RecentHelper.add(post);
        }
        if (post != null) {
            BusSingleton.getBus().post(new CommentPostUpdated());
        }
        CommentsView commentsView = this.view;
        if (commentsView != null) {
            commentsView.setSubredditTitle(this.subreddit);
            if (post != null) {
                this.view.setSortLabel(this.sort, post.getSuggestedSort());
            } else {
                this.view.setSortLabel(this.sort, null);
            }
        }
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public boolean isLoading() {
        return this.commentsRequest != null;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public boolean isSelf(Post post) {
        if (post == null) {
            return false;
        }
        return post.getAuthor().equalsIgnoreCase(AccountSingleton.getInstance().getUsername());
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void scrollToComment(Post post) {
        this.highlightedComment = post;
        this.keywordFilter = null;
        if (post != null) {
            BusSingleton.getBus().post(new ScrollToPosition(post));
        }
        this.view.notifyDataSetChanged();
    }

    public void selectCommentDefault(Post post) {
        selectCommentAnimated(post, true);
    }

    @Override // com.resync.app.presenters.BaseCursorPresenter
    public void openLink(Pair<View, String>[] pairArr, Post post) {
        if (!canOpenLink()) {
            Logger.verbose("CommentsPresenterImpl", "Bouncing link!");
            return;
        }
        Logger.verbose("CommentsPresenterImpl", "Opening link: " + post);
        this.view.setNoAnimation();
        if (pairArr == null || pairArr.length <= 0) {
            PostsHelper.markViewed(this.context, post);
        } else {
            PostsHelper.markViewedDelayed(this.context, post);
        }
        LinkHelper.open(this.context, pairArr, post);
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void replyToComment(Post post) {
        Post post2 = this.post;
        if (post2 == null || !post2.isLocked() || this.post.isMod()) {
            openEditFragment(getPost(), post);
        } else {
            StaticToast.showWithContext(this.context, "This post has been locked");
        }
    }

    public void unregisterLoaderListener() {
        if (this.cursorLoader == null || this.loaderListener == null) {
            Logger.verbose("CommentsPresenterImpl", "The LoaderListener was not unregistered");
            return;
        }
        Logger.verbose("CommentsPresenterImpl", "Unregistered the LoaderListener");
        this.cursorLoader.unregisterListener(this.loaderListener);
        this.loaderListener = null;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public String getUserFilter() {
        return this.userFilter;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public Post getScrollTarget() {
        return this.highlightedComment;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public String getNewCommentSuffix() {
        if (this.newCommentCount <= 0 || !SettingsSingleton.getInstance().getCurrentSettings().commentsHighlightNew) {
            return null;
        }
        return " (+" + this.newCommentCount + ")";
    }

    @Override // com.resync.app.presenters.BaseCursorPresenter
    public void openLinkExternally(Post post) {
    }

    @Override // com.resync.app.presenters.BasePresenter
    public void onDestroy() {
        Logger.verbose("CommentsPresenterImpl", "onDestroy");
        cancelAllRequests();
        closeCursor();
    }

    @Override // com.resync.app.presenters.BasePresenter
    public void onStop() {
        Logger.verbose("CommentsPresenterImpl", "onStop");
        unregisterLoaderListener();
        CommentsView commentsView = this.view;
        if (commentsView != null) {
            commentsView.setCursor(null);
        }
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void onCommentLongClicked(Post post) {
        Logger.verbose("CommentsPresenterImpl", "Comment long clicked: " + post);
        if (this.search) {
            return;
        }
        if (!SettingsSingleton.getSettings().commentsSingle) {
            if (post.getChildCount() > 0) {
                expandCollapsedChildren(post);
                return;
            } else {
                collapseComment(post);
                return;
            }
        }
        Post post2 = this.selectedComment;
        if (post2 == null || !post2.equals(post)) {
            selectCommentDefault(post);
        } else {
            deselectComment(true);
        }
    }

    @Override // com.resync.app.presenters.BasePresenter
    public void restoreArguments(Bundle bundle) {
        if (bundle.containsKey("Post")) {
            setPost((Post) bundle.getSerializable("Post"));
            RedditApplication.warmUpCustomTab(getPost());
        }
        if (bundle.containsKey("post_id")) {
            this.postId = bundle.getString("post_id");
        }
        if (bundle.containsKey("comment_id")) {
            this.commentId = bundle.getString("comment_id");
        }
        if (bundle.containsKey("subreddit")) {
            this.subreddit = bundle.getString("subreddit");
        }
        this.sort = bundle.getString("access");
        this.np = bundle.getBoolean("np", false);
        this.search = bundle.getBoolean("search", false);
        this.continueInstance = bundle.getBoolean("continue_instance", false);
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public boolean hasScrollTarget() {
        return this.highlightedComment != null;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public String getCommentId() {
        return this.commentId;
    }

    @Override // com.resync.app.presenters.BaseCursorPresenter
    public Cursor getCursor() {
        return this.cursor;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public CommentNavigation getNavigation() {
        return this.navigation;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public String getDatabaseId() {
        return DatabaseIdHelper.buildId(this.postId, this.commentId);
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public boolean isPostDeleted() {
        Post post = this.post;
        return post != null && post.isArchived();
    }

    @Override // com.resync.app.presenters.BaseCursorPresenter
    public void openComments(Post post) {
        if (!canOpenLink()) {
            Logger.verbose("CommentsPresenterImpl", "Bouncing link!");
            return;
        }
        Logger.verbose("CommentsPresenterImpl", "Opening comments: " + post);
        this.view.setNoAnimation();
        PostsHelper.markViewedDelayed(this.context, post);
        com.resync.app.util.links.LinkHandler.openComments(this.context, post);
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public String getKeywordFilter() {
        return this.keywordFilter;
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void distinguishComment(Post post) {
        if (!ConnectivityHelper.isConnected(this.context)) {
            StaticToast.show(this.context, R.string.common_generic_error_offline);
        } else if (TextUtils.isEmpty(post.getDistinguished())) {
            FragmentUtils.showWithPostId(DistinguishCommentAlertDialogBottomSheet.class, getFragmentManager(), post.getId());
        } else {
            FragmentUtils.showWithPostId(UndistinguishCommentAlertDialogBottomSheet.class, getFragmentManager(), post.getId());
        }
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public boolean isPostSpoiler() {
        Post post = this.post;
        return post != null && post.isContestMode();
    }

    @Override // com.resync.app.presenters.BaseCursorPresenter
    public void loadCursor() {
        if (this.context == null) {
            return;
        }
        loadCommentsCursor();
    }

    @Override // com.resync.app.presenters.BaseCursorPresenter
    public void toggleHide(Post post) {
        if (post.isHidden()) {
            unhidePost(post);
        } else {
            hidePost(post);
        }
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void saveComment(Post post) {
        if (SaveManager.toggleSave(this.context, post)) {
            StaticToast.showWithContext(this.context, post.isSaved() ? "Unsaved comment" : "Saved comment");
        }
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void selectCommentAnimated(Post post, boolean z) {
        Logger.verbose("CommentsPresenterImpl", "Selected: " + post);
        this.selectedComment = post;
        if (z) {
            this.view.setActionsAnimation();
        } else {
            this.view.setNoAnimation();
        }
        this.view.notifyDataSetChanged();
    }

    @Override // com.resync.app.presenters.CommentsPresenter
    public void cancelMore(Post post) {
        if (post == null || this.moreRequest == null || !StringUtils.equalsIgnoreCase(post.getId(), this.moreRequest.getFullname())) {
            return;
        }
        Logger.verbose("More cancelled");
        this.moreRequest.cancel();
        cancelMoreRequest();
    }
}
