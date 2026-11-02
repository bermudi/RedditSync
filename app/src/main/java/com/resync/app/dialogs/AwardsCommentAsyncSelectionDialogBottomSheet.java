package com.resync.app.dialogs;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.resync.app.ui.fragment_dialogs.bottom.material_dialogs.base.AbstractSelectionDialogBottomSheet;
import com.resync.app.helpers.ui.StaticToast;
import com.resync.app.net.OAuthRequestManager;
import com.resync.app.model.Award;
import com.resync.app.dialogs.AwardBottomSheetFragment;
import com.resync.app.dialogs.FragmentUtils;
import com.resync.app.net.oauth.OAuthAwardsRequest;

/* JADX INFO: renamed from: t9.g */
/* JADX INFO: compiled from: AwardsCommentAsyncSelectionDialogBottomSheet.java */
/* JADX INFO: loaded from: classes.dex */
public class AwardsCommentAsyncSelectionDialogBottomSheet extends AbstractSelectionDialogBottomSheet {

    /* JADX INFO: compiled from: AwardsCommentAsyncSelectionDialogBottomSheet.java */
    class AwardsResponseListener implements Response.Listener<Award[]> {
        AwardsResponseListener() {
        }

        @Override // com.android.volley.Response.Listener
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public void onResponse(Award[] awardArr) {
            if (AwardsCommentAsyncSelectionDialogBottomSheet.this.isDialogShowing()) {
                for (Award award : awardArr) {
                    AwardsCommentAsyncSelectionDialogBottomSheet awardsCommentAsyncSelectionDialogBottomSheet = AwardsCommentAsyncSelectionDialogBottomSheet.this;
                    StringBuilder sb2 = new StringBuilder();
                    sb2.append(award.name);
                    sb2.append(award.count > 1 ? " × " + award.count : "");
                    AbstractSelectionDialogBottomSheet.ImageSelectionItem imageItem = new AbstractSelectionDialogBottomSheet.ImageSelectionItem(sb2.toString(), award.resizedIconUrl);
                    imageItem.tag = award;
                    AwardsCommentAsyncSelectionDialogBottomSheet.this.addItem(imageItem);
                }
                AwardsCommentAsyncSelectionDialogBottomSheet.this.showItems();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void onAwardsLoadError(VolleyError volleyError) {
        if (isDialogShowing()) {
            StaticToast.showWithContext(getActivity(), "Error loading awards");
            dismiss();
        }
    }

    @Override // com.resync.app.ui.fragment_dialogs.bottom.material_dialogs.base.AbstractSelectionDialogBottomSheet
    public void setupItems() {
        OAuthRequestManager.sendWithAppContext(new OAuthAwardsRequest("t1_", getPostId(), getLinkId(), new AwardsResponseListener(), new Response.ErrorListener() { // from class: t9.f
            @Override // com.android.volley.Response.ErrorListener
            public final void onErrorResponse(VolleyError volleyError) {
                AwardsCommentAsyncSelectionDialogBottomSheet.this.onAwardsLoadError(volleyError);
            }
        }));
    }

    @Override // com.resync.app.dialogs.interfaces.SelectionDialogBottomSheetInterface
    public String getTitle() {
        return "Comment awards";
    }

    @Override // com.resync.app.dialogs.interfaces.SelectionDialogBottomSheetInterface
    public void onItemSelected(AbstractSelectionDialogBottomSheet.SelectionItem item) {
        FragmentUtils.showWithArguments(AwardBottomSheetFragment.class, getParentFragmentManager(), AwardBottomSheetFragment.buildArgs((Award) item.tag));
        dismiss();
    }

    @Override // com.resync.app.ui.fragment_dialogs.bottom.material_dialogs.base.AbstractSelectionDialogBottomSheet
    public boolean isAsync() {
        return true;
    }
}
