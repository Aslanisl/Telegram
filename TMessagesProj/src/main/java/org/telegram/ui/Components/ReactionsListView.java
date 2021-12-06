package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ReactionCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;

public class ReactionsListView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private static final int padding = 8;
    private static final int contentHeight = 56;
    private static final int stickerSize = 32;
    private static final int stickerWidth = 40;

    public class BackgroundDrawable extends Drawable {

        private Paint bgPaint, shadowPaint;
        private Bitmap shadowBitmap;
        private RectF rectF = new RectF();

        public BackgroundDrawable() {
            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
            shadowPaint = new Paint();
            shadowPaint.setColor(0x4C000000);
        }

        @Override
        public void draw(Canvas canvas) {
            if (shadowBitmap == null)
                onBoundsChange(getBounds());
            if (shadowBitmap != null)
                canvas.drawBitmap(shadowBitmap, 0, 0, shadowPaint);

            drawBackground(canvas, getBounds(), bgPaint);
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            if (bounds.width() <= 0) {
                shadowBitmap = null;
                return;
            }
            shadowBitmap = Bitmap.createBitmap(bounds.width(), AndroidUtilities.dp(78), Bitmap.Config.ALPHA_8);
            Canvas c = new Canvas(shadowBitmap);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShadowLayer(AndroidUtilities.dp(3.33333f), 0, AndroidUtilities.dp(0.666f), 0xFFFFFFFF);
            drawBackground(c, bounds, p);
        }

        private void drawBackground(Canvas canvas, Rect bounds, Paint paint) {
            rectF.set(AndroidUtilities.dp(0), AndroidUtilities.dp(padding), bounds.width() - AndroidUtilities.dp(0), AndroidUtilities.dp(contentHeight + padding));
            canvas.drawCircle(bounds.width() - AndroidUtilities.dp(48), AndroidUtilities.dp(contentHeight + padding - 2), AndroidUtilities.dp(8), paint);
            canvas.drawCircle(bounds.width() - AndroidUtilities.dp(42), AndroidUtilities.dp(81), AndroidUtilities.dp(5), paint);
            canvas.drawRoundRect(rectF, bounds.height() / 2f, bounds.height() / 2f, paint);
        }

        @Override
        public boolean getPadding(Rect rect) {
            rect.set(AndroidUtilities.dp(padding), AndroidUtilities.dp(padding), AndroidUtilities.dp(padding), AndroidUtilities.dp(padding));
            return true;
        }
    }

    public interface Delegate {
        void dismissPopup();
    }

    private RecyclerListView listView;
    private ListAdapter adapter;
    private int currentAccount;
    private TLRPC.ChatFull chatFull;
    private final ArrayList<TLRPC.TL_availableReaction> availableReactions = new ArrayList<>();
    private AnimatorSet animator;
    private ActionBarPopupWindow popupWindow;
    private FrameLayout popupLayout;
    private BackgroundDrawable backgroundDrawable;
    private ChatMessageCell messageCell;

    public ReactionsListView(SizeNotifierFrameLayout contentView, int currentAccount, @NonNull View targetView, @NonNull ChatActivity parentFragment, @NonNull Delegate delegate) {
        super(contentView.getContext());
        MessageObject msg = null;
        if (targetView instanceof ChatMessageCell) {
            msg = ((ChatMessageCell) targetView).getMessageObject();
            messageCell = (ChatMessageCell) targetView;
        } else if (targetView instanceof ChatActionCell) {
            msg = ((ChatActionCell) targetView).getMessageObject();
        }
        if (msg == null) {
            return;
        }
        MessageObject finalMsg = msg;
        setWillNotDraw(false);
        setPadding(0, AndroidUtilities.dp(padding), 0, AndroidUtilities.dp(padding));
        setClipToPadding(false);

        this.currentAccount = currentAccount;
        if (msg.getChatId() != 0) {
            this.chatFull = MessagesController.getInstance(currentAccount).getChatFull(finalMsg.getChatId());
        }

        listView = new RecyclerListView(contentView.getContext());
        listView.setAdapter(adapter = new ListAdapter());
        listView.setLayoutManager(new LinearLayoutManager(contentView.getContext(), LinearLayoutManager.HORIZONTAL, false));
        listView.setOnItemClickListener((view, position) -> {
            TLRPC.TL_availableReaction reaction = availableReactions.get(position);
            if (messageCell != null) {
                messageCell.hideReaction(reaction.reaction);
            }
            SendMessagesHelper.getInstance(currentAccount).sendReaction(finalMsg, reaction.reaction, parentFragment);
            showStickerToContentViewWithAnimation(contentView, view, reaction);
            delegate.dismissPopup();
        });
        listView.setFadingEdgeLength(AndroidUtilities.dp(16));
        listView.setHorizontalFadingEdgeEnabled(true);

        if (Build.VERSION.SDK_INT >= 21) {
            listView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
                }
            });
        }

        setMinimumWidth(AndroidUtilities.dp(stickerWidth * 2));
        listView.setClipToPadding(false);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, contentHeight, Gravity.CENTER));
        backgroundDrawable = new BackgroundDrawable();
        updateForReactions(MessagesController.getInstance(currentAccount).getAvailableReactions());
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
            updateForReactions(reactions);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(stickerWidth * 6.5f), View.MeasureSpec.AT_MOST), heightMeasureSpec);
        backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    private void updateForReactions(ArrayList<TLRPC.TL_availableReaction> reactions) {
        availableReactions.clear();
        if (chatFull != null && chatFull.available_reactions != null) {
            for (TLRPC.TL_availableReaction reaction : reactions) {
                if (chatFull.available_reactions.stream().anyMatch(r -> r.equals(reaction.reaction))) {
                    availableReactions.add(reaction);
                }
            }
        } else {
            availableReactions.addAll(reactions);
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void showStickerToContentViewWithAnimation(SizeNotifierFrameLayout contentView, View currentView, TLRPC.TL_availableReaction reaction) {
        if (animator != null) {
            animator.cancel();
        }
        if (popupWindow != null) {
            popupWindow.dismiss();
        }
        animator = new AnimatorSet();
        animator.setDuration(180);

        popupLayout = new FrameLayout(getContext()) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent keyEvent) {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && popupWindow != null && popupWindow.isShowing()) {
                    popupWindow.dismiss();
                }
                return super.dispatchKeyEvent(keyEvent);
            }
        };
        popupLayout.setOnTouchListener((v, event) -> {
            if (popupWindow != null && popupWindow.isShowing()) {
                popupWindow.dismiss();
            }
            return false;
        });

        final FrameLayout contentLayout = new FrameLayout(getContext());
        ReactionCell selectReactionCell = new ReactionCell(getContext(), 150, 150, 150);
        selectReactionCell.imageView.getImageReceiver().setAutoRepeat(0);
        selectReactionCell.setDocument(reaction.activate_animation, reaction.reaction, "100x100", false);
        contentLayout.addView(selectReactionCell, LayoutHelper.createFrame(150, 150, Gravity.CENTER));

        final ReactionCell activateAnimationCell = new ReactionCell(getContext(), 300, 300, 300);
        activateAnimationCell.setDelegate(() -> {
            ChatMessageCell.ReactionButton reactionButton = null;
            if (messageCell != null) {
                reactionButton = messageCell.getReactionButtonForReaction(reaction.reaction);
            }
            if (reactionButton != null && contentLayout.getParent() instanceof ViewGroup) {
                if (animator != null) {
                    animator.cancel();
                }
                animator = new AnimatorSet();
                animator.setDuration(100);
                int[] contentViewLocation = new int[2];
                contentLayout.getLocationOnScreen(contentViewLocation);

                int[] messageCellViewLocation = new int[2];
                messageCell.getLocationOnScreen(messageCellViewLocation);

                float scale = reactionButton.elementSize / 300f;

                float translationX = messageCellViewLocation[0] - contentViewLocation[0] - (contentLayout.getWidth() / 2f) + reactionButton.x + messageCell.getReactionsAddX() + reactionButton.elementSize - (reactionButton.margin / 2f);
                float translationY = messageCellViewLocation[1] - contentViewLocation[1] - (contentLayout.getHeight() / 2f) + reactionButton.y + reactionButton.elementSize - (reactionButton.margin / 2f);
                animator.playTogether(
                        ObjectAnimator.ofFloat(contentLayout, View.SCALE_X, scale),
                        ObjectAnimator.ofFloat(contentLayout, View.SCALE_Y, scale),
                        ObjectAnimator.ofFloat(contentLayout, View.TRANSLATION_X, translationX),
                        ObjectAnimator.ofFloat(contentLayout, View.TRANSLATION_Y, translationY)
                );
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation, boolean isReverse) {
                        if (popupWindow != null && popupWindow.isShowing()) {
                            popupWindow.dismiss();
                        }
                    }
                });
                animator.start();
            } else if (popupWindow != null && popupWindow.isShowing()) {
                popupWindow.dismiss();
            }
        });
        activateAnimationCell.imageView.getImageReceiver().setAutoRepeat(0);
        activateAnimationCell.setDocument(reaction.effect_animation, reaction.reaction, "200x200", false);
        contentLayout.addView(activateAnimationCell, LayoutHelper.createFrame(300, 300, Gravity.CENTER));

        popupLayout.addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        int[] currentViewLocation = new int[2];
        currentView.getLocationOnScreen(currentViewLocation);

        int[] contentViewLocation = new int[2];
        contentView.getLocationOnScreen(contentViewLocation);

        float scale = stickerSize / 150f;
        float translationX = currentViewLocation[0] - contentViewLocation[0];
        float translationY = currentViewLocation[1] - contentViewLocation[1];

        contentLayout.setScaleX(scale);
        contentLayout.setScaleY(scale);
        contentLayout.setTranslationX(-contentView.getMeasuredWidth() / 2f + translationX + AndroidUtilities.dp(28));
        contentLayout.setTranslationY(-(contentView.getMeasuredHeight()) / 2f + translationY + AndroidUtilities.dp(10));
        animator.playTogether(
                ObjectAnimator.ofFloat(contentLayout, View.SCALE_X, 1f),
                ObjectAnimator.ofFloat(contentLayout, View.SCALE_Y, 1f),
                ObjectAnimator.ofFloat(contentLayout, View.TRANSLATION_X, 0f),
                ObjectAnimator.ofFloat(contentLayout, View.TRANSLATION_Y, 0f)
        );

        popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(false);
        popupWindow.setFocusable(true);
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        popupWindow.getContentView().setFocusableInTouchMode(true);
        popupWindow.setAnimationStyle(0);
        popupWindow.setAnimationEnabled(false);
        popupWindow.setOnDismissListener(() -> {
            popupWindow = null;
            popupLayout = null;
            if (messageCell != null) {
                messageCell.showReaction(reaction.reaction);
            }
        });
        currentView.setVisibility(View.INVISIBLE);
        animator.start();
        popupWindow.showAtLocation(contentView, Gravity.LEFT | Gravity.TOP, 0, 0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        backgroundDrawable.draw(canvas);
        super.onDraw(canvas);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new ReactionCell(parent.getContext(), stickerSize, stickerWidth, contentHeight));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ReactionCell cell = (ReactionCell) holder.itemView;
            TLRPC.TL_availableReaction availableReaction = availableReactions.get(position);
            cell.setDocument(availableReaction.select_animation, availableReaction.reaction, "32x32", true);
        }

        @Override
        public int getItemCount() {
            return availableReactions.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }
    }
}
