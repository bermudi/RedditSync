package com.resync.app.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.resync.app.R;
import com.resync.app.ui.activities.BaseActivity;
import com.resync.app.ui.fragment_dialogs.bottom.ActionsBottomSheetFragment;
import com.resync.app.helpers.ui.FoldableHelper;
import com.resync.app.helpers.ui.MonetUtil;
import com.resync.app.helpers.ui.TabletHelper;
import com.resync.app.events.StopScrollEvent;
import com.resync.app.singletons.BusSingleton;
import com.resync.app.events.StopExoplayerEvent;
import com.resync.app.helpers.NightHelper;
import com.resync.app.helpers.PaddingHelper;
import com.resync.app.helpers.SizeHelper;
import com.resync.app.helpers.WindowHelper;
import com.resync.app.helpers.WindowUtils;
import com.resync.app.dialogs.CustomBottomSheetInterface;
import com.resync.app.util.ColorUtils;
import com.resync.app.util.Logger;
import com.resync.app.model.Post;

/* JADX INFO: renamed from: s9.f */
/* JADX INFO: compiled from: CustomBottomSheetDialogFragment.java */
/* JADX INFO: loaded from: classes.dex */
public abstract class CustomBottomSheetDialogFragment extends BottomSheetDialogFragment implements CustomBottomSheetInterface {
    protected BottomSheetBehavior bottomSheetBehavior;
    private String user;
    private String subreddit;
    private String postId;
    private String linkId;
    private String url;
    private Post post;
    protected boolean topSheet;
    private int sheetState;
    private float touchX = -1.0f;
    private float touchY = -1.0f;

    /* JADX INFO: compiled from: CustomBottomSheetDialogFragment.java */
    class RoundedSheetShowListener implements DialogInterface.OnShowListener {

        final /* synthetic */ int backgroundColor;

        /* JADX INFO: compiled from: CustomBottomSheetDialogFragment.java */
        class SheetCallback extends BottomSheetBehavior.BottomSheetCallback {
            SheetCallback() {
            }

            @Override // com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
            public void onSlide(View view, float slideOffset) {
                CustomBottomSheetDialogFragment.this.onSheetSlide(view, slideOffset);
            }

            @Override // com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
            public void onStateChanged(View view, int newState) {
                CustomBottomSheetDialogFragment.this.onSheetStateChanged(view, newState);
            }
        }

        RoundedSheetShowListener(int backgroundColor) {
            this.backgroundColor = backgroundColor;
        }

        @Override // android.content.DialogInterface.OnShowListener
        public void onShow(DialogInterface dialogInterface) {
            FrameLayout frameLayout = (FrameLayout) ((BottomSheetDialog) dialogInterface).findViewById(R.id.design_bottom_sheet);
            if (frameLayout != null) {
                int cornerRadiusPx = SizeHelper.dpToPx(12);
                GradientDrawable gradientDrawable = new GradientDrawable();
                gradientDrawable.setColor(this.backgroundColor);
                if (CustomBottomSheetDialogFragment.this.isTopSheet()) {
                    float radius = cornerRadiusPx;
                    gradientDrawable.setCornerRadii(new float[]{0.0f, 0.0f, 0.0f, 0.0f, radius, radius, radius, radius});
                } else {
                    float radius = cornerRadiusPx;
                    gradientDrawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0.0f, 0.0f, 0.0f, 0.0f});
                }
                frameLayout.setBackground(gradientDrawable);
                if (CustomBottomSheetDialogFragment.this.getContext() != null) {
                    if (FoldableHelper.isFoldableModeEnabled()) {
                        if (FoldableHelper.isFoldableExpanded()) {
                            frameLayout.getLayoutParams().width = (int) (WindowUtils.getDisplayWidth() * 0.8f);
                            frameLayout.requestLayout();
                        }
                    } else if (WindowHelper.isLandscape(CustomBottomSheetDialogFragment.this.getContext()) && TabletHelper.isTabletModeRaw()) {
                        frameLayout.getLayoutParams().width = (int) (WindowUtils.getDisplayWidth() * 0.5f);
                        frameLayout.requestLayout();
                    } else if (WindowHelper.isLandscape(CustomBottomSheetDialogFragment.this.getContext())) {
                        frameLayout.getLayoutParams().width = (int) (WindowUtils.getDisplayWidth() * 0.66f);
                        frameLayout.requestLayout();
                    }
                }
                if (CustomBottomSheetDialogFragment.this.isTopSheet()) {
                    return;
                }
                CustomBottomSheetDialogFragment.this.bottomSheetBehavior = BottomSheetBehavior.from(frameLayout);
                CustomBottomSheetDialogFragment.this.bottomSheetBehavior.disableShapeAnimations();
                CustomBottomSheetDialogFragment.this.bottomSheetBehavior.setDraggable(true);
                CustomBottomSheetDialogFragment.this.bottomSheetBehavior.addBottomSheetCallback(new SheetCallback());
                if (CustomBottomSheetDialogFragment.this.isInitiallyExpanded()) {
                    CustomBottomSheetDialogFragment.this.bottomSheetBehavior.setState(3);
                    if (CustomBottomSheetDialogFragment.this.isHideable()) {
                        CustomBottomSheetDialogFragment.this.bottomSheetBehavior.setHideable(true);
                    }
                }
                CustomBottomSheetDialogFragment.this.onSheetReady();
            }
        }
    }

    protected CustomBottomSheetDialogFragment() {
    }

    @Override // androidx.fragment.app.c
    public int getTheme() {
        return isTopSheet() ? R.style.MaterialTopSheet : R.style.MaterialBottomSheet;
    }

    @Override // com.google.android.material.bottomsheet.BottomSheetDialogFragment, androidx.appcompat.app.AppCompatDialogFragment, androidx.fragment.app.c
    public Dialog onCreateDialog(Bundle bundle) {
        Dialog dialog = super.onCreateDialog(bundle);
        int backgroundColor = MonetUtil.getBackgroundColor(isNightMode());
        if (!isTopSheet()) {
            if (isNightMode()) {
                dialog.getWindow().setNavigationBarColor(backgroundColor);
            } else {
                dialog.getWindow().setNavigationBarColor(backgroundColor);
                if (!ColorUtils.isDark(backgroundColor)) {
                    dialog.getWindow().getDecorView().setSystemUiVisibility(16);
                }
            }
        }
        dialog.setOnShowListener(new RoundedSheetShowListener(backgroundColor));
        isTopSheet();
        return dialog;
    }

    @Override // androidx.fragment.app.Fragment
    public void onActivityCreated(Bundle bundle) {
        super.onActivityCreated(bundle);
        if (isTopSheet()) {
            getDialog().getWindow().getAttributes().windowAnimations = R.style.TopsheetCustomAnimation;
        } else if (dismissWithAnimation()) {
            try {
                ((BottomSheetDialog) requireDialog()).setDismissWithAnimation(true);
            } catch (Exception unused) {
            }
        }
    }

    @Override // androidx.fragment.app.c, androidx.fragment.app.Fragment
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override // androidx.fragment.app.c, androidx.fragment.app.Fragment
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        BusSingleton.getBus().post(new StopScrollEvent());
        BusSingleton.getBus().post(new StopExoplayerEvent());
        if (getClass() != ActionsBottomSheetFragment.class) {
            ActionsBottomSheetFragment.dismissExisting(getActivity());
        }
        if (getClass() != SubredditAboutBottomSheetFragment.class) {
            SubredditAboutBottomSheetFragment.dismissExisting(getActivity());
        }
        if (getArguments() != null && getArguments().containsKey(FragmentUtils.keySubreddit())) {
            setSubreddit(getArguments().getString(FragmentUtils.keySubreddit()));
        }
        if (getArguments() != null && getArguments().containsKey(FragmentUtils.keyUser())) {
            setUser(getArguments().getString(FragmentUtils.keyUser()));
        }
        if (getArguments() != null && getArguments().containsKey(FragmentUtils.keyPostId())) {
            setPostId(getArguments().getString(FragmentUtils.keyPostId()));
        }
        if (getArguments() != null && getArguments().containsKey(FragmentUtils.keyLinkId())) {
            setLinkId(getArguments().getString(FragmentUtils.keyLinkId()));
        }
        if (getArguments() != null && getArguments().containsKey(FragmentUtils.keyUrl())) {
            setUrl(getArguments().getString(FragmentUtils.keyUrl()));
        }
        if (getArguments() != null && getArguments().containsKey("Post")) {
            setPost((Post) getArguments().getSerializable("Post"));
        }
        if (getActivity() instanceof BaseActivity) {
            this.touchX = ((BaseActivity) getActivity()).lastX;
            this.touchY = ((BaseActivity) getActivity()).lastY;
            View viewFindViewById = getActivity().findViewById(R.id.toolbar);
            if (viewFindViewById != null) {
                int[] location = new int[2];
                viewFindViewById.getLocationOnScreen(location);
                if (((BaseActivity) getActivity()).lastY < location[1] + viewFindViewById.getHeight()) {
                    this.topSheet = true;
                }
            }
        }
        loadCustomArguments();
    }

    public boolean shouldPadTopSheet() {
        return true;
    }

    protected void expand() {
        BottomSheetBehavior bottomSheetBehavior = this.bottomSheetBehavior;
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(3);
        }
    }

    protected int getSheetState() {
        return this.sheetState;
    }

    @Override // androidx.fragment.app.Fragment
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        return layoutInflater.inflate(getLayoutRes(), viewGroup);
    }

    public boolean isNightMode() {
        return NightHelper.isNight();
    }

    public Post getPost() {
        return this.post;
    }

    public String getPostId() {
        return this.postId;
    }

    /** The id of the link (thread) this dialog's post lives under, when provided. */
    public String getLinkId() {
        return this.linkId;
    }

    public String getSubreddit() {
        return this.subreddit;
    }

    public String getUrl() {
        return this.url;
    }

    public String getUser() {
        return this.user;
    }

    public boolean requiresArguments() {
        return false;
    }

    public void hideKeyboard() {
        ((InputMethodManager) getActivity().getSystemService("input_method")).hideSoftInputFromWindow(getView().getRootView().getWindowToken(), 0);
    }

    public boolean isExpanded() {
        BottomSheetBehavior bottomSheetBehavior = this.bottomSheetBehavior;
        return bottomSheetBehavior != null && bottomSheetBehavior.getState() == 3;
    }

    public boolean isInitiallyExpanded() {
        return false;
    }

    public boolean isTopSheet() {
        return false;
    }

    public boolean isDialogShowing() {
        return (getDialog() == null || !getDialog().isShowing() || getActivity() == null) ? false : true;
    }

    public void loadCustomArguments() {
        if (requiresArguments()) {
            throw new RuntimeException("Custom arguments not loaded!");
        }
    }

    protected void onSheetReady() {
    }

    public void onSheetSlide(View view, float slideOffset) {
    }

    public void onSheetStateChanged(View view, int newState) {
        this.sheetState = newState;
        if (newState == 5) {
            try {
                dismissAllowingStateLoss();
            } catch (Exception e) {
                Logger.logException(e);
            }
        }
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public void setPostId(String str) {
        this.postId = str;
    }

    public void setLinkId(String str) {
        this.linkId = str;
    }

    public void setSubreddit(String str) {
        this.subreddit = str;
    }

    @Override // androidx.fragment.app.c, androidx.fragment.app.Fragment
    /* JADX INFO: renamed from: m2 */
    public void onStart() {
        super.onStart();
        BusSingleton.getBus().register(this);
    }

    public void setUrl(String str) {
        this.url = str;
    }

    @Override // androidx.fragment.app.c, androidx.fragment.app.Fragment
    /* JADX INFO: renamed from: n2 */
    public void onStop() {
        BusSingleton.getBus().unregister(this);
        super.onStop();
    }

    public void setUser(String str) {
        this.user = str;
    }

    @Override // androidx.fragment.app.Fragment
    public void onViewCreated(View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        if (isTopSheet()) {
            ((CoordinatorLayout.LayoutParams) getDialog().findViewById(R.id.design_bottom_sheet).getLayoutParams()).setBehavior(null);
            if (shouldPadTopSheet()) {
                PaddingHelper.setPaddingBottom(getDialog().findViewById(R.id.design_bottom_sheet), 16);
            }
        }
        if (TabletHelper.isTabletModeRaw() && TabletHelper.isLandscape() && this.touchX != -1.0f) {
            if (this.touchX < WindowUtils.getDisplayWidth() / 2) {
                ((ViewGroup.MarginLayoutParams) ((CoordinatorLayout.LayoutParams) getDialog().findViewById(R.id.design_bottom_sheet).getLayoutParams())).leftMargin = SizeHelper.dpToPx(16);
                ((CoordinatorLayout.LayoutParams) getDialog().findViewById(R.id.design_bottom_sheet).getLayoutParams()).gravity = 3;
            } else {
                ((ViewGroup.MarginLayoutParams) ((CoordinatorLayout.LayoutParams) getDialog().findViewById(R.id.design_bottom_sheet).getLayoutParams())).rightMargin = SizeHelper.dpToPx(16);
                ((CoordinatorLayout.LayoutParams) getDialog().findViewById(R.id.design_bottom_sheet).getLayoutParams()).gravity = 5;
            }
        }
    }

    protected boolean dismissWithAnimation() {
        return true;
    }

    public boolean isHideable() {
        return true;
    }

    public void showKeyboard(View view) {
        if (view != null) {
            try {
                view.requestFocus();
                ((InputMethodManager) getActivity().getSystemService("input_method")).showSoftInput(view, 1);
            } catch (Exception unused) {
            }
        }
    }

    @Override // androidx.fragment.app.c
    public void dismiss() {
        dismissAllowingStateLoss();
    }
}
