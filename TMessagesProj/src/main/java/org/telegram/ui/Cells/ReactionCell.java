package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class ReactionCell extends FrameLayout {
    public interface Delegate {
        void animationStickerEnd();
    }

    public BackupImageView imageView;
    private ReactionCell.Delegate delegate;
    private String currentReaction;

    public ReactionCell(Context context, int stickerSize, int frameWidth, int frameHeight) {
        super(context);
        imageView = new BackupImageView(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (delegate != null && getImageReceiver().getLottieAnimation() != null && getImageReceiver().getLottieAnimation().isRunning() && getImageReceiver().getLottieAnimation().getCurrentFrame() == getImageReceiver().getLottieAnimation().getFramesCount() - 2) {
                    delegate.animationStickerEnd();
                }
            }
        };
        imageView.setSize(AndroidUtilities.dp(stickerSize), AndroidUtilities.dp(stickerSize));
        imageView.setLayerNum(1);
        imageView.setAspectFit(true);
        addView(imageView, LayoutHelper.createFrame(frameWidth, frameHeight, Gravity.CENTER));
    }

    public void setDelegate(ReactionCell.Delegate delegate) {
        this.delegate = delegate;
    }

    public String getCurrentReaction() {
        return currentReaction;
    }

    public void setDocument(TLRPC.Document document, String reaction, String imageFilter, boolean enableLoadBlur) {
        this.currentReaction = reaction;
        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
        SvgHelper.SvgDrawable svgThumb = null;
        if (enableLoadBlur) {
            svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);
        }
        if (MessageObject.canAutoplayAnimatedSticker(document)) {
            if (svgThumb != null) {
                imageView.setImage(ImageLocation.getForDocument(document), imageFilter, null, svgThumb, document);
            } else if (thumb != null) {
                imageView.setImage(ImageLocation.getForDocument(document), imageFilter, ImageLocation.getForDocument(thumb, document), null, 0, document);
            } else {
                imageView.setImage(ImageLocation.getForDocument(document), imageFilter, null, null, document);
            }
        } else {
            if (svgThumb != null) {
                if (thumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", svgThumb, document);
                } else {
                    imageView.setImage(ImageLocation.getForDocument(document), null, "webp", svgThumb, document);
                }
            } else if (thumb != null) {
                imageView.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", null, document);
            } else {
                imageView.setImage(ImageLocation.getForDocument(document), null, "webp", null, document);
            }
        }
    }
}
