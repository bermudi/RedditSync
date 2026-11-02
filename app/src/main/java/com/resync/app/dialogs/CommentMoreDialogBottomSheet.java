package com.resync.app.dialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.bumptech.glide.RedditHelper;
import com.resync.app.R;
import com.resync.app.singleton.AccountSingleton;
import com.resync.app.ui.activities.CommentsActivity;
import com.resync.app.ui.fragment_dialogs.EditFragment;
import com.resync.app.ui.fragment_dialogs.ParentCommentFragment;
import com.resync.app.ui.fragment_dialogs.bottom.material_dialogs.base.AbstractSelectionDialogBottomSheet;
import com.resync.app.premium.UserPaintManager;
import com.resync.app.helpers.ui.AlertDialogHelper;
import com.resync.app.helpers.ui.StaticToast;
import com.resync.app.managers.NewUserTagManager;
import com.resync.app.dialogs.TranslateAsyncAlertDialogBottomSheet;
import com.resync.app.singletons.BusSingleton;
import com.resync.app.net.OAuthRequestManager;
import com.resync.app.net.thirdparty.RestoreCommentRequest;
import com.resync.app.events.StartTts;
import com.resync.app.dialogs.FragmentUtils;
import org.apache.commons.lang3.StringUtils;
import com.resync.app.helpers.ConnectivityHelper;
import com.resync.app.helpers.CrashlyticsHelper;
import com.resync.app.helpers.PreferencesHelper;
import com.resync.app.helpers.RemoteConfigHelper;
import com.resync.app.security.KeyManager;
import com.resync.app.premium.UltraHelper;
import com.resync.app.util.Logger;
import com.resync.app.model.Post;

/* JADX INFO: renamed from: t9.s */
/* JADX INFO: compiled from: CommentMoreDialogBottomSheet.java */
/* JADX INFO: loaded from: classes.dex */
public class CommentMoreDialogBottomSheet extends AbstractSelectionDialogBottomSheet {
    private Post comment;
    private boolean showFull;
    AbstractSelectionDialogBottomSheet.SelectionItem viewAwardsOption;
    AbstractSelectionDialogBottomSheet.SelectionItem viewFilteredCommentOption;
    AbstractSelectionDialogBottomSheet.SelectionItem shareOption;
    AbstractSelectionDialogBottomSheet.SelectionItem openInBrowserOption;
    AbstractSelectionDialogBottomSheet.SelectionItem selectTextOption;
    AbstractSelectionDialogBottomSheet.SelectionItem copyOption;
    AbstractSelectionDialogBottomSheet.SelectionItem reportOption;
    AbstractSelectionDialogBottomSheet.SelectionItem editOption;
    AbstractSelectionDialogBottomSheet.SelectionItem deleteOption;
    AbstractSelectionDialogBottomSheet.SelectionItem inboxRepliesOption;
    AbstractSelectionDialogBottomSheet.SelectionItem tagUserOption;
    AbstractSelectionDialogBottomSheet.SelectionItem paintUserOption;
    AbstractSelectionDialogBottomSheet.SelectionItem restoreCommentOption;
    AbstractSelectionDialogBottomSheet.SelectionItem translateCommentOption;
    AbstractSelectionDialogBottomSheet.SelectionItem readAloudOption;

    /* JADX INFO: compiled from: CommentMoreDialogBottomSheet.java */
    class CopySelectedTextClickListener implements DialogInterface.OnClickListener {

        final /* synthetic */ TextView textView;

        CopySelectedTextClickListener(TextView textView) {
            this.textView = textView;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public void onClick(DialogInterface dialogInterface, int which) {
            int selectionStart = this.textView.getSelectionStart();
            int selectionEnd = this.textView.getSelectionEnd();
            if (selectionStart == selectionEnd) {
                StaticToast.showWithContext(CommentMoreDialogBottomSheet.this.getContext(), "No text copied");
                return;
            }
            String strSubstring = this.textView.getText().toString().substring(selectionStart, selectionEnd);
            RedditHelper.copyToClipboard(CommentMoreDialogBottomSheet.this.getContext(), strSubstring, false);
            StaticToast.showWithContext(CommentMoreDialogBottomSheet.this.getContext(), "Text copied: " + strSubstring);
        }
    }

    public static Bundle buildArgs(Post post, boolean showFull) {
        Bundle bundle = new Bundle();
        bundle.putSerializable("comment", post);
        bundle.putBoolean("show_full", showFull);
        return bundle;
    }

    private String getPermalink() {
        return "https://www.reddit.com" + this.comment.getPermalink();
    }

    @Override // com.resync.app.ui.fragment_dialogs.bottom.material_dialogs.base.AbstractSelectionDialogBottomSheet
    public void setupItems() {
        if (this.comment.isCommentFiltered()) {
            this.viewFilteredCommentOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.ic_filter_list_black_24dp, "View filtered comment"));
        }
        if (this.comment.hasAwards()) {
            this.viewAwardsOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.outline_medal, "View awards"));
        }
        this.shareOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.outline_share_24, "Share"));
        this.openInBrowserOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.outline_open_in_browser_24, "Open in browser"));
        this.selectTextOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.outline_text_fields_24, "Select text"));
        this.copyOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.ic_content_copy_black_24dp, "Copy..."));
        this.reportOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.outline_report_24, "Report"));
        String tagLabel = NewUserTagManager.getInstance().hasTag(this.comment.getAuthor()) ? "Remove tag" : "Tag user";
        this.tagUserOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.format_text_variant_outline, tagLabel));
        boolean paintEnabled;
        if (UltraHelper.isUltra()) {
            if (!RemoteConfigHelper.isC4266fa()) {
                paintEnabled = true;
            } else {
                String key = PreferencesHelper.getDefaultPrefs().getString(KeyManager.purchaseTokenKey(), null);
                paintEnabled = !StringUtils.isEmpty(key) && StringUtils.containsIgnoreCase(key, ".AO-");
            }
        } else {
            paintEnabled = false;
        }
        if (paintEnabled) {
            this.paintUserOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.format_paint, "Paint user"));
        }
        if (StringUtils.equalsAnyIgnoreCase(AccountSingleton.getInstance().getUsername(), this.comment.getAuthor())) {
            this.editOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.outline_edit_24, "Edit"));
            this.deleteOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.outline_delete_24, "Delete"));
            String inboxLabel = "Inbox replies (" + (this.comment.isSendReplies() ? "enabled" : "disabled") + ")";
            this.inboxRepliesOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.markunread_mailbox_outline, inboxLabel));
        }
        if (UltraHelper.isUltra() && StringUtils.equalsAnyIgnoreCase(this.comment.getAuthor(), "[removed]", "[deleted]") && RemoteConfigHelper.isUltraRestore()) {
            this.restoreCommentOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.outline_restore_24, "Restore comment"));
        }
        if (this.showFull) {
            this.readAloudOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.text_to_speech, "Read aloud"));
        }
        try {
            if ((getActivity() instanceof CommentsActivity) && UltraHelper.isUltra() && RemoteConfigHelper.isUltraTranslate()) {
                this.translateCommentOption = addItem(new AbstractSelectionDialogBottomSheet.SelectionItem(R.drawable.outline_translate_24, "Translate comment with Google"));
            }
        } catch (Exception e) {
            Logger.logException(e);
        }
    }

    @Override // com.resync.app.dialogs.CustomBottomSheetDialogFragment
    public boolean requiresArguments() {
        return true;
    }

    @Override // com.resync.app.dialogs.CustomBottomSheetDialogFragment
    public void loadCustomArguments() {
        this.comment = (Post) getArguments().getSerializable("comment");
        this.showFull = getArguments().getBoolean("show_full");
    }

    @Override // com.resync.app.dialogs.interfaces.SelectionDialogBottomSheetInterface
    public String getTitle() {
        return "Comment options ";
    }

    @Override // com.resync.app.dialogs.interfaces.SelectionDialogBottomSheetInterface
    public void onItemSelected(AbstractSelectionDialogBottomSheet.SelectionItem item) {
        if (item.equals(this.viewAwardsOption)) {
            FragmentUtils.showWithPostIdAndLinkId(AwardsCommentAsyncSelectionDialogBottomSheet.class, getParentFragmentManager(), this.comment.getId(), this.comment.getLinkId());
        } else if (item.equals(this.tagUserOption)) {
            NewUserTagManager.toggleTag(getActivity(), this.comment.getAuthor());
            StaticToast.showWithContext(getActivity(), item.toString());
        } else if (item.equals(this.viewFilteredCommentOption)) {
            ParentCommentFragment.newInstance(this.comment).show(getParentFragmentManager(), "ParentCommentFragment");
        } else if (item.equals(this.shareOption)) {
            RedditHelper.shareTextWithSubject(getActivity(), this.comment.getTitle(), getPermalink());
        } else if (item.equals(this.openInBrowserOption)) {
            com.resync.app.util.links.LinkHandler.openExternal(getActivity(), getPermalink());
        } else if (item.equals(this.selectTextOption)) {
            View viewInflate = View.inflate(getActivity(), R.layout.dialog_copy, null);
            TextView textView = (TextView) viewInflate.findViewById(R.id.select);
            textView.setText(this.comment.getBodyRaw());
            AlertDialogHelper.newBuilder(getContext()).setMessage("Select text...").setView(viewInflate).setPositiveButton("Copy", new CopySelectedTextClickListener(textView)).create().show();
        } else if (item.equals(this.reportOption)) {
            if (AccountSingleton.getInstance().isLoggedIn()) {
                FragmentUtils.showWithPostId(ReportCommentInputDialogBottomSheet.class, getParentFragmentManager(), this.comment.getId());
            } else {
                StaticToast.show(getActivity(), R.string.common_generic_error_logged_out);
            }
        } else if (item.equals(this.copyOption)) {
            FragmentUtils.showWithArguments(CopyCommentDialogBottomSheet.class, getParentFragmentManager(), CopyCommentDialogBottomSheet.buildArgs(this.comment));
        } else if (item.equals(this.editOption)) {
            try {
                Post post = this.comment;
                EditFragment.newCommentInstance(null, post, post).show(getChildFragmentManager(), "EditFragment");
            } catch (Exception e) {
                CrashlyticsHelper.logException(e);
                Logger.logException(e);
            }
        } else if (item.equals(this.deleteOption)) {
            if (ConnectivityHelper.isConnected(getContext())) {
                FragmentUtils.showWithPostId(DeleteCommentAlertDialogBottomSheet.class, getParentFragmentManager(), this.comment.getId());
            } else {
                StaticToast.show(getContext(), R.string.common_generic_error_offline);
            }
        } else if (item.equals(this.inboxRepliesOption)) {
            FragmentUtils.showWithPost(CommentInboxRepliesAlertDialogBottomSheet.class, getParentFragmentManager(), this.comment);
        } else if (item.equals(this.restoreCommentOption)) {
            OAuthRequestManager.enqueueAux(new RestoreCommentRequest(this.comment));
        } else if (item.equals(this.paintUserOption)) {
            UserPaintManager.getInstance().showAddPaintDialog(getActivity(), this.comment.getAuthor());
        } else if (item.equals(this.translateCommentOption)) {
            if ((getActivity() instanceof CommentsActivity) && ((CommentsActivity) getActivity()).getCommentsFragment() != null) {
                if (((CommentsActivity) getActivity()).getCommentsFragment().getPresenter().getPost().isOver18()) {
                    StaticToast.show("Translate is not available on NSFW posts");
                } else {
                    FragmentUtils.showWithArguments(TranslateAsyncAlertDialogBottomSheet.class, getParentFragmentManager(), TranslateAsyncAlertDialogBottomSheet.buildArgs(this.comment, null));
                }
            }
        } else if (item.equals(this.readAloudOption)) {
            BusSingleton.getBus().post(new StartTts(this.comment));
        }
        dismiss();
    }
}
