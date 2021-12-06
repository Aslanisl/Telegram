package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ReactionsMenuView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public interface Delegate {
        void onBackPressed(ReactionsMenuView view);

        void dismiss();
    }

    private static int tabHeight = 32;
    private static int tabPadding = 5;
    private static int cellHeight = 56;

    private static class ReactionsEntity {

        public int count;
        public ArrayList<TLRPC.TL_messageUserReaction> userReactions;
        public ArrayList<TLRPC.User> users;
        public String next_offset;

        public ReactionsEntity(TLRPC.TL_messages_messageReactionsList reactionsList) {
            count = reactionsList.count;
            userReactions = reactionsList.reactions;
            users = reactionsList.users;
            next_offset = reactionsList.next_offset;
        }

        public void addReactionsList(TLRPC.TL_messages_messageReactionsList reactionsList) {
            count = reactionsList.count;
            if (reactionsList.reactions != null) {
                userReactions.addAll(reactionsList.reactions);
            }
            if (reactionsList.users != null) {
                users.addAll(reactionsList.users);
            }
            next_offset = reactionsList.next_offset;
        }

        public boolean isLoading() {
            return userReactions == null || userReactions.size() < count;
        }
    }

    public static final Property<UserCell, Float> USER_CELL_PROPERTY = new AnimationProperties.FloatProperty<UserCell>("placeholderAlpha") {
        @Override
        public void setValue(UserCell object, float value) {
            object.setPlaceholderAlpha(value);
        }

        @Override
        public Float get(UserCell object) {
            return object.getPlaceholderAlpha();
        }
    };

    private RecyclerListView listView;
    private View actionBarShadow;
    private ActionBar actionBar;
    private AnimatorSet actionBarAnimation;

    private ChatActivity chatActivity;
    private MessageObject messageObject;
    private TLRPC.InputPeer peer;
    private HashSet<ReactionsEntity> loadingMore = new HashSet<>();

    private ArrayList<ReactionsEntity> reactionsEntities = new ArrayList<>();

    private int scrollOffsetY;

    private ArrayList<Integer> queries = new ArrayList<>();

    private Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient placeholderGradient;
    private Matrix placeholderMatrix;
    private float totalTranslation;
    private float gradientWidth;
    private boolean loadingResults = true;
    private ScrollSlidingTabStrip stickersTab;

    private Delegate delegate;

    private ViewPagerAdapter viewPagerAdapter;

    private RecyclerListView tabListView;
    private TabAdapter tabAdapter;

    private class TabEntity {
        TLRPC.TL_availableReaction availableReaction;
        Drawable iconDrawable;
        int count;
        String reaction;
    }

    private int selectedTab = 0;
    private int currentAccount;
    private ViewPager viewPager;
    private ArrayList<TabEntity> tabEntities = new ArrayList<>();
    private HashMap<String, RecyclerListView> reactionListViews = new HashMap();
    private HashMap<String, ReactionAdapter> reactionListAdapter = new HashMap();
    private HashMap<String, ReactionsEntity> reactionEntities = new HashMap();
    private HashSet<String> reqIds = new HashSet<>();
    private TLRPC.TL_availableReaction availableReaction;

    public ReactionsMenuView(ChatActivity parentFragment, MessageObject message, int currentAccount, @Nullable TLRPC.TL_availableReaction availableReaction, Delegate delegate) {
        super(parentFragment.contentView.getContext());
        this.delegate = delegate;
        messageObject = message;
        chatActivity = parentFragment;
        this.currentAccount = currentAccount;
        Context context = parentFragment.getParentActivity();
        this.availableReaction = availableReaction;

        setWillNotDraw(false);
        updatePlaceholder();

        setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
        Drawable shadowDrawable2 = ContextCompat.getDrawable(parentFragment.contentView.getContext(), R.drawable.popup_fixed_alert).mutate();
        shadowDrawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        setBackground(shadowDrawable2);

        if (availableReaction == null) {
            actionBar = new ActionBar(context);
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitleColor(Theme.getColor(Theme.key_dialogTextBlack));
            actionBar.setItemsColor(Theme.getColor(Theme.key_dialogTextBlack), false);
            actionBar.setOccupyStatusBar(false);
            actionBar.setTitle(LocaleController.getString("Back", R.string.Back));
            addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1 && delegate != null) {
                        delegate.onBackPressed(ReactionsMenuView.this);
                    }
                }
            });

            if (messageObject == null || !messageObject.hasReactions()) {
                return;
            }

            tabListView = new RecyclerListView(getContext());
            tabListView.setItemAnimator(null);
            tabListView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            tabListView.setAdapter(tabAdapter = new TabAdapter());
            tabListView.setPadding(AndroidUtilities.dp(tabPadding + tabPadding), AndroidUtilities.dp(tabPadding), AndroidUtilities.dp(tabPadding), AndroidUtilities.dp(tabPadding));
            tabListView.setOnItemClickListener((view, position) -> {
                viewPager.setCurrentItem(position, true);
            });
            addView(tabListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, tabHeight + 2 * tabPadding, Gravity.LEFT, 0, ActionBar.getCurrentActionBarHeightDP(), 0, 0));
        }

        viewPager = new ViewPager(getContext());
        viewPager.setAdapter(viewPagerAdapter = new ViewPagerAdapter());
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                selectedTab = position;
                tabListView.smoothScrollToPosition(position);
                if (tabAdapter != null) {
                    tabAdapter.notifyDataSetChanged();
                }
            }
        });

        if (availableReaction == null) {
            addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, cellHeight * 7.5f, Gravity.LEFT, 0, ActionBar.getCurrentActionBarHeightDP() + tabHeight + 2 * tabPadding, 0, 0));
        }

        if (availableReaction == null) {
            actionBarShadow = new View(context);
            actionBarShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
            addView(actionBarShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.LEFT, 0, ActionBar.getCurrentActionBarHeightDP() + tabHeight + 2 * tabPadding, 0, 0));
        }
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
        ArrayList<TLRPC.TL_availableReaction> currentReactions = new ArrayList<>();
        ArrayList<Integer> currentReactionsCount = new ArrayList<>();

        for (int i = 0; i < messageObject.messageOwner.reactions.results.size(); i++) {
            TLRPC.TL_reactionCount reactionCount = messageObject.messageOwner.reactions.results.get(i);
            if (availableReaction == null) {
                reactionsCount += reactionCount.count;
            } else {
                if (reactionCount.reaction.equals(availableReaction.reaction)) {
                    reactionsCount += reactionCount.count;
                }
            }

            for (int a = 0; a < reactions.size(); a++) {
                TLRPC.TL_availableReaction availableReaction = reactions.get(a);
                if (availableReaction.reaction.equals(reactionCount.reaction)) {
                    currentReactions.add(availableReaction);
                    currentReactionsCount.add(reactionCount.count);
                    break;
                }
            }
        }

        createListViewForReaction(availableReaction, reactionsCount, availableReaction != null);

        if (availableReaction == null) {
            for (int i = 0; i < currentReactions.size(); i++) {
                TLRPC.TL_availableReaction currentReaction = currentReactions.get(i);
                Integer currentCount = currentReactionsCount.get(i);
                createListViewForReaction(currentReaction, currentCount, false);
            }
        }

        if (tabAdapter != null) {
            tabAdapter.notifyDataSetChanged();
        }
        if (viewPagerAdapter != null) {
            viewPagerAdapter.notifyDataSetChanged();
        }
    }

    private void loadMessageReactionsList(String reaction, String next_offset) {
        if (reqIds.contains(reaction)) {
            return;
        }
        reqIds.add(reaction);
        TLRPC.TL_messages_getMessageReactionsList req = new TLRPC.TL_messages_getMessageReactionsList();
        req.peer = chatActivity.getMessagesController().getInputPeer((int) messageObject.getDialogId());
        req.id = messageObject.getId();
        if (reaction != null && !reaction.equals("null")) {
            req.limit = 50;
            req.reaction = reaction;
            req.flags |= 1;
        } else {
            req.limit = 100;
        }
        if (next_offset != null) {
            req.offset = next_offset;
            req.flags |= 2;
        }
        chatActivity.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                TLRPC.TL_messages_messageReactionsList res = (TLRPC.TL_messages_messageReactionsList) response;
                chatActivity.getMessagesController().putUsers(res.users, false);
                addReactions(reaction, res);
                ReactionAdapter adapter = reactionListAdapter.get(reaction);
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
            reqIds.remove(reaction);
        }));
    }

    private void addReactions(String reaction, TLRPC.TL_messages_messageReactionsList messageReactionsList) {
        ReactionsEntity reactionEntity = reactionEntities.get(reaction);
        if (reactionEntity == null) {
            reactionEntity = new ReactionsEntity(messageReactionsList);
        } else {
            reactionEntity.addReactionsList(messageReactionsList);
        }
        reactionEntities.put(reaction, reactionEntity);
    }

    private class ViewPagerAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return tabEntities.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return object == view;
        }

        @Override
        public int getItemPosition(Object object) {
            return POSITION_UNCHANGED;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view = reactionListViews.get(tabEntities.get(position).reaction);
            loadMessageReactionsList(tabEntities.get(position).reaction, null);
            container.addView(view);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }

    private void createListViewForReaction(TLRPC.TL_availableReaction availableReaction, int count, boolean exacltySize) {
        String reaction = availableReaction == null ? "null" : availableReaction.reaction;
        RecyclerListView listView = new RecyclerListView(getContext());
        if (exacltySize) {
            addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, count > 7 ? cellHeight * 7.5f : cellHeight * count));
            requestLayout();
        }
        listView.setClipToPadding(false);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);

        ReactionAdapter adapter = new ReactionAdapter(reaction);
        listView.setAdapter(adapter);
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        listView.setOnItemClickListener((view, position) -> {
            if (chatActivity == null || chatActivity.getParentActivity() == null || queries != null && !queries.isEmpty()) {
                return;
            }
            UserCell userCell = (UserCell) view;
            if (userCell.currentUser == null) {
                return;
            }
            if (delegate != null) {
                delegate.dismiss();
            }
            TLRPC.User currentUser = chatActivity.getCurrentUser();
            Bundle args = new Bundle();
            args.putLong("user_id", userCell.currentUser.id);
            ProfileActivity fragment = new ProfileActivity(args);
            fragment.setPlayProfileAnimation(currentUser != null && currentUser.id == userCell.currentUser.id ? 1 : 0);
            chatActivity.presentFragment(fragment);
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int position = layoutManager.findLastVisibleItemPosition();
                if (position != RecyclerView.NO_POSITION) {
                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(position);
                    if (holder != null && holder.itemView instanceof UserCell && ((UserCell) holder.itemView).isLoading()) {
                        ReactionsEntity entity = reactionEntities.get(reaction);
                        if (entity != null && entity.isLoading()) {
                            loadMessageReactionsList(reaction, entity.next_offset);
                        }
                    }
                }
            }
        });

        TabEntity entity = new TabEntity();
        entity.count = count;
        reactionListViews.put(reaction, listView);
        reactionListAdapter.put(reaction, adapter);
        if (availableReaction == null) {
            Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.actions_reactions).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.chat_reactionTextPaint.getColor(), PorterDuff.Mode.MULTIPLY));
            entity.iconDrawable = drawable;
        } else {
            entity.availableReaction = availableReaction;
        }
        entity.reaction = reaction;
        tabEntities.add(entity);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && delegate != null) {
            delegate.onBackPressed(this);
        }
        return super.dispatchKeyEvent(event);
    }

    private class TabAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new TabCell(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TabCell cell = (TabCell) holder.itemView;
            TabEntity entity = tabEntities.get(position);
            if (entity.availableReaction != null) {
                cell.setReaction(entity.availableReaction, entity.count, position == selectedTab);
            } else {
                cell.setDrawable(entity.iconDrawable, entity.count, position == selectedTab);
            }
        }

        @Override
        public int getItemCount() {
            return tabEntities.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }
    }

    public class UserCell extends FrameLayout {

        private BackupImageView avatarImageView;
        private BackupImageView reactionImageView;
        private SimpleTextView nameTextView;

        private TLRPC.TL_messageUserReaction currentReaction;

        private AvatarDrawable avatarDrawable;
        private TLRPC.User currentUser;

        private String lastName;
        private int lastStatus;
        private TLRPC.FileLocation lastAvatar;

        private int currentAccount = UserConfig.selectedAccount;

        private boolean drawPlaceholder;
        private float placeholderAlpha = 1.0f;

        private ValueAnimator animator;
        private RectF rect = new RectF();

        public UserCell(Context context) {
            super(context);

            setWillNotDraw(false);

            avatarDrawable = new AvatarDrawable();

            avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(18));
            addView(avatarImageView, LayoutHelper.createFrame(36, 36, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 0 : 2 * tabPadding, 0, LocaleController.isRTL ? 2 * tabPadding : 0, 0));

            reactionImageView = new BackupImageView(context);
            reactionImageView.setRoundRadius(AndroidUtilities.dp(12));
            addView(reactionImageView, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 3 * tabPadding : 0, 0, LocaleController.isRTL ? 0 : 3 * tabPadding, 0));

            nameTextView = new SimpleTextView(context);
            nameTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            nameTextView.setTextSize(16);
            nameTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 52 : 58, 0, LocaleController.isRTL ? 58 : 52, 0));
        }

        public void setData(TLRPC.User user, TLRPC.TL_messageUserReaction reaction) {
            currentUser = user;
            currentReaction = reaction;
            drawPlaceholder = user == null;

            if (user == null) {
                nameTextView.setText("");
                avatarImageView.setImageDrawable(null);
                reactionImageView.setImageDrawable(null);
            } else {
                update(0);
            }
            if (animator != null) {
                animator.cancel();
            }
            if (user == null) {
                animator = ObjectAnimator.ofFloat(this, USER_CELL_PROPERTY, 1.0f, 0.4f);
                animator.setDuration(800);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setRepeatMode(ValueAnimator.REVERSE);
                animator.start();
            } else {
                setPlaceholderAlpha(0f);
            }
        }

        public boolean isLoading() {
            return drawPlaceholder;
        }

        @Keep
        public void setPlaceholderAlpha(float value) {
            placeholderAlpha = value;
            invalidate();
        }

        @Keep
        public float getPlaceholderAlpha() {
            return placeholderAlpha;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellHeight), MeasureSpec.EXACTLY));
        }

        public void update(int mask) {
            TLRPC.FileLocation photo = null;
            String newName = null;
            if (currentUser != null && currentUser.photo != null) {
                photo = currentUser.photo.photo_small;
            }

            if (mask != 0) {
                boolean continueUpdate = false;
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                    if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                        continueUpdate = true;
                    }
                }
                if (currentUser != null && !continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    int newStatus = 0;
                    if (currentUser.status != null) {
                        newStatus = currentUser.status.expires;
                    }
                    if (newStatus != lastStatus) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                    if (currentUser != null) {
                        newName = UserObject.getUserName(currentUser);
                    }
                    if (!newName.equals(lastName)) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate) {
                    return;
                }
            }

            avatarDrawable.setInfo(currentUser);
            if (currentUser.status != null) {
                lastStatus = currentUser.status.expires;
            } else {
                lastStatus = 0;
            }

            if (currentUser != null) {
                lastName = newName == null ? UserObject.getUserName(currentUser) : newName;
            } else {
                lastName = "";
            }
            nameTextView.setText(lastName);

            lastAvatar = photo;
            if (currentUser != null) {
                avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
            } else {
                avatarImageView.setImageDrawable(avatarDrawable);
            }

            if (currentReaction != null) {
                ArrayList<TLRPC.TL_availableReaction> reactions = MessagesController.getInstance(currentAccount).getAvailableReactions();
                TLRPC.TL_availableReaction reaction = null;
                for (int i = 0; i < reactions.size(); i++) {
                    TLRPC.TL_availableReaction reactionIterate = reactions.get(i);
                    if (reactionIterate.reaction.equals(currentReaction.reaction)) {
                        reaction = reactionIterate;
                        break;
                    }
                }
                if (reaction != null) {
                    TLRPC.Document document = reaction.static_icon;
                    String imageFilter = "16x16";
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                    SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);
                    if (svgThumb != null) {
                        reactionImageView.setImage(ImageLocation.getForDocument(document), imageFilter, null, svgThumb, document);
                    } else if (thumb != null) {
                        reactionImageView.setImage(ImageLocation.getForDocument(document), imageFilter, ImageLocation.getForDocument(thumb, document), null, 0, document);
                    } else {
                        reactionImageView.setImage(ImageLocation.getForDocument(document), imageFilter, null, null, document);
                    }
                }
            }
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (drawPlaceholder || placeholderAlpha != 0) {
                placeholderPaint.setAlpha((int) (255 * placeholderAlpha));
                int cx = avatarImageView.getLeft() + avatarImageView.getMeasuredWidth() / 2;
                int cy = avatarImageView.getTop() + avatarImageView.getMeasuredHeight() / 2;
                canvas.drawCircle(cx, cy, avatarImageView.getMeasuredWidth() / 2f, placeholderPaint);

                int w;

                cx = AndroidUtilities.dp(65);
                w = AndroidUtilities.dp(48);

                if (LocaleController.isRTL) {
                    cx = getMeasuredWidth() - cx - w;
                }
                rect.set(cx, cy - AndroidUtilities.dp(4), cx + w, cy + AndroidUtilities.dp(4));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), placeholderPaint);

                cx = AndroidUtilities.dp(119);
                w = AndroidUtilities.dp(60);
                if (LocaleController.isRTL) {
                    cx = getMeasuredWidth() - cx - w;
                }
                rect.set(cx, cy - AndroidUtilities.dp(4), cx + w, cy + AndroidUtilities.dp(4));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), placeholderPaint);
            }
        }
    }

    private class TabCell extends FrameLayout {
        TextView titleView;
        BackupImageView leftImageView;
        ImageView leftIconView;
        RectF rect = new RectF();
        boolean isSelected;

        public TabCell(@NonNull Context context) {
            super(context);
            setPadding(AndroidUtilities.dp(tabPadding), 0, AndroidUtilities.dp(tabPadding + tabPadding), 0);

            leftImageView = new BackupImageView(context);
            leftImageView.setRoundRadius(AndroidUtilities.dp(8));
            addView(leftImageView, LayoutHelper.createFrame(16, 16, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            leftIconView = new ImageView(context);
            addView(leftIconView, LayoutHelper.createFrame(16, 16, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, AndroidUtilities.dp(14));
            titleView.setTextColor(Theme.chat_reactionTextPaint.getColor());
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 21, 0, tabPadding, 0));

            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(tabHeight), MeasureSpec.EXACTLY));
        }

        private void setReaction(TLRPC.TL_availableReaction reaction, int count, boolean isSelected) {
            this.isSelected = isSelected;

            leftIconView.setAlpha(0f);
            leftImageView.setAlpha(1f);

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
            titleView.setText(String.format("%d", count));
        }

        private void setDrawable(Drawable drawable, int count, boolean isSelected) {
            this.isSelected = isSelected;

            leftIconView.setAlpha(1f);
            leftImageView.setAlpha(0f);

            leftIconView.setImageDrawable(drawable);
            titleView.setText(String.format("%d", count));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            rect.set(AndroidUtilities.dp(1), AndroidUtilities.dp(1), getMeasuredWidth() - AndroidUtilities.dp(tabPadding) - AndroidUtilities.dp(1), getMeasuredHeight() - AndroidUtilities.dp(1));
            canvas.drawRoundRect(rect, getMeasuredHeight() / 2f, getMeasuredHeight() / 2f, Theme.getThemePaint(Theme.key_paint_chatReactionBackground));
            if (isSelected) {
                canvas.drawRoundRect(rect, getMeasuredHeight() / 2f, getMeasuredHeight() / 2f, Theme.getThemePaint(Theme.key_paint_chatReactionBackgroundSelected));
            }
            super.onDraw(canvas);
        }
    }

    private class ReactionAdapter extends RecyclerListView.SelectionAdapter {

        private String reaction;

        ReactionAdapter(String reaction) {
            this.reaction = reaction;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new UserCell(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            UserCell userCell = (UserCell) holder.itemView;
            TLRPC.TL_messageUserReaction userReaction = getUserReaction(holder.getAdapterPosition());
            TLRPC.User user;
            if (userReaction != null && userReaction.user_id != 0) {
                user = chatActivity.getMessagesController().getUser(userReaction.user_id);
            } else {
                user = null;
            }
            userCell.setData(user, userReaction);
        }

        private TLRPC.TL_messageUserReaction getUserReaction(int position) {
            ReactionsEntity reactionsEntity = reactionEntities.get(reaction);
            if (reactionsEntity != null && reactionsEntity.userReactions != null && position < reactionsEntity.userReactions.size()) {
                return reactionsEntity.userReactions.get(position);
            }

            return null;
        }

        @Override
        public int getItemCount() {
            ReactionsEntity reactionsEntity = reactionEntities.get(reaction);
            if (reactionsEntity != null) {
                return reactionsEntity.count;
            }
            return 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }
    }

    private void updatePlaceholder() {
        if (placeholderPaint == null) {
            return;
        }
        int color0 = Theme.getColor(Theme.key_dialogBackground);
        int color1 = Theme.getColor(Theme.key_dialogBackgroundGray);
        color0 = AndroidUtilities.getAverageColor(color1, color0);
        placeholderPaint.setColor(color1);
        placeholderGradient = new LinearGradient(0, 0, gradientWidth = AndroidUtilities.dp(500), 0, new int[]{color1, color0, color1}, new float[]{0.0f, 0.18f, 0.36f}, Shader.TileMode.REPEAT);
        placeholderPaint.setShader(placeholderGradient);
        placeholderMatrix = new Matrix();
        placeholderGradient.setLocalMatrix(placeholderMatrix);
    }
}