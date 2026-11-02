package com.resync.app.dialogs;

import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import com.resync.app.RedditApplication;
import com.resync.app.helpers.ui.StaticToast;
import org.apache.commons.lang3.StringUtils;
import com.resync.app.util.Logger;
import com.resync.app.model.Post;

/* JADX INFO: renamed from: s9.g */
/* JADX INFO: compiled from: FragmentUtils.java */
/* JADX INFO: loaded from: classes.dex */
public final class FragmentUtils {
    public static String keyPostId() {
        return "post_id";
    }

    public static String keySubreddit() {
        return "subreddit";
    }

    public static String keyUrl() {
        return "url";
    }

    public static String keyUser() {
        return "user";
    }

    public static void showWithArguments(Class<? extends CustomBottomSheetDialogFragment> cls, FragmentManager fragmentManager, Bundle bundle) {
        String simpleName = cls.getSimpleName();
        if (fragmentManager == null || fragmentManager.findFragmentByTag(simpleName) != null) {
            return;
        }
        try {
            CustomBottomSheetDialogFragment customBottomSheetDialogFragmentNewInstance = cls.newInstance();
            if (customBottomSheetDialogFragmentNewInstance.requiresArguments() && bundle == null) {
                throw new RuntimeException("Missing arguments: " + cls);
            }
            if (bundle != null) {
                customBottomSheetDialogFragmentNewInstance.setArguments(bundle);
            }
            customBottomSheetDialogFragmentNewInstance.show(fragmentManager, simpleName);
        } catch (Exception e) {
            StaticToast.showWithContext(RedditApplication.getAppContext(), "Error starting fragment: " + cls);
            Logger.logException(e);
        }
    }

    public static void show(Class<? extends CustomBottomSheetDialogFragment> cls, FragmentManager fragmentManager) {
        showWithArguments(cls, fragmentManager, null);
    }

    public static void showWithPostId(Class<? extends CustomBottomSheetDialogFragment> cls, FragmentManager fragmentManager, String str) {
        if (StringUtils.isEmpty(str)) {
            throw new RuntimeException("Missing post ID: " + cls);
        }
        Bundle bundle = new Bundle();
        bundle.putString(keyPostId(), str);
        showWithArguments(cls, fragmentManager, bundle);
    }

    public static String keyLinkId() {
        return "link_id";
    }

    /** Like {@link #showWithPostId}, plus the id of the link (thread) the post lives under. */
    public static void showWithPostIdAndLinkId(Class<? extends CustomBottomSheetDialogFragment> cls, FragmentManager fragmentManager, String postId, String linkId) {
        if (StringUtils.isEmpty(postId)) {
            throw new RuntimeException("Missing post ID: " + cls);
        }
        Bundle bundle = new Bundle();
        bundle.putString(keyPostId(), postId);
        bundle.putString(keyLinkId(), linkId);
        showWithArguments(cls, fragmentManager, bundle);
    }

    public static void showWithPost(Class<? extends CustomBottomSheetDialogFragment> cls, FragmentManager fragmentManager, Post post) {
        if (post != null) {
            Bundle bundle = new Bundle();
            bundle.putSerializable("Post", post);
            showWithArguments(cls, fragmentManager, bundle);
        } else {
            throw new RuntimeException("Missing post: " + cls);
        }
    }

    public static void showWithSubreddit(Class<? extends CustomBottomSheetDialogFragment> cls, FragmentManager fragmentManager, String str) {
        if (StringUtils.isEmpty(str)) {
            throw new RuntimeException("Missing subreddit: " + cls);
        }
        Bundle bundle = new Bundle();
        bundle.putString(keySubreddit(), str);
        showWithArguments(cls, fragmentManager, bundle);
    }

    public static void showWithUrl(Class<? extends CustomBottomSheetDialogFragment> cls, FragmentManager fragmentManager, String str) {
        if (StringUtils.isEmpty(str)) {
            throw new RuntimeException("Missing URL: " + cls);
        }
        Bundle bundle = new Bundle();
        bundle.putString(keyUrl(), str);
        showWithArguments(cls, fragmentManager, bundle);
    }

    public static void showWithUser(Class<? extends CustomBottomSheetDialogFragment> cls, FragmentManager fragmentManager, String str) {
        if (StringUtils.isEmpty(str)) {
            throw new RuntimeException("Missing user: " + cls);
        }
        Bundle bundle = new Bundle();
        bundle.putString(keyUser(), str);
        showWithArguments(cls, fragmentManager, bundle);
    }
}
