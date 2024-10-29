package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.QuickShareDialogCell;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

import androidx.annotation.NonNull;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.Cells.QuickShareDialogCell.CELL_RADIUS;
import static org.telegram.ui.Cells.QuickShareDialogCell.CELL_SIZE;

public class QuickShareContainerLayout extends FrameLayout {

    private static final int MAX_DIALOGS = 5;

    private static final int CELLS_CONTAINER_PADDING_DP = 6;
    private static final int CELLS_CONTAINER_PADDING = dp(CELLS_CONTAINER_PADDING_DP);
    private static final int TOP_PADDING = dp(0);
    private static final int TOP_PADDING_TOUCH_ADD = dp(6);
    private static final int CELLS_BOTTOM_PADDING = dp(24);
    public static final int CELL_PADDING_DP = 10;
    public static final int CELL_PADDING = dp(10);
    private final static int ANIMATION_DURATION = 700;

    private int currentAccount = UserConfig.selectedAccount;

    private RectF cellsRect = new RectF();
    private RectF rect = new RectF();
    private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
    private ArrayList<QuickShareDialogCell> shareCells = new ArrayList<>();
    protected ArrayList<MessageObject> sendingMessageObjects;

    private final Theme.ResourcesProvider resourcesProvider;

    private int cellsWidth;
    private int cellsHeight;

    private boolean isHandlingTouchEvent = false;
    private boolean blockAttach = false;

    private ChatActivity activity;
    private ChatActivity.ChatActivityFragmentView rootView;
    private ChatMessageCell cell;

    private float[] translationLocation = new float[2];

    private ValueAnimator sendButtonAnimator;
    private float sendButtonFactor;

    private ValueAnimator contentAnimator;
    private float contentFactor;

    private ValueAnimator hideAnimation;
    private float hideAnimationFactor = 1f;

    private int centerIndex = -1;
    private int centerIndex1 = -1, centerIndex2 = -1;
    private boolean isEven = false;

    private float cellY = 0;
    private float chatListY = 0;
    private float sideButtonSize = dp(32);
    private float sideButtonSizeHalf = sideButtonSize / 2f;
    private float layoutStartX = 0f;
    private float layoutStartY = 0f;

    public QuickShareContainerLayout(@NonNull Context context, ChatActivity activity, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.activity = activity;

        fetchDialogs();

        setClipChildren(false);
        setClipToPadding(false);

        cellsWidth = dialogs.size() * CELL_SIZE + (dialogs.size() - 1) * CELL_PADDING + 2 * (int) CELLS_CONTAINER_PADDING;
        cellsHeight = CELL_SIZE + 2 * (int) CELLS_CONTAINER_PADDING;
        cellsRect.set(0, 0, cellsWidth, cellsHeight);

        bgPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
        circlePaint.setColor(Theme.getColor(Theme.key_chat_messagePanelIcons, resourcesProvider));
    }

    private void fetchDialogs() {
        dialogs.clear();
        long selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
        if (!MessagesController.getInstance(currentAccount).dialogsForward.isEmpty()) {
            TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogsForward.get(0);
            dialogs.add(dialog);
        }
        ArrayList<TLRPC.Dialog> allDialogs = MessagesController.getInstance(currentAccount).getAllDialogs();
        for (int a = 0; a < allDialogs.size(); a++) {
            TLRPC.Dialog dialog = allDialogs.get(a);
            if (!(dialog instanceof TLRPC.TL_dialog)) {
                continue;
            }
            if (dialog.id == selfUserId) {
                continue;
            }
            if (dialogs.size() >= MAX_DIALOGS) {
                break;
            }
            if (!DialogObject.isEncryptedDialog(dialog.id)) {
                if (DialogObject.isUserDialog(dialog.id)) {
                    dialogs.add(dialog);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
                    if (!(chat == null || ChatObject.isNotInChat(chat) || chat.gigagroup && !ChatObject.hasAdminRights(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
                        dialogs.add(dialog);
                    }
                }
            }
        }

        float leftMargin = CELLS_CONTAINER_PADDING;
        int size = Math.min(dialogs.size(), MAX_DIALOGS);
        for (int i = 0; i < size; i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            QuickShareDialogCell cell = new QuickShareDialogCell(getContext(), resourcesProvider);
            cell.setDialog(dialog);
            shareCells.add(cell);
            if (i == size - 1) {
                cell.isLastCell(true);
            }
            cell.setTranslationX(leftMargin);
            cell.setTranslationY(CELLS_CONTAINER_PADDING);
            addView(cell, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            leftMargin += CELL_SIZE + CELL_PADDING;
        }

        updateCenterIndices();
    }

    private void updateCenterIndices() {
        int n = shareCells.size();
        isEven = (n % 2 == 0);
        if (isEven) {
            centerIndex1 = n / 2 - 1;
            centerIndex2 = n / 2;
            centerIndex = -1;
        } else {
            centerIndex = n / 2;
            centerIndex1 = centerIndex2 = -1;
        }
    }

    public void showForPosition(
            ChatActivity.ChatActivityFragmentView rootView,
            ChatMessageCell cell,
            float chatListY,
            float cellY,
            ArrayList<MessageObject> messages
    ) {
        if (getParent() == null || blockAttach) return;

        this.cell = cell;
        this.rootView = rootView;
        this.sendingMessageObjects = messages;
        this.cellY = cellY;
        this.chatListY = chatListY;

        setTranslationX(rootView.getWidth() - cellsWidth - dp(10));
        setTranslationY(cellY + cell.sideStartY - cellsHeight - CELLS_BOTTOM_PADDING);

        hideAnimationFactor = 1f;

        getParent().requestDisallowInterceptTouchEvent(true);

        showWithAnimation();
        cell.hideSideButton = true;
        cell.invalidate();
    }

    private void showWithAnimation() {
        setVisibility(View.VISIBLE);
        if (sendButtonAnimator != null) {
            sendButtonAnimator.cancel();
        }
        sendButtonAnimator = ValueAnimator.ofFloat(0, 1, 0);
        sendButtonAnimator.setDuration(ANIMATION_DURATION);
        sendButtonAnimator.addUpdateListener(animation -> {
            sendButtonFactor = (float) animation.getAnimatedValue();
            invalidate();
        });
        sendButtonAnimator.setInterpolator(new OvershootInterpolator(2.0f));
        sendButtonAnimator.start();

        if (contentAnimator != null) {
            contentAnimator.cancel();
        }
        contentAnimator = ValueAnimator.ofFloat(0, 1);
        contentAnimator.setDuration(ANIMATION_DURATION);
        contentAnimator.addUpdateListener(animation -> {
            contentFactor = (float) animation.getAnimatedValue();
            invalidate();
        });
        contentAnimator.setInterpolator(new OvershootInterpolator(1f));
        contentAnimator.start();
    }

    private void hideWithAnimation() {
//        if (hideAnimation != null) {
//            hideAnimation.cancel();
//        }
//        hideAnimation = ValueAnimator.ofFloat(1, 0);
//        hideAnimation.setDuration(150);
//        hideAnimation.addUpdateListener(animation -> {
//            hideAnimationFactor = (float) animation.getAnimatedValue();
//            if (hideAnimationFactor <= 0.05) {
//                setVisibility(View.GONE);
//            }
//            invalidate();
//        });
//        hideAnimation.start();

        setVisibility(View.GONE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(cellsWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellsHeight, MeasureSpec.EXACTLY)
        );
    }

    public boolean onChatTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                float touchX = event.getX() - getTranslationX() - layoutStartX + CELLS_CONTAINER_PADDING;
                float keyboardHeight = 0;
                if (activity.isKeyboardVisible()) {
                    keyboardHeight = rootView.getKeyboardHeight();
                }
                float touchY = event.getY() - cellY - cell.sideStartY + CELLS_CONTAINER_PADDING - CELL_SIZE - keyboardHeight;

                for (int i = 0; i < shareCells.size(); i++) {
                    QuickShareDialogCell shareCell = shareCells.get(i);
                    float left = shareCell.getX();
                    float top = shareCell.getY() - dp(32);
                    float right = left + CELL_SIZE;
                    float bottom = top + CELL_SIZE + dp(32);

                    boolean isSelected = touchX >= left && touchX <= right && touchY >= top && touchY <= bottom;
                    shareCell.selectCell(isSelected, true);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                blockAttach = true;

                selectCell();
                hideWithAnimation();

                cell.hideSideButton = false;
                cell.invalidate();

                for (QuickShareDialogCell shareCell : shareCells) {
                    shareCell.selectCell(false, false);
                }

                postDelayed(() -> {
                    blockAttach = false;
                }, 300);
                isHandlingTouchEvent = false;
                getParent().requestDisallowInterceptTouchEvent(false);

                return false;
        }
        invalidate();
        return isHandlingTouchEvent;
    }

    private void selectCell() {
        TLRPC.Dialog selectedDialog = null;
        QuickShareDialogCell selectedCell = null;
        for (int i = 0; i < shareCells.size(); i++) {
            QuickShareDialogCell shareCell = shareCells.get(i);
            if (shareCell.isSelected()) {
                selectedCell = shareCell;
                selectedDialog = shareCell.getCurrentDialog();
                break;
            }
        }
        if (selectedDialog == null || sendingMessageObjects == null || selectedCell == null) {
            return;
        }

        QuickShareDialogCell cell = new QuickShareDialogCell(getContext(), resourcesProvider);
        cell.setDialog(selectedDialog);
        cell.setTranslationX(getTranslationX() + selectedCell.getTranslationX());
        cell.setTranslationY(getTranslationY() + selectedCell.getTranslationY());
        cell.selectCell(true, false);
        cell.isHideTitle = true;

        int sendMessagesCount = sendingMessageObjects.size();
        SendMessagesHelper.getInstance(currentAccount).sendMessage(sendingMessageObjects, selectedDialog.id, true, false, true, 0, null);
        onSend(selectedDialog, cell, sendMessagesCount);
    }

    protected void onSend(TLRPC.Dialog selectedDialog, QuickShareDialogCell selectedCell, int count) {

    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (cell == null) return;

        float sideTranslateX = -getTranslationX() + cell.getX() + cell.sideStartX;
        float sideTranslateY = -getTranslationY() + cellY + cell.sideStartY - sideButtonSizeHalf * sendButtonFactor;

        // Draw side button
        canvas.save();

        canvas.translate(sideTranslateX, sideTranslateY);
        canvas.rotate(sendButtonFactor * -45, sideButtonSizeHalf, sideButtonSizeHalf);

        rect.set(0, 0, sideButtonSize, sideButtonSize);

        canvas.drawRoundRect(rect, dp(16), dp(16), getThemedPaint(Theme.key_paint_chatActionBackground));
        if (cell.hasGradientService()) {
            canvas.drawRoundRect(rect, dp(16), dp(16), Theme.chat_actionBackgroundGradientDarkenPaint);
        }
        final int scx = (int) dp(16), scy = (int) dp(16);
        Drawable drawable = getThemedDrawable(Theme.key_drawable_shareIcon);
        final int shw = drawable.getIntrinsicWidth() / 2, shh = drawable.getIntrinsicHeight() / 2;
        drawable.setBounds(scx - shw, scy - shh, scx + shw, scy + shh);
        BaseCell.setDrawableBounds(drawable, dp(4), dp(4));
        drawable.draw(canvas);
        canvas.restore();

        canvas.save();

        float translateRectX = (1 - contentFactor) * sideTranslateX;
        float translateRectY = (1 - contentFactor) * sideTranslateY;
        canvas.translate(translateRectX, translateRectY);

        float currentWidth = sideButtonSize + contentFactor * (cellsWidth - sideButtonSize);
        float currentHeight = sideButtonSize + contentFactor * (cellsHeight - sideButtonSize);

        bgPaint.setAlpha((int) (255 * hideAnimationFactor));
        AndroidUtilities.rectTmp.set(0, 0, currentWidth, currentHeight);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, currentHeight / 2f, currentHeight / 2f, bgPaint);

        for (int i = 0; i < shareCells.size(); i++) {
            canvas.save();
            QuickShareDialogCell shareCell = shareCells.get(i);
            canvas.translate(shareCell.getX() * (currentWidth / cellsWidth), shareCell.getY() * (currentHeight / cellsHeight));
            boolean isCenter;

            if (isEven) {
                isCenter = (i == centerIndex1 || i == centerIndex2);
            } else {
                isCenter = (i == centerIndex);
            }

            float scale;
            if (isCenter) {
                float delayFactor = 0.1f;
                scale = (contentFactor - delayFactor) / (1f - delayFactor);
            } else {
                float delayFactor = Math.abs(i - centerIndex) * 0.35f;
                if (contentFactor <= delayFactor) {
                    scale = 0f;
                } else {
                    scale = (contentFactor - delayFactor) / (1f - delayFactor);
                }
            }
            canvas.scale(scale, scale, CELL_RADIUS, CELL_RADIUS);
            shareCell.draw(canvas);
            canvas.restore();
        }

        canvas.restore();
    }

    public Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    private Drawable getThemedDrawable(String key) {
        Drawable drawable = resourcesProvider != null ? resourcesProvider.getDrawable(key) : null;
        return drawable != null ? drawable : Theme.getThemeDrawable(key);
    }
}
