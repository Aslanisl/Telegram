package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class ReactionCountView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    AvatarsImageView avatarsImageView;
    TextView titleView;
    BackupImageView leftImageView;
    ImageView leftIconView;
    int currentAccount;
    private MessageObject messageObject;

    public ReactionCountView(@NonNull Context context, int currentAccount, MessageObject messageObject) {
        super(context);
        this.currentAccount = currentAccount;
        this.messageObject = messageObject;

        setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4), 0);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 36, 0, 62, 0));

        avatarsImageView = new AvatarsImageView(context, false);
        avatarsImageView.setStyle(AvatarsImageView.STYLE_MESSAGE_SEEN);
        addView(avatarsImageView, LayoutHelper.createFrame(24 + 12 + 12 + 8, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        leftImageView = new BackupImageView(context);
        leftImageView.setRoundRadius(AndroidUtilities.dp(12));
        addView(leftImageView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        leftIconView = new ImageView(context);
        addView(leftIconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0, 0));

        ArrayList<TLRPC.TL_availableReaction> availableReactions = MessagesController.getInstance(currentAccount).getAvailableReactions();
        updateView(availableReactions);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.availableReactionsUpdated);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.availableReactionsUpdated);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.availableReactionsUpdated) {
            ArrayList<TLRPC.TL_availableReaction> reactions = (ArrayList<TLRPC.TL_availableReaction>) args[0];
            updateView(reactions);
        }
    }

    private void updateView(ArrayList<TLRPC.TL_availableReaction> reactions) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.messageOwner.reactions == null || messageObject.messageOwner.reactions.results == null) {
            return;
        }
        int reactionsCount = 0;
        for (int i = 0; i < messageObject.messageOwner.reactions.results.size(); i++) {
            TLRPC.TL_reactionCount reactionCount = messageObject.messageOwner.reactions.results.get(i);
            reactionsCount += reactionCount.count;
        }
        if (reactionsCount == 1 && messageObject.messageOwner.reactions.recent_reactons != null && messageObject.messageOwner.reactions.recent_reactons.size() == 1) {
            TLRPC.TL_messageUserReaction messageReactions = messageObject.messageOwner.reactions.recent_reactons.get(0);
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageReactions.user_id);
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            if (user != null) {
                avatarDrawable.setInfo(user);
                titleView.setText(ContactsController.formatName(user.first_name, user.last_name));
            }

            for (int i = 0; i < reactions.size(); i++) {
                TLRPC.TL_availableReaction reaction = reactions.get(i);
                if (reaction.reaction.equals(messageReactions.reaction)) {
                    TLRPC.Document document = reaction.static_icon;
                    String imageFilter = "16x16";
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                    SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);
                    if (svgThumb != null) {
                        leftImageView.setImage(ImageLocation.getForDocument(document), imageFilter, null, svgThumb, document);
                    } else if (thumb != null) {
                        leftImageView.setImage(ImageLocation.getForDocument(document), imageFilter, ImageLocation.getForDocument(thumb, document), null, 0, document);
                    } else {
                        leftImageView.setImage(ImageLocation.getForDocument(document), imageFilter, null, null, document);
                    }
                    break;
                }
            }

            leftIconView.setAlpha(0f);
            leftImageView.setAlpha(1f);
        } else {
            Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.actions_reactions).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
            leftIconView.setImageDrawable(drawable);

            titleView.setText(LocaleController.formatPluralString("Reactions", reactionsCount));

            leftIconView.setAlpha(1f);
            leftImageView.setAlpha(0f);
        }

        ArrayList<TLRPC.User> users = new ArrayList<>();
        for (int i = 0; i < messageObject.messageOwner.reactions.recent_reactons.size(); i++) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.reactions.recent_reactons.get(i).user_id);
            if (user != null) {
                users.add(user);
            }
        }

        for (int i = 0; i < 3; i++) {
            if (i < users.size()) {
                avatarsImageView.setObject(i, currentAccount, users.get(i));
            } else {
                avatarsImageView.setObject(i, currentAccount, null);
            }
        }
        if (users.size() == 1) {
            avatarsImageView.setTranslationX(AndroidUtilities.dp(24));
        } else if (users.size() == 2) {
            avatarsImageView.setTranslationX(AndroidUtilities.dp(12));
        } else {
            avatarsImageView.setTranslationX(0);
        }
        avatarsImageView.commitTransition(false);
    }
}