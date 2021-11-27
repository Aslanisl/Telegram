package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Iterator;

public class ReactionsSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static long lastTimeReactionsLoaded = 0;
    private static final ArrayList<TLRPC.TL_availableReaction> reactions = new ArrayList<>();

    private final ArrayList<String> availableReactions = new ArrayList<>();

    HeaderCell headerCell;

    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    ListAdapter adapter;
    AnimatorSet animatorSet;

    TextCheckCell enableReactionsCheckCell;

    long chatId;
    TLRPC.ChatFull info;

    public ReactionsSettingsActivity(long chatId, TLRPC.ChatFull chatFull) {
        super();
        this.chatId = chatId;
        info = chatFull;
        updateReactionsForInfo();
    }

    private void updateReactionsForInfo() {
        if (info != null) {
            availableReactions.clear();
            if (info.available_reactions != null) {
                availableReactions.addAll(info.available_reactions);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        fragmentView = new LinearLayout(context);
        LinearLayout linearLayout = (LinearLayout) this.fragmentView;
        linearLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        linearLayout.setTag(Theme.key_windowBackgroundGray);

        linearLayout.setOrientation(LinearLayout.VERTICAL);
        createActionBar(context);
        linearLayout.addView(actionBar);
        actionBar.setTitle(LocaleController.getString("ReactionsSettingsTitle", R.string.ReactionsSettingsTitle));
        actionBar.setCastShadows(false);

        enableReactionsCheckCell = new TextCheckCell(context);
        enableReactionsCheckCell.setHeight(56);
        enableReactionsCheckCell.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        enableReactionsCheckCell.setTextAndCheck(LocaleController.getString("EnableReactions", R.string.EnableReactions), false, false);
        enableReactionsCheckCell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
        enableReactionsCheckCell.setOnClickListener(v -> {
            boolean newIsChecked = !enableReactionsCheckCell.isChecked();
            setReactionsCheck(newIsChecked, true);
        });

        linearLayout.addView(enableReactionsCheckCell);

        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
        infoCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        infoCell.setText(LocaleController.getString("EnableReactionsHeader", R.string.EnableReactionsHeader));
        linearLayout.addView(infoCell);

        headerCell = new HeaderCell(context);
        headerCell.setText(LocaleController.getString("AvailableReactions", R.string.AvailableReactions));
        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout.addView(headerCell);

        listView = new RecyclerListView(context);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        listView.setAdapter(adapter = new ListAdapter());
        listView.setOnItemClickListener((view, position) -> {
            ReactionCheckCell checkCell = (ReactionCheckCell) view;
            boolean newIsChecked = !checkCell.isChecked;

            TLRPC.TL_availableReaction reaction = reactions.get(position);
            if (checkCell.isChecked) {
                Iterator<String> iterator = availableReactions.iterator();
                while (iterator.hasNext()) {
                    String reactionString = iterator.next();
                    if (reactionString.equals(reaction.reaction)) {
                        iterator.remove();
                    }
                }
            } else {
                availableReactions.add(reaction.reaction);
            }

            checkCell.setChecked(newIsChecked);
        });
        linearLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        updateEnableForChatInfo(false);
        loadReaction(false);
        return linearLayout;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                info = chatFull;
                updateEnableForChatInfo(true);
                updateReactionsForInfo();
            }
        }
    }

    private void updateEnableForChatInfo(boolean animated) {
        boolean isEnabled = false;
        if (info != null) {
            isEnabled = !info.available_reactions.isEmpty();
        }
        setReactionsCheck(isEnabled, animated);
    }

    private void loadReaction(boolean force) {
        TLRPC.TL_messages_getAvailableReactions availableReactions = new TLRPC.TL_messages_getAvailableReactions();
        if (System.currentTimeMillis() - lastTimeReactionsLoaded < 3600 * 1000L && !reactions.isEmpty() && !force) {
            availableReactions.hash = 1;
        }
        getConnectionsManager().sendRequest(availableReactions, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_messages_availableReactions) {
                lastTimeReactionsLoaded = System.currentTimeMillis();
                TLRPC.TL_messages_availableReactions reactionsResult = (TLRPC.TL_messages_availableReactions) response;
                reactions.clear();
                reactions.addAll(reactionsResult.reactions);
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        }));
    }

    private void saveReactions() {
        TLRPC.TL_messages_setChatAvailableReactions setChatAvailableReactions = new TLRPC.TL_messages_setChatAvailableReactions();
        boolean enableReactions = enableReactionsCheckCell.isChecked();
        if (enableReactions) {
            setChatAvailableReactions.available_reactions = availableReactions;
        } else {
            setChatAvailableReactions.available_reactions = new ArrayList<>();
        }
        setChatAvailableReactions.peer = getMessagesController().getInputPeer(-chatId);
        getConnectionsManager().sendRequest(setChatAvailableReactions, (response, error) -> {
            if (response instanceof TLRPC.TL_updates) {
                TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
                getMessagesController().processUpdates(updates, false);
                AndroidUtilities.runOnUIThread(() -> {
                    if (enableReactions) {
                        info.available_reactions = availableReactions;
                    } else {
                        info.available_reactions = new ArrayList<>();
                    }
                    getMessagesController().putChatFull(info);
                    getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, false);
                });
            }
            if (error != null && error.code == 400 && error.text.equals("REACTION_INVALID")) {
                getMessagesController().loadFullChat(chatId, 0, true);
                loadReaction(true);
            }
        });
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        saveReactions();
    }

    private void setReactionsCheck(boolean isChecked, boolean animated) {
        if (animated) {
            enableReactionsCheckCell.setBackgroundColorAnimated(isChecked, Theme.getColor(isChecked ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
        } else {
            enableReactionsCheckCell.setBackgroundColor(Theme.getColor(isChecked ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
        }
        enableReactionsCheckCell.setChecked(isChecked);
        setListViewVisibility(isChecked, animated);
    }

    private void setListViewVisibility(boolean isVisible, boolean animated) {
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        if (animated) {
            animatorSet = new AnimatorSet();

            if (isVisible) {
                headerCell.setVisibility(View.VISIBLE);
                listView.setVisibility(View.VISIBLE);
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(headerCell, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(listView, View.ALPHA, 1.0f)
                );
            } else {
                headerCell.setVisibility(View.VISIBLE);
                listView.setVisibility(View.VISIBLE);
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(headerCell, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(listView, View.ALPHA, 0.0f)
                );
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        headerCell.setVisibility(View.INVISIBLE);
                        listView.setVisibility(View.INVISIBLE);
                    }
                });
            }
            animatorSet.setDuration(180);
            animatorSet.start();
        } else {
            if (isVisible) {
                headerCell.setAlpha(1f);
                headerCell.setVisibility(View.VISIBLE);
                listView.setAlpha(1f);
                listView.setVisibility(View.VISIBLE);
            } else {
                headerCell.setVisibility(View.INVISIBLE);
                listView.setVisibility(View.INVISIBLE);
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new ReactionCheckCell(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ReactionCheckCell checkCell = (ReactionCheckCell) holder.itemView;
            boolean isChecked = availableReactions.stream().anyMatch(e -> e.equals(reactions.get(position).reaction));
            checkCell.setReaction(reactions.get(position), isChecked, position != reactions.size() - 1);
        }

        @Override
        public int getItemCount() {
            return reactions.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }
    }

    private static class ReactionCheckCell extends FrameLayout {
        private ImageView imageView;
        private TextCheckCell checkCell;
        boolean isChecked;

        public ReactionCheckCell(Context context) {
            super(context);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

            checkCell = new TextCheckCell(context, 16);
            addView(checkCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 58, 0, 0, 0));
        }

        public void setReaction(TLRPC.TL_availableReaction reaction, boolean isChecked, boolean divider) {
            Emoji.EmojiDrawable drawable = Emoji.getEmojiDrawable(reaction.reaction);
            if (drawable != null) {
                drawable.setBounds(0, 0, AndroidUtilities.dp(32), AndroidUtilities.dp(32));
                drawable.preload();
            }
            imageView.setImageDrawable(drawable);
            checkCell.setTextAndCheck(reaction.title, isChecked, divider);
            this.isChecked = isChecked;
        }

        public void setChecked(boolean isChecked) {
            this.isChecked = isChecked;
            checkCell.setChecked(isChecked);
        }
    }
}
