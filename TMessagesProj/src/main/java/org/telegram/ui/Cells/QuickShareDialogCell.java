package org.telegram.ui.Cells;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.Text;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static org.telegram.messenger.AndroidUtilities.dp;

public class QuickShareDialogCell extends FrameLayout {

    private static final int ANIMATION_DURATION = 300;
    private static final float ANIMATION_SCALE = 1.1f;

    public static final int CELL_SIZE_DP = 44;
    public static final int CELL_SIZE = AndroidUtilities.dp(CELL_SIZE_DP);
    public static final int CELL_RADIUS = CELL_SIZE / 2;

    private static final int TITLE_SIZE_DP = 12;
    private static final int TITLE_SIZE = AndroidUtilities.dp(TITLE_SIZE_DP);
    private static final float TITLE_PADDING_VERTICAL = dp(6);
    private static final float TITLE_PADDING_HORIZONTAL = dp(10);

    private final ImageReceiver imageReceiver = new ImageReceiver();
    private final AvatarDrawable avatarDrawable = new AvatarDrawable() {
        @Override
        public void invalidateSelf() {
            super.invalidateSelf();
            invalidate();
        }
    };

    private RepostStoryDrawable repostStoryDrawable;
    private TLRPC.User user;

    private TLRPC.Dialog currentDialog;

    private final int currentAccount = UserConfig.selectedAccount;
    public final Theme.ResourcesProvider resourcesProvider;

    private ValueAnimator scaleValueAnimator;
    private ValueAnimator alphaValueAnimator;
    private float currentScale = 1.0f;
    private float currentAlpha = 0.5f;
    private boolean isSelected;
    private boolean isLastCell;

    private Text title;
    private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public boolean isHideTitle = false;

    public QuickShareDialogCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setWillNotDraw(false);

        imageReceiver.setRoundRadius(CELL_RADIUS);
        imageReceiver.setImageCoords(0, 0, CELL_SIZE, CELL_SIZE);

        setClipChildren(false);
        setClipToPadding(false);

        title = new Text("", TITLE_SIZE_DP, AndroidUtilities.bold()).hackClipBounds();
        title.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        bgPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(CELL_SIZE, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(CELL_SIZE, MeasureSpec.EXACTLY)
        );
    }

    public void isLastCell(boolean isLastCell) {
        this.isLastCell = isLastCell;
    }

    public void setDialog(TLRPC.Dialog dialog) {
        long uid = dialog.id;
        if (uid == Long.MAX_VALUE) {
            if (repostStoryDrawable == null) {
                repostStoryDrawable = new RepostStoryDrawable(getContext(), this, true, resourcesProvider);
            }
            imageReceiver.setImage(null, null, null, null, repostStoryDrawable, 0, null, null, 0);
        } else if (DialogObject.isUserDialog(uid)) {
            user = MessagesController.getInstance(currentAccount).getUser(uid);
            avatarDrawable.setInfo(currentAccount, user);
            if (UserObject.isReplyUser(user)) {
                title.setText(LocaleController.getString(R.string.RepliesTitle));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                imageReceiver.setImage(null, null, null, null, avatarDrawable, 0, null, user, 0);
            } else if (UserObject.isUserSelf(user)) {
                title.setText(LocaleController.getString(R.string.SavedMessages));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                imageReceiver.setImage(null, null, null, null, avatarDrawable, 0, null, user, 0);
            } else {
                title.setText(user.first_name);
                imageReceiver.setForUserOrChat(user, avatarDrawable);
            }
        } else {
            user = null;
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-uid);
            if (chat != null) {
                title.setText(chat.title);
            } else {
                title.setText("");
            }
            avatarDrawable.setInfo(currentAccount, chat);
            imageReceiver.setForUserOrChat(user, avatarDrawable);
        }
        invalidate();
        currentDialog = dialog;
    }

    public TLRPC.Dialog getCurrentDialog() {
        return currentDialog;
    }

    @Override
    public boolean isSelected() {
        return isSelected;
    }

    public boolean isCellSelected() {
        return isSelected;
    }

    public void selectCell(boolean selected, boolean animated) {
        if (selected == isSelected) {
            return;
        }
        isSelected = selected;
        if (scaleValueAnimator != null) {
            scaleValueAnimator.cancel();
        }
        if (alphaValueAnimator != null) {
            alphaValueAnimator.cancel();
        }
        if (!animated) {
            currentScale = selected ? ANIMATION_SCALE : 1.0f;
            currentAlpha = selected ? 1.0f : 0.5f;
            invalidate();
            return;
        }
        if (selected) {
            scaleValueAnimator = ValueAnimator.ofFloat(currentScale, ANIMATION_SCALE);
            alphaValueAnimator = ValueAnimator.ofFloat(currentAlpha, 1f);
        } else {
            scaleValueAnimator = ValueAnimator.ofFloat(currentScale, 1.0f);
            alphaValueAnimator = ValueAnimator.ofFloat(currentAlpha, 0.5f);
        }
        scaleValueAnimator.addUpdateListener(animation -> {
            currentScale = (float) animation.getAnimatedValue();
            invalidate();
        });

        scaleValueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK);
        scaleValueAnimator.setDuration(ANIMATION_DURATION);
        scaleValueAnimator.start();

        alphaValueAnimator.addUpdateListener(animation -> {
            currentAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
        alphaValueAnimator.setDuration(ANIMATION_DURATION);
        alphaValueAnimator.start();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (getParent() != null && getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.scale(currentScale, currentScale, (float) CELL_SIZE / 2, (float) CELL_SIZE / 2);
        imageReceiver.setAlpha(currentAlpha);
        imageReceiver.draw(canvas);

        canvas.restore();

        if (isSelected && !isHideTitle) {
            canvas.save();

            float backgroundHeight = TITLE_PADDING_VERTICAL + TITLE_SIZE + TITLE_PADDING_VERTICAL;
            float backgroundWidth = TITLE_PADDING_HORIZONTAL + title.getWidth() + TITLE_PADDING_HORIZONTAL;

            float dx = 0;
            if (isLastCell && backgroundWidth > CELL_SIZE) {
                dx = CELL_SIZE - backgroundWidth;
            } else {
                dx = (CELL_SIZE - backgroundWidth) / 2f;
            }
            canvas.translate(dx, -TITLE_SIZE - TITLE_PADDING_VERTICAL - dp(20));
            canvas.scale(currentScale, currentScale, backgroundWidth / 2, backgroundHeight / 2);
            AndroidUtilities.rectTmp.set(0, 0, backgroundWidth, backgroundHeight);
            bgPaint.setAlpha((int) (currentAlpha * 180));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, backgroundHeight / 2, backgroundHeight / 2, bgPaint);

//            Paint backgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackground);
//            int oldBackgroundAlpha = backgroundPaint.getAlpha();
//
//            backgroundPaint.setAlpha((int) (currentAlpha * 255));
//
//            canvas.drawRoundRect(AndroidUtilities.rectTmp, backgroundHeight / 2, backgroundHeight / 2, backgroundPaint);
//
//            if (resourcesProvider != null) {
//                resourcesProvider.applyServiceShaderMatrix((int) backgroundWidth, (int) backgroundHeight, 0f, 0f);
//            } else {
//                Theme.applyServiceShaderMatrix((int) backgroundWidth, (int) backgroundHeight, 0f, 0f);
//            }

            canvas.translate(TITLE_PADDING_HORIZONTAL, TITLE_PADDING_VERTICAL);
            title.draw(canvas, 0, title.getHeight() / 2 - dp(1), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), currentAlpha);
            canvas.restore();

//            backgroundPaint.setAlpha(oldBackgroundAlpha);
        }
    }

    public boolean hasGradientService() {
        return resourcesProvider != null ? resourcesProvider.hasGradientService() : Theme.hasGradientService();
    }

    protected Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    public static class RepostStoryDrawable extends Drawable {

        private final LinearGradient gradient;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final RLottieDrawable lottieDrawable;
        private final Drawable drawable;

        public RepostStoryDrawable(Context context, View parentView, boolean animate, Theme.ResourcesProvider resourcesProvider) {
            gradient = new LinearGradient(0, 0, CELL_SIZE, CELL_SIZE, new int[]{
                    Theme.getColor(Theme.key_stories_circle1, resourcesProvider),
                    Theme.getColor(Theme.key_stories_circle2, resourcesProvider)
            }, new float[]{0, 1}, Shader.TileMode.CLAMP);
            paint.setShader(gradient);

            if (animate) {
                lottieDrawable = new RLottieDrawable(R.raw.story_repost, "story_repost", CELL_SIZE, CELL_SIZE, true, null);
                lottieDrawable.setMasterParent(parentView);
                AndroidUtilities.runOnUIThread(lottieDrawable::start, 450);
                drawable = null;
            } else {
                lottieDrawable = null;
                drawable = context.getResources().getDrawable(R.drawable.large_repost_story).mutate();
                drawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            }
        }

        int alpha = 0xFF;

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            AndroidUtilities.rectTmp.set(0, 0, getBounds().width(), getBounds().height());
            paint.setAlpha(alpha);
            float r2 = Math.min(getBounds().width(), getBounds().height()) / 2f * ((float) alpha / 0xFF);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, r2, r2, paint);
            canvas.restore();

            final int r = dp(lottieDrawable != null ? 20 : 15);
            AndroidUtilities.rectTmp2.set(
                    getBounds().centerX() - r,
                    getBounds().centerY() - r,
                    getBounds().centerX() + r,
                    getBounds().centerY() + r
            );
            Drawable drawable = lottieDrawable == null ? this.drawable : lottieDrawable;
            if (drawable != null) {
                drawable.setBounds(AndroidUtilities.rectTmp2);
                drawable.setAlpha(alpha);
                drawable.draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getIntrinsicWidth() {
            return CELL_SIZE;
        }

        @Override
        public int getIntrinsicHeight() {
            return CELL_SIZE;
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }
}
