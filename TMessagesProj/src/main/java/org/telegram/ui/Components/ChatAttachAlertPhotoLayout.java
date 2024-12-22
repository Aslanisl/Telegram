/*
 * This is the source code of Telegram for Android v. 6.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.Components;

import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.touchSlop;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewPropertyAnimator;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.camera.CameraController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.Cells.PhotoAttachCameraCell;
import org.telegram.ui.Cells.PhotoAttachPermissionCell;
import org.telegram.ui.Cells.PhotoAttachPhotoCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.AlbumButton;
import org.telegram.ui.Stories.recorder.CollageLayout;
import org.telegram.ui.Stories.recorder.CollageLayoutButton;
import org.telegram.ui.Stories.recorder.CollageLayoutView2;
import org.telegram.ui.Stories.recorder.DualCameraView;
import org.telegram.ui.Stories.recorder.FlashViews;
import org.telegram.ui.Stories.recorder.PhotoVideoSwitcherView;
import org.telegram.ui.Stories.recorder.RecordControl;
import org.telegram.ui.Stories.recorder.SliderView;
import org.telegram.ui.Stories.recorder.StoryEntry;
import org.telegram.ui.Stories.recorder.StoryPrivacySelector;
import org.telegram.ui.Stories.recorder.ToggleButton;
import org.telegram.ui.Stories.recorder.ToggleButton2;
import org.telegram.ui.Stories.recorder.VideoTimerView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatAttachAlertPhotoLayout extends ChatAttachAlert.AttachAlertLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final int VIEW_TYPE_AVATAR_CONSTRUCTOR = 4;
    private static final int SHOW_FAST_SCROLL_MIN_COUNT = 30;
    private final boolean needCamera;

    private RecyclerListView cameraPhotoRecyclerView;
    private LinearLayoutManager cameraPhotoLayoutManager;
    private PhotoAttachAdapter cameraAttachAdapter;

    private FrameLayout actionBarContainer;
    private LinearLayout actionBarButtons;
    private ActionBarMenuItem dropDownContainer;
    public TextView dropDown;
    private Drawable dropDownDrawable;

    public RecyclerListView gridView;
    private GridLayoutManager layoutManager;
    private PhotoAttachAdapter adapter;
    private EmptyTextProgressView progressView;
    private RecyclerViewItemRangeSelector itemRangeSelector;
    private int gridExtraSpace;
    private boolean shouldSelect;
    private int alertOnlyOnce;

    private Drawable cameraDrawable;

    private int currentSelectedCount;

    private boolean isHidden;

    ValueAnimator paddingAnimator;
    private int animateToPadding;

    private AnimatorSet cameraInitAnimation;
    private DualCameraView cameraView;
    protected FrameLayout cameraIcon;
    protected PhotoAttachCameraCell cameraCell;
    private boolean flashAnimationInProgress;
    private float[] cameraViewLocation = new float[2];
    private int[] viewPosition = new int[2];
    private float cameraViewOffsetX;
    private float cameraViewOffsetY;
    private float cameraViewOffsetBottomY;
    public boolean cameraOpened;
    private boolean canSaveCameraPreview;
    private boolean cameraAnimationInProgress;
    private float cameraOpenProgress;
    private int[] animateCameraValues = new int[5];
    private DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);

    private FlashViews.ImageViewInvertable backButton;
    private FrameLayout cameraControlContainer;
    private CameraTouchView cameraTouchView;
    private ToggleButton dualButton;
    private ToggleButton2 flashButton;
    private RecordControl recordControl;
    private PhotoVideoSwitcherView modeSwitcherView;
    private FlashViews flashViews;
    private ZoomControlView zoomControlView;
    private AnimatorSet zoomControlAnimation;
    private Runnable zoomControlHideRunnable;
    private Runnable afterCameraInitRunnable;
    private Boolean isCameraFrontfaceBeforeEnteringEditMode = null;
    private TextView counterTextView;
    private TextView tooltipTextView;
    private VideoTimerView videoTimerView;

    private BlurringShader.BlurManager blurManager;
    private CollageLayout lastCollageLayout;
    private CollageLayoutButton collageButton;
    private ToggleButton2 collageRemoveButton;
    private CollageLayoutButton.CollageLayoutListView collageListView;
    protected CollageLayoutView2 collageLayoutView;

    private int flashButtonResId;
    private boolean takingPhoto;
    private boolean isVideo = false;
    private boolean takingVideo = false;
    private boolean stoppingTakingVideo = false;
    private boolean scrollingY, scrollingX;

    private static boolean mediaFromExternalCamera;
    public static ArrayList<Object> cameraPhotos = new ArrayList<>();
    public static HashMap<Object, Object> selectedPhotos = new HashMap<>();
    public static ArrayList<Object> selectedPhotosOrder = new ArrayList<>();
    public static int lastImageId = -1;
    private boolean cancelTakingPhotos;
    private boolean checkCameraWhenShown;

    private boolean mediaEnabled;
    private boolean videoEnabled;
    private boolean photoEnabled;
    private boolean documentsEnabled;

    private float pinchStartDistance;
    private float cameraZoom;
    private boolean zooming;
    private boolean zoomWas;
    private android.graphics.Rect hitRect = new Rect();

    private float lastY;
    private boolean pressed;
    private boolean maybeStartDraging;
    private boolean dragging;

    private boolean cameraPhotoRecyclerViewIgnoreLayout;

    private int itemSize = dp(80);
    private int lastItemSize = itemSize;
    private int itemsPerRow = 3;

    private boolean deviceHasGoodCamera;
    private boolean noCameraPermissions;
    private boolean noGalleryPermissions;
    private boolean requestingPermissions;

    private boolean ignoreLayout;
    private int lastNotifyWidth;

    private MediaController.AlbumEntry selectedAlbumEntry;
    private MediaController.AlbumEntry galleryAlbumEntry;
    private ArrayList<MediaController.AlbumEntry> dropDownAlbums;
    private float currentPanTranslationY;

    private boolean loading = true;

    public final static int group = 0;
    public final static int compress = 1;
    public final static int spoiler = 2;
    public final static int open_in = 3;
    public final static int preview_gap = 4;
    public final static int media_gap = 5;
    public final static int preview = 6;
    public final static int caption = 7;
    public final static int stars = 8;

    private ActionBarMenuSubItem spoilerItem;
    private ActionBarMenuSubItem compressItem;
    private ActionBarMenuSubItem starsItem;
    protected ActionBarMenuSubItem previewItem;
    public MessagePreviewView.ToggleButton captionItem;

    boolean forceDarkTheme;
    private AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    private boolean showAvatarConstructor;

    public void updateAvatarPicker() {
        showAvatarConstructor = parentAlert.avatarPicker != 0 && !parentAlert.isPhotoPicker;
    }

    private class BasePhotoProvider extends PhotoViewer.EmptyPhotoViewerProvider {
        @Override
        public boolean isPhotoChecked(int index) {
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            return photoEntry != null && selectedPhotos.containsKey(photoEntry.imageId);
        }

        @Override
        public int setPhotoChecked(int index, VideoEditedInfo videoEditedInfo) {
            if (parentAlert.maxSelectedPhotos >= 0 && selectedPhotos.size() >= parentAlert.maxSelectedPhotos && !isPhotoChecked(index)) {
                return -1;
            }
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            if (photoEntry == null) {
                return -1;
            }
            if (checkSendMediaEnabled(photoEntry)) {
                return -1;
            }
            if (selectedPhotos.size() + 1 > maxCount()) {
                return -1;
            }
            boolean add = true;
            int num;
            if ((num = addToSelectedPhotos(photoEntry, -1)) == -1) {
                num = selectedPhotosOrder.indexOf(photoEntry.imageId);
            } else {
                add = false;
                photoEntry.editedInfo = null;
            }
            photoEntry.editedInfo = videoEditedInfo;

            int count = gridView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = gridView.getChildAt(a);
                if (view instanceof PhotoAttachPhotoCell) {
                    int tag = (Integer) view.getTag();
                    if (tag == index) {
                        if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                            ((PhotoAttachPhotoCell) view).setChecked(num, add, false);
                        } else {
                            ((PhotoAttachPhotoCell) view).setChecked(-1, add, false);
                        }
                        break;
                    }
                }
            }
            count = cameraPhotoRecyclerView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = cameraPhotoRecyclerView.getChildAt(a);
                if (view instanceof PhotoAttachPhotoCell) {
                    int tag = (Integer) view.getTag();
                    if (tag == index) {
                        if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                            ((PhotoAttachPhotoCell) view).setChecked(num, add, false);
                        } else {
                            ((PhotoAttachPhotoCell) view).setChecked(-1, add, false);
                        }
                        break;
                    }
                }
            }
            parentAlert.updateCountButton(add ? 1 : 2);
            return num;
        }

        @Override
        public int getSelectedCount() {
            return selectedPhotos.size();
        }

        @Override
        public ArrayList<Object> getSelectedPhotosOrder() {
            return selectedPhotosOrder;
        }

        @Override
        public HashMap<Object, Object> getSelectedPhotos() {
            return selectedPhotos;
        }

        @Override
        public int getPhotoIndex(int index) {
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            if (photoEntry == null) {
                return -1;
            }
            return selectedPhotosOrder.indexOf(photoEntry.imageId);
        }
    }

    private void setCurrentSpoilerVisible(int i, boolean visible) {
        PhotoViewer photoViewer = PhotoViewer.getInstance();
        int index = i == -1 ? photoViewer.getCurrentIndex() : i;
        List<Object> photos = photoViewer.getImagesArrLocals();
        boolean hasSpoiler = photos != null && !photos.isEmpty() && index < photos.size() && photos.get(index) instanceof MediaController.PhotoEntry && ((MediaController.PhotoEntry) photos.get(index)).hasSpoiler;

        if (hasSpoiler) {
            MediaController.PhotoEntry entry = (MediaController.PhotoEntry) photos.get(index);

            gridView.forAllChild(view -> {
                if (view instanceof PhotoAttachPhotoCell) {
                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                    if (cell.getPhotoEntry() == entry) {
                        cell.setHasSpoiler(visible, 250f);
                        cell.setStarsPrice(getStarsPrice(), selectedPhotos.size() > 1);
                    }
                }
            });
        }
    }

    public PhotoViewer.PhotoViewerProvider photoViewerProvider = new BasePhotoProvider() {
        @Override
        public void onOpen() {
            pauseCameraPreview();
            setCurrentSpoilerVisible(-1, true);
        }

        @Override
        public void onPreClose() {
            setCurrentSpoilerVisible(-1, false);
        }

        @Override
        public void onClose() {
            resumeCameraPreview();
            AndroidUtilities.runOnUIThread(() -> setCurrentSpoilerVisible(-1, true), 150);
        }

        @Override
        public void onEditModeChanged(boolean isEditMode) {
            onPhotoEditModeChanged(isEditMode);
        }

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                int[] coords = new int[2];
                cell.getImageView().getLocationInWindow(coords);
                if (Build.VERSION.SDK_INT < 26) {
                    coords[0] -= parentAlert.getLeftInset();
                }
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1];
                object.parentView = gridView;
                object.imageReceiver = cell.getImageView().getImageReceiver();
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.scale = cell.getScale();
                object.clipBottomAddition = (int) parentAlert.getClipLayoutBottom();
                cell.showCheck(false);
                return object;
            }

            return null;
        }

        @Override
        public void updatePhotoAtIndex(int index) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                cell.getImageView().setOrientation(0, true);
                MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
                if (photoEntry == null) {
                    return;
                }
                if (photoEntry.thumbPath != null) {
                    cell.getImageView().setImage(photoEntry.thumbPath, null, Theme.chat_attachEmptyDrawable);
                } else if (photoEntry.path != null) {
                    cell.getImageView().setOrientation(photoEntry.orientation, photoEntry.invert, true);
                    if (photoEntry.isVideo) {
                        cell.getImageView().setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable);
                    } else {
                        cell.getImageView().setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable);
                    }
                } else {
                    cell.getImageView().setImageDrawable(Theme.chat_attachEmptyDrawable);
                }
            }
        }

        @Override
        public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                return cell.getImageView().getImageReceiver().getBitmapSafe();
            }
            return null;
        }

        @Override
        public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                cell.showCheck(true);
            }
        }

        @Override
        public void willHidePhotoViewer() {
            int count = gridView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = gridView.getChildAt(a);
                if (view instanceof PhotoAttachPhotoCell) {
                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                    cell.showCheck(true);
                }
            }
        }

        @Override
        public void onApplyCaption(CharSequence caption) {
            if (selectedPhotos.size() > 0 && selectedPhotosOrder.size() > 0) {
                Object o = selectedPhotos.get(selectedPhotosOrder.get(0));
                CharSequence firstPhotoCaption = null;
                ArrayList<TLRPC.MessageEntity> entities = null;
                if (o instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                    firstPhotoCaption = photoEntry1.caption;
                    entities = photoEntry1.entities;
                }
                if (o instanceof MediaController.SearchImage) {
                    MediaController.SearchImage photoEntry1 = (MediaController.SearchImage) o;
                    firstPhotoCaption = photoEntry1.caption;
                    entities = photoEntry1.entities;
                }
                if (firstPhotoCaption != null) {
                    if (entities != null) {
                        if (!(firstPhotoCaption instanceof Spannable)) {
                            firstPhotoCaption = new SpannableStringBuilder(firstPhotoCaption);
                        }
                        MessageObject.addEntitiesToText(firstPhotoCaption, entities, false, false, false, false);
                    }
                }
                parentAlert.getCommentView().setText(AnimatedEmojiSpan.cloneSpans(firstPhotoCaption, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW));
            }
        }

        @Override
        public boolean cancelButtonPressed() {
            return false;
        }

        @Override
        public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument) {
            parentAlert.sent = true;
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            if (photoEntry != null) {
                photoEntry.editedInfo = videoEditedInfo;
            }
            if (selectedPhotos.isEmpty() && photoEntry != null) {
                addToSelectedPhotos(photoEntry, -1);
            }
            if (parentAlert.checkCaption(parentAlert.getCommentView().getText())) {
                return;
            }
            parentAlert.applyCaption();
            if (PhotoViewer.getInstance().hasCaptionForAllMedia) {
                HashMap<Object, Object> selectedPhotos = getSelectedPhotos();
                ArrayList<Object> selectedPhotosOrder = getSelectedPhotosOrder();
                if (!selectedPhotos.isEmpty()) {
                    for (int a = 0; a < selectedPhotosOrder.size(); a++) {
                        Object o = selectedPhotos.get(selectedPhotosOrder.get(a));
                        if (o instanceof MediaController.PhotoEntry) {
                            MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                            if (a == 0) {
                                CharSequence[] caption = new CharSequence[]{PhotoViewer.getInstance().captionForAllMedia};
                                photoEntry1.entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(caption, false);
                                photoEntry1.caption = caption[0];
                                if (parentAlert.checkCaption(photoEntry1.caption)) {
                                    return;
                                }
                            } else {
                                photoEntry1.caption = null;
                            }
                        }
                    }
                }
            }
            parentAlert.delegate.didPressedButton(7, true, notify, scheduleDate, 0, parentAlert.isCaptionAbove(), forceDocument);
            selectedPhotos.clear();
            cameraPhotos.clear();
            selectedPhotosOrder.clear();
            selectedPhotos.clear();
        }

        @Override
        public boolean allowCaption() {
            return !parentAlert.isPhotoPicker;
        }

        @Override
        public long getDialogId() {
            if (parentAlert.baseFragment instanceof ChatActivity)
                return ((ChatActivity) parentAlert.baseFragment).getDialogId();
            return super.getDialogId();
        }

        @Override
        public boolean canMoveCaptionAbove() {
            return parentAlert != null && parentAlert.baseFragment instanceof ChatActivity;
        }

        @Override
        public boolean isCaptionAbove() {
            return parentAlert != null && parentAlert.captionAbove;
        }

        @Override
        public void moveCaptionAbove(boolean above) {
            if (parentAlert == null || parentAlert.captionAbove == above) return;
            parentAlert.setCaptionAbove(above);
            captionItem.setState(!parentAlert.captionAbove, true);
        }
    };

    protected void updateCheckedPhotoIndices() {
        if (!(parentAlert.baseFragment instanceof ChatActivity)) {
            return;
        }
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = gridView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition((Integer) cell.getTag());
                if (photoEntry != null) {
                    cell.setNum(selectedPhotosOrder.indexOf(photoEntry.imageId));
                }
            }
        }
        count = cameraPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = cameraPhotoRecyclerView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition((Integer) cell.getTag());
                if (photoEntry != null) {
                    cell.setNum(selectedPhotosOrder.indexOf(photoEntry.imageId));
                }
            }
        }
    }

    protected void updateCheckedPhotos() {
        if (!(parentAlert.baseFragment instanceof ChatActivity)) {
            return;
        }
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = gridView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                int position = gridView.getChildAdapterPosition(view);
                if (adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                    position--;
                }
                MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(position);
                cell.setHasSpoiler(photoEntry != null && photoEntry.hasSpoiler);
                if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                    cell.setChecked(photoEntry != null ? selectedPhotosOrder.indexOf(photoEntry.imageId) : -1, photoEntry != null && selectedPhotos.containsKey(photoEntry.imageId), true);
                } else {
                    cell.setChecked(-1, photoEntry != null && selectedPhotos.containsKey(photoEntry.imageId), true);
                }
            }
        }
        count = cameraPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = cameraPhotoRecyclerView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                int position = cameraPhotoRecyclerView.getChildAdapterPosition(view);
                if (adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                    position--;
                }
                MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(position);
                cell.setHasSpoiler(photoEntry != null && photoEntry.hasSpoiler);
                if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                    cell.setChecked(photoEntry != null ? selectedPhotosOrder.indexOf(photoEntry.imageId) : -1, photoEntry != null && selectedPhotos.containsKey(photoEntry.imageId), true);
                } else {
                    cell.setChecked(-1, photoEntry != null && selectedPhotos.containsKey(photoEntry.imageId), true);
                }
            }
        }
    }

    private MediaController.PhotoEntry getPhotoEntryAtPosition(int position) {
        if (position < 0) {
            return null;
        }
        int cameraCount = cameraPhotos.size();
        if (position < cameraCount) {
            return (MediaController.PhotoEntry) cameraPhotos.get(position);
        }
        position -= cameraCount;
        if (selectedAlbumEntry != null && position < selectedAlbumEntry.photos.size()) {
            return selectedAlbumEntry.photos.get(position);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected ArrayList<Object> getAllPhotosArray() {
        ArrayList<Object> arrayList;
        if (selectedAlbumEntry != null) {
            if (!cameraPhotos.isEmpty()) {
                arrayList = new ArrayList<>(selectedAlbumEntry.photos.size() + cameraPhotos.size());
                arrayList.addAll(cameraPhotos);
                arrayList.addAll(selectedAlbumEntry.photos);
            } else {
                arrayList = (ArrayList) selectedAlbumEntry.photos;
            }
        } else if (!cameraPhotos.isEmpty()) {
            arrayList = cameraPhotos;
        } else {
            arrayList = new ArrayList<>(0);
        }
        return arrayList;
    }

    private boolean isDark;

    private void checkIsDark() {
        if (cameraView == null || cameraView.getTextureView() == null) {
            isDark = false;
            return;
        }
        final Bitmap bitmap = cameraView.getTextureView().getBitmap();
        if (bitmap == null) {
            isDark = false;
            return;
        }
        float l = 0;
        final int sx = bitmap.getWidth() / 12;
        final int sy = bitmap.getHeight() / 12;
        for (int x = 0; x < 10; ++x) {
            for (int y = 0; y < 10; ++y) {
                l += AndroidUtilities.computePerceivedBrightness(bitmap.getPixel((1 + x) * sx, (1 + y) * sy));
            }
        }
        l /= 100;
        bitmap.recycle();
        isDark = l < .22f;
    }

    private boolean useDisplayFlashlight() {
        return (takingPhoto || takingVideo) && (cameraView.isFrontface()) && (frontfaceFlashMode == 2 || frontfaceFlashMode == 1 && isDark);
    }

    private void updateVideoViews() {
        showVideoTimer(isVideo, true);
        if (modeSwitcherView != null) {
            modeSwitcherView.switchMode(isVideo);
        }
        if (recordControl != null) {
            recordControl.startAsVideo(isVideo);
        }
    }

    private AnimatorSet recordingAnimator;
    private boolean animatedRecording;

    private void animateRecording(boolean recording, boolean animated) {
        if (animatedRecording == recording) {
            return;
        }
        if (recordingAnimator != null) {
            recordingAnimator.cancel();
            recordingAnimator = null;
        }
        animatedRecording = recording;
        if (animated) {
            backButton.setVisibility(View.VISIBLE);
            flashButton.setVisibility(View.VISIBLE);
            dualButton.setVisibility(cameraView != null && cameraView.dualAvailable() ? View.VISIBLE : View.GONE);
            recordingAnimator = new AnimatorSet();
            recordingAnimator.playTogether(
                    ObjectAnimator.ofFloat(backButton, View.ALPHA, recording ? 0 : 1),
                    ObjectAnimator.ofFloat(flashButton, View.ALPHA, recording || !cameraOpened ? 0 : 1),
                    ObjectAnimator.ofFloat(dualButton, View.ALPHA, recording || !cameraOpened || cameraView == null || !cameraView.dualAvailable() ? 0 : 1),
                    ObjectAnimator.ofFloat(modeSwitcherView, View.ALPHA, recording || !cameraOpened || inCheck() ? 0 : 1),
                    ObjectAnimator.ofFloat(modeSwitcherView, View.TRANSLATION_Y, recording || !cameraOpened || inCheck() ? dp(16) : 0)
            );
            recordingAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (recording) {
                        backButton.setVisibility(View.GONE);
                    }
                    if (recording || !cameraOpened) {
                        flashButton.setVisibility(View.GONE);
                    }
                }
            });
            recordingAnimator.setDuration(260);
            recordingAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            recordingAnimator.start();
        } else {
            backButton.setAlpha(recording ? 0 : 1f);
            backButton.setVisibility(recording ? View.GONE : View.VISIBLE);
            flashButton.setAlpha(recording || !cameraOpened ? 0 : 1f);
            flashButton.setVisibility(recording || !cameraOpened ? View.GONE : View.VISIBLE);
            dualButton.setAlpha(recording || !cameraOpened ? 0 : 1f);
            dualButton.setVisibility(recording || !cameraOpened || cameraView == null || !cameraView.dualAvailable() ? View.GONE : View.VISIBLE);
            modeSwitcherView.setAlpha(recording || !cameraOpened || inCheck() ? 0 : 1f);
            modeSwitcherView.setTranslationY(recording || !cameraOpened || inCheck() ? dp(16) : 0);
        }
    }

    private final RecordControl.Delegate recordControlDelegate = new RecordControl.Delegate() {

        private File outputFile;
        private boolean zoomingWas;
        // TODO VREMENNO или нет...!
        private StoryEntry outputEntry;

        @Override
        public boolean canRecordAudio() {
            return requestAudioPermission();
        }

        @Override
        public void onPhotoShoot() {
            if (takingPhoto || cameraView == null || !cameraView.isInited()) {
                return;
            }
//            cameraHint.hide();
            outputFile = AndroidUtilities.generatePicturePath(parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat(), null);
            takingPhoto = true;
            checkFrontfaceFlashModes();
            isDark = false;
            if (cameraView.isFrontface() && frontfaceFlashMode == 1) {
                checkIsDark();
            }
            if (useDisplayFlashlight()) {
                flashViews.flash(this::takePicture);
            } else {
                takePicture(null);
            }
        }

        @Override
        public void onCheckClick() {
            ArrayList<StoryEntry> entries = collageLayoutView.getContent();
            if (entries.size() == 1) {
                outputEntry = entries.get(0);
            } else {
                outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
            }
            isVideo = outputEntry != null && outputEntry.isVideo;
            if (modeSwitcherView != null) {
                modeSwitcherView.switchMode(isVideo);
            }
            if (outputEntry != null && !outputEntry.wouldBeVideo()) {
                outputFile = AndroidUtilities.generatePicturePath(parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat(), null);
                outputEntry.buildPhoto(outputFile);
                MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, outputFile.getAbsolutePath(), 0, false, outputEntry.width, outputEntry.height, 0);
                photoEntry.canDeleteAfter = true;
                if (useDisplayFlashlight()) {
                    flashViews.flashOut();
                }
                final boolean sameTakePictureOrientation = cameraView.getCameraSession().isSameTakePictureOrientation();
                openPhotoViewer(photoEntry, sameTakePictureOrientation, false);

                if (collageLayoutView.hasLayout()) {
                    collageLayoutView.clear(false);
                    recordControl.setCollageProgress(0.0f, false);
                }
            } else if (outputEntry != null) {
                outputEntry.getVideoEditedInfo(info -> {
                    outputFile = AndroidUtilities.generateVideoPath(parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat());
                    if (!outputFile.exists()) {
                        try {
                            outputFile.createNewFile();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    File thumbFile = prepareThumb(outputEntry, false);
                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, outputFile.getAbsolutePath(), 0, false, outputEntry.width, outputEntry.height, 0);
                    info.isPhoto = true;
                    info.originalWidth = outputEntry.width;
                    info.originalHeight = outputEntry.height;
                    info.originalPath = outputFile.getAbsolutePath();
                    photoEntry.editedInfo = info;
                    photoEntry.isVideo = false;
                    photoEntry.thumbPath = thumbFile.getAbsolutePath();
                    if (useDisplayFlashlight()) {
                        flashViews.flashOut();
                    }
                    final boolean sameTakePictureOrientation = cameraView.getCameraSession().isSameTakePictureOrientation();
                    openPhotoViewer(photoEntry, sameTakePictureOrientation, false);

//                    if (collageLayoutView.hasLayout()) {
//                        collageLayoutView.clear(false);
//                        recordControl.setCollageProgress(0.0f, false);
//                    }
//                    File file = AndroidUtilities.generateVideoPath(parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat());
//
//                    TLRPC.TL_message message = new TLRPC.TL_message();
//                    message.id = 0;
//                    message.message = "";
//                    message.media = new TLRPC.TL_messageMediaEmpty();
//                    message.action = new TLRPC.TL_messageActionEmpty();
//                    // TODO check
//                    message.dialog_id = ((ChatActivity) parentAlert.baseFragment).dialog_id;
//                    String path = message.attachPath = file.getAbsolutePath();
//                    MessageObject messageObject = new MessageObject(UserConfig.selectedAccount, message, false, false);
//                    messageObject.messageOwner.attachPath = path;
//                    messageObject.videoEditedInfo = info;
//                    boolean result = MediaController.getInstance().scheduleVideoConvert(messageObject, true, true, true);
//                    Log.d("TAGLOG", "Result " + result);


//                    // TODO return
//                    outputFile = AndroidUtilities.generateVideoPath(parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat());
//                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, outputFile.getAbsolutePath(), 0, false, outputEntry.width, outputEntry.height, 0);
//                    parentAlert.sent = true;
//                    info.originalWidth = outputEntry.width;
//                    info.originalHeight = outputEntry.height;
//                    info.originalPath = outputFile.getAbsolutePath();
//                    photoEntry.editedInfo = info;
//                    if (useDisplayFlashlight()) {
//                        flashViews.flashOut();
//                    }
//                    addToSelectedPhotos(photoEntry, -1);
////                    if (!outputFile.exists()) {
////                        try {
////                            outputFile.createNewFile();
////                        } catch (IOException e) {
////                            e.printStackTrace();
////                        }
////                    }
//                    parentAlert.delegate.didPressedButton(7, true, true, 0, 0, parentAlert.isCaptionAbove(), false);
//                    selectedPhotos.clear();
//                    cameraPhotos.clear();
//                    selectedPhotosOrder.clear();
//                    selectedPhotos.clear();

                    // TODO return
//                    outputFile = AndroidUtilities.generateVideoPath(parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat());
//                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, outputFile.getAbsolutePath(), 0, false, outputEntry.width, outputEntry.height, 0);
//                    parentAlert.sent = true;
//                    info.originalWidth = outputEntry.width;
//                    info.originalHeight = outputEntry.height;
//                    info.originalPath = outputFile.getAbsolutePath();
//                    photoEntry.editedInfo = info;
//                    if (useDisplayFlashlight()) {
//                        flashViews.flashOut();
//                    }
//                    addToSelectedPhotos(photoEntry, -1);
////                    if (!outputFile.exists()) {
////                        try {
////                            outputFile.createNewFile();
////                        } catch (IOException e) {
////                            e.printStackTrace();
////                        }
////                    }
//                    parentAlert.delegate.didPressedButton(7, true, true, 0, 0, parentAlert.isCaptionAbove(), false);
//                    selectedPhotos.clear();
//                    cameraPhotos.clear();
//                    selectedPhotosOrder.clear();
//                    selectedPhotos.clear();
                });
            }
//            navigateTo(PAGE_PREVIEW, true);
        }

        private File prepareThumb(StoryEntry storyEntry, boolean forDraft) {
            if (storyEntry == null) {
                return null;
            }
            File file = forDraft ? storyEntry.draftThumbFile : storyEntry.uploadThumbFile;
            if (file != null) {
                file.delete();
                file = null;
            }

            View previewView = collageLayoutView;

            final float scale = forDraft ? 1 / 3f : 1f;
            final int w = (int) (previewView.getWidth() * scale);
            final int h = (int) (previewView.getHeight() * scale);
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);

            canvas.save();
            canvas.scale(scale, scale);
            AndroidUtilities.makingGlobalBlurBitmap = true;
            previewView.draw(canvas);
            AndroidUtilities.makingGlobalBlurBitmap = false;
            canvas.restore();

            final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

            if (storyEntry.paintBlurFile != null) {
                try {
                    Bitmap paintBitmap = BitmapFactory.decodeFile(storyEntry.paintBlurFile.getPath());
                    canvas.save();
                    float scale2 = w / (float) paintBitmap.getWidth();
                    canvas.scale(scale2, scale2);
                    canvas.drawBitmap(paintBitmap, 0, 0, bitmapPaint);
                    canvas.restore();
                    paintBitmap.recycle();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            if (storyEntry.paintFile != null) {
                try {
                    Bitmap paintBitmap = BitmapFactory.decodeFile(storyEntry.paintFile.getPath());
                    canvas.save();
                    float scale2 = w / (float) paintBitmap.getWidth();
                    canvas.scale(scale2, scale2);
                    canvas.drawBitmap(paintBitmap, 0, 0, bitmapPaint);
                    canvas.restore();
                    paintBitmap.recycle();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            Bitmap thumbBitmap = Bitmap.createScaledBitmap(bitmap, 40, 22, true);

            file = StoryEntry.makeCacheFile(UserConfig.selectedAccount, false);
            try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, forDraft ? 95 : 99, new FileOutputStream(file));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            bitmap.recycle();

            if (forDraft) {
                storyEntry.draftThumbFile = file;
            } else {
                storyEntry.uploadThumbFile = file;
            }
            storyEntry.thumbBitmap = thumbBitmap;
            return file;
        }

        private void takePicture(Utilities.Callback<Runnable> done) {
            boolean savedFromTextureView = false;
            if (!useDisplayFlashlight()) {
                cameraView.startTakePictureAnimation(true);
            }
            final boolean sameTakePictureOrientation = cameraView.getCameraSession().isSameTakePictureOrientation();

            if (cameraView.isDual() && TextUtils.equals(cameraView.getCameraSession().getCurrentFlashMode(), Camera.Parameters.FLASH_MODE_OFF) || collageLayoutView.hasLayout()) {
                if (!collageLayoutView.hasLayout()) {
                    cameraView.pauseAsTakingPicture();
                }
                final Bitmap bitmap = cameraView.getTextureView().getBitmap();
                try (FileOutputStream out = new FileOutputStream(outputFile.getAbsoluteFile())) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    savedFromTextureView = true;
                } catch (Exception e) {
                    FileLog.e(e);
                }
                bitmap.recycle();

                takingPhoto = false;
                if (collageLayoutView.hasLayout()) {
                    final StoryEntry entry = StoryEntry.fromPhotoShoot(outputFile, 0);
                    outputFile = null;
                    if (collageLayoutView.push(entry)) {
                        outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
                    }

                    if (done != null) {
                        done.run(null);
                    }
                    updateActionBarButtons(true);
                } else {
                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, outputFile.getAbsolutePath(), 0, false, bitmap.getWidth(), bitmap.getHeight(), 0);
                    bitmap.recycle();
                    photoEntry.canDeleteAfter = true;
                    if (useDisplayFlashlight()) {
                        flashViews.flashOut();
                    }
                    openPhotoViewer(photoEntry, sameTakePictureOrientation, false);
                }
            }

            if (!savedFromTextureView) {
                cameraView.getCameraSession().setFlipFront(parentAlert.baseFragment instanceof ChatActivity || parentAlert.avatarPicker == 2);
                takingPhoto = CameraController.getInstance().takePicture(outputFile, false, cameraView.getCameraSessionObject(), (orientation) -> {
                    if (useDisplayFlashlight()) {
                        flashViews.flashOut();
                    }
                    takingPhoto = false;
                    if (outputFile == null || parentAlert.destroyed) {
                        return;
                    }
//                    Pair<Integer, Integer> orientation = AndroidUtilities.getImageOrientation(cameraFile);
                    mediaFromExternalCamera = false;
                    int width = 0, height = 0;
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(new File(outputFile.getAbsolutePath()).getAbsolutePath(), options);
                        width = options.outWidth;
                        height = options.outHeight;
                    } catch (Exception ignore) {
                    }
                    if (collageLayoutView.hasLayout()) {
                        int rotate = orientation == -1 ? 0 : 90;
                        if (orientation == -1) {
                            if (width > height) {
                                rotate = 270;
                            }
                        } else if (height > width && rotate != 0) {
                            rotate = 0;
                        }

                        StoryEntry entry = StoryEntry.fromPhotoShoot(outputFile, rotate);
                        outputFile = null;
                        if (collageLayoutView.push(entry)) {
                            outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());

                            //                            if (done != null) {
//                                done.run(() -> navigateTo(PAGE_PREVIEW, true));
//                            } else {
//                                navigateTo(PAGE_PREVIEW, true);
//                            }
                        }
                        if (done != null) {
                            done.run(null);
                        }
                        updateActionBarButtons(true);
                    } else {
                        MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, outputFile.getAbsolutePath(), orientation == -1 ? 0 : orientation, false, width, height, 0);
                        photoEntry.canDeleteAfter = true;
                        openPhotoViewer(photoEntry, sameTakePictureOrientation, false);
                    }
                });
            }
        }

        @Override
        public void onVideoRecordStart(boolean byLongPress, Runnable whenStarted) {
            if (takingVideo || cameraView == null || cameraView.getCameraSession() == null) {
                return;
            }
            BaseFragment baseFragment = parentAlert.baseFragment;
            if (baseFragment == null) {
                baseFragment = LaunchActivity.getLastFragment();
            }
            if (baseFragment == null || baseFragment.getParentActivity() == null) {
                return;
            }
            if (!videoEnabled) {
                BulletinFactory.of(cameraView, resourcesProvider).createErrorBulletin(LocaleController.getString(R.string.GlobalAttachVideoRestricted)).show();
                return;
            }


//            if (dualHint != null) {
//                dualHint.hide();
//            }
//            if (savedDualHint != null) {
//                savedDualHint.hide();
//            }
//            cameraHint.hide();
            takingVideo = true;
//            outputFile = AndroidUtilities.generateVideoPath(photoAlert.parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) photoAlert.parentAlert.baseFragment).isSecretChat());
            checkFrontfaceFlashModes();
            isDark = false;
            if (cameraView.isFrontface() && frontfaceFlashMode == 1) {
                checkIsDark();
            }
            if (useDisplayFlashlight()) {
                flashViews.flashIn(() -> startRecording(byLongPress, whenStarted));
            } else {
                startRecording(byLongPress, whenStarted);
            }
        }

        private void startRecording(boolean byLongPress, Runnable whenStarted) {
            BaseFragment baseFragment = parentAlert.baseFragment;
            if (baseFragment == null) {
                baseFragment = LaunchActivity.getLastFragment();
            }
            if (baseFragment == null || baseFragment.getParentActivity() == null) {
                return;
            }

            showVideoTimer(true, true);

            outputFile = AndroidUtilities.generateVideoPath(parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat());
            AndroidUtilities.lockOrientation(parentAlert.baseFragment.getParentActivity());
            CameraController.getInstance().recordVideo(cameraView.getCameraSessionObject(), outputFile, parentAlert.avatarPicker != 0, (thumbPath, duration) -> {
                if (useDisplayFlashlight()) {
                    flashViews.flashOut();
                }
                showVideoTimer(false, true);
                animateRecording(false, true);
                if (recordControl != null) {
                    recordControl.stopRecordingLoading(true);
                }
                takingVideo = false;
                if (outputFile == null || parentAlert.destroyed || cameraView == null) {
                    return;
                }
                mediaFromExternalCamera = false;

                if (collageLayoutView.hasLayout()) {
                    StoryEntry entry = StoryEntry.fromVideoShoot(outputFile, thumbPath, duration);
                    outputFile = null;
                    entry.videoVolume = 1.0f;
                    if (collageLayoutView.push(entry)) {
                        outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
                        int width = cameraView.getVideoWidth(), height = cameraView.getVideoHeight();
                        if (width > 0 && height > 0) {
                            outputEntry.width = width;
                            outputEntry.height = height;
                            outputEntry.setupMatrix();
                        }
                    }
                    updateActionBarButtons(true);
                } else {
                    int width = 0, height = 0;
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(new File(thumbPath).getAbsolutePath(), options);
                        width = options.outWidth;
                        height = options.outHeight;
                    } catch (Exception ignore) {
                    }
                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, outputFile.getAbsolutePath(), 0, true, width, height, 0);
                    photoEntry.duration = (int) (duration / 1000f);
                    photoEntry.thumbPath = thumbPath;
                    if (parentAlert.avatarPicker != 0 && cameraView.isFrontface()) {
                        photoEntry.cropState = new MediaController.CropState();
                        photoEntry.cropState.mirrored = true;
                        photoEntry.cropState.freeform = false;
                        photoEntry.cropState.lockedAspectRatio = 1.0f;
                    }
                    openPhotoViewer(photoEntry, false, false);
                }
            }, () /* onVideoStart */ -> {
                whenStarted.run();

                animateRecording(true, true);

                collageListView.setVisible(false, true);
                videoTimerView.setRecording(true, true);
                showVideoTimer(true, true);
            }, cameraView);
            cameraView.runHaptic();

            if (!isVideo) {
                isVideo = true;
                showVideoTimer(isVideo, true);
                modeSwitcherView.switchMode(isVideo);
                recordControl.startAsVideo(isVideo);
            }
        }

        @Override
        public void onVideoRecordLocked() {
//            hintTextView.setText(getString(R.string.StoryHintPinchToZoom), true);
        }

        @Override
        public void onVideoRecordPause() {

        }

        @Override
        public void onVideoRecordResume() {

        }

        @Override
        public void onVideoRecordEnd(boolean byDuration) {
            if (cameraView == null || cameraView.getCameraSession() == null) {
                return;
            }
            resetRecordState();
            CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), false);

//            if (stoppingTakingVideo || !takingVideo) {
//                return;
//            }
//            stoppingTakingVideo = true;
//            AndroidUtilities.runOnUIThread(() -> {
//                if (takingVideo && stoppingTakingVideo && cameraDelegate != null) {
//                    showZoomControls(false, true);
////                    animateRecording(false, true);
////                    setAwakeLock(false);
//                    CameraController.getInstance().stopVideoRecording(cameraDelegate.cameraView.getCameraSessionRecording(), false, false);
//                }
//            }, byDuration ? 0 : 400);
        }

        @Override
        public void onVideoDuration(long duration) {
            videoTimerView.setDuration(duration, true);
        }

        @Override
        public void onGalleryClick() {
            if (cameraPhotoRecyclerView != null) {
                cancelTakingPhotos = false;
                cameraPhotoRecyclerView.setVisibility(View.VISIBLE);
                counterTextView.setAlpha(1.0f);
                updatePhotosCounter(false);
            }
//            if (currentPage == PAGE_CAMERA && !takingPhoto && !takingVideo && requestGalleryPermission()) {
//                animateGalleryListView(true);
//            }
        }

        @Override
        public void onFlipClick() {
            if (cameraView == null) {
                return;
            }
//            if (cameraDelegate == null || awaitingPlayer || takingPhoto || !cameraDelegate.cameraView.isInited() || currentPage != PAGE_CAMERA) {
//                return;
//            }
//            if (savedDualHint != null) {
//                savedDualHint.hide();
//            }
//            if (useDisplayFlashlight() && frontfaceFlashModes != null && !frontfaceFlashModes.isEmpty()) {
//                final String mode = frontfaceFlashModes.get(frontfaceFlashMode);
//                SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
//                sharedPreferences.edit().putString("flashMode", mode).commit();
//            }
            cameraView.switchCamera();
            if (useDisplayFlashlight()) {
                flashViews.flashIn(null);
            } else {
                flashViews.flashOut();
            }
        }

        @Override
        public void onFlipLongClick() {
            if (cameraView != null) {
                cameraView.toggleDual();
            }
        }

        @Override
        public void onZoom(float zoom) {
            zoomControlView.setZoom(zoom, true);
            showZoomControls(false, true);
        }
    };

    private int frontfaceFlashMode = -1;
    private ArrayList<String> frontfaceFlashModes;

    private void checkFrontfaceFlashModes() {
        if (frontfaceFlashMode < 0) {
            frontfaceFlashMode = MessagesController.getGlobalMainSettings().getInt("frontflash", 1);
            frontfaceFlashModes = new ArrayList<>();
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_OFF);
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_AUTO);
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_ON);

            flashViews.setWarmth(MessagesController.getGlobalMainSettings().getFloat("frontflash_warmth", .9f));
            flashViews.setIntensity(MessagesController.getGlobalMainSettings().getFloat("frontflash_intensity", 1));
        }
    }

    private void saveFrontFaceFlashMode() {
        if (frontfaceFlashMode >= 0) {
            MessagesController.getGlobalMainSettings().edit()
                    .putFloat("frontflash_warmth", flashViews.warmth)
                    .putFloat("frontflash_intensity", flashViews.intensity)
                    .apply();
        }
    }

    private String getCurrentFlashMode() {
        if (cameraView == null || cameraView.getCameraSession() == null) {
            return null;
        }
        if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
            checkFrontfaceFlashModes();
            return frontfaceFlashModes.get(frontfaceFlashMode);
        }
        return cameraView.getCameraSession().getCurrentFlashMode();
    }

    private String getNextFlashMode() {
        if (cameraView == null || cameraView.getCameraSession() == null) {
            return null;
        }
        if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
            checkFrontfaceFlashModes();
            return frontfaceFlashModes.get(frontfaceFlashMode + 1 >= frontfaceFlashModes.size() ? 0 : frontfaceFlashMode + 1);
        }
        return cameraView.getCameraSession().getNextFlashMode();
    }

    private void setCurrentFlashMode(String mode) {
        if (cameraView == null || cameraView.getCameraSession() == null) {
            return;
        }
        if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
            int index = frontfaceFlashModes.indexOf(mode);
            if (index >= 0) {
                frontfaceFlashMode = index;
                MessagesController.getGlobalMainSettings().edit().putInt("frontflash", frontfaceFlashMode).apply();
            }
            return;
        }
        cameraView.getCameraSession().setCurrentFlashMode(mode);
    }

    private boolean requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && parentAlert.baseFragment != null && parentAlert.baseFragment.getParentActivity() != null) {
            boolean granted = parentAlert.baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                parentAlert.baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 112);
                return false;
            }
        }
        return true;
    }

    public class CameraTouchView extends FrameLayout {

        private GestureDetectorFixDoubleTap gestureDetector;
        private ScaleGestureDetector scaleGestureDetector;

        public CameraTouchView(Context context) {
            super(context);
            gestureDetector = new GestureDetectorFixDoubleTap(context, new GestureListener());
            scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        }

        public void cancelGestures() {
            scaleGestureDetector.onTouchEvent(AndroidUtilities.emptyMotionEvent());
            gestureDetector.onTouchEvent(AndroidUtilities.emptyMotionEvent());
        }

        private int lastKeyboardHeight;


        private boolean flingDetected;

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (cameraView != null) {
                cameraView.onTouchEvent(ev);
            }
            if (collageLayoutView != null && collageLayoutView.hasLayout() && collageLayoutView.getFilledProgress() >= 1 && collageLayoutView.dispatchTouchEvent(ev)) {
                return false;
            }
//            if (counterTextView != null && counterTextView.onTouchEvent(ev)) {
//                return false;
//            }
            flingDetected = false;
//            if (collageListView != null && collageListView.isVisible()) {
//                final float y = containerView.getY() + actionBarContainer.getY() + collageListView.getY();
//                if (ev.getY() >= y && ev.getY() <= y + collageListView.getHeight() || touchInCollageList) {
//                    touchInCollageList = ev.getAction() != MotionEvent.ACTION_UP && ev.getAction() != MotionEvent.ACTION_CANCEL;
//                    return super.dispatchTouchEvent(ev);
//                } else {
//                    collageListView.setVisible(false, true);
//                    updateActionBarButtons(true);
//                }
//            }
//            if (touchInCollageList && (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL)) {
//                touchInCollageList = false;
//            }
            scaleGestureDetector.onTouchEvent(ev);
            gestureDetector.onTouchEvent(ev);
            if (ev.getAction() == MotionEvent.ACTION_UP && !flingDetected) {
                allowModeScroll = true;
                returnCamera();
//                if (containerView.getTranslationY() > 0) {
//                    if (dismissProgress > .4f) {
//                        close(true);
//                    } else {
//                        animateContainerBack();
//                    }
//                } else if (galleryListView != null && galleryListView.getTranslationY() > 0 && !galleryClosing) {
//                    animateGalleryListView(!takingVideo && galleryListView.getTranslationY() < galleryListView.getPadding());
//                }
//                galleryClosing = false;
                modeSwitcherView.stopScroll(0);
                scrollingY = false;
                scrollingX = false;
            }
            return true;
        }

        private void returnCamera() {
            if (collageLayoutView.getTranslationY() != 0) {
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(collageLayoutView, View.TRANSLATION_Y, 0.0f),
                        ObjectAnimator.ofFloat(cameraControlContainer, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 1.0f));
                animatorSet.setDuration(250);
                animatorSet.setInterpolator(interpolator);
                animatorSet.start();
                cameraControlContainer.setTag(null);
            }
        }

        @Override
        public boolean dispatchKeyEventPreIme(KeyEvent event) {
            if (event != null && event.getKeyCode()
                    == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                onBackPressed();
                return true;
            }
            return super.dispatchKeyEventPreIme(event);
        }

        private boolean scaling = false;

        private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (!scaling || cameraView == null || cameraView.isDualTouch() || !cameraOpened) {
                    return false;
                }
                final float deltaScaleFactor = (detector.getScaleFactor() - 1.0f) * .75f;
                cameraZoom += deltaScaleFactor;
                cameraZoom = Utilities.clamp(cameraZoom, 1, 0);
                cameraView.setZoom(cameraZoom);
                if (zoomControlView != null) {
                    zoomControlView.setZoom(cameraZoom, false);
                }
                showZoomControls(true, true);
                return true;
            }

            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                if (cameraView == null || !cameraOpened) {
                    return false;
                }
                scaling = true;
                return super.onScaleBegin(detector);
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                scaling = false;
//                animateGalleryListView(false);
//                animateContainerBack();
                super.onScaleEnd(detector);
            }
        }

        private float ty, sty, stx;
        private boolean allowModeScroll = true;

        private final class GestureListener extends GestureDetectorFixDoubleTap.OnGestureListener {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                sty = 0;
                stx = 0;
                return false;
            }

            @Override
            public void onShowPress(@NonNull MotionEvent e) {

            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                scrollingY = false;
                scrollingX = false;
                if (!hasDoubleTap(e)) {
                    if (onSingleTapConfirmed(e)) {
                        return true;
                    }
                }
//                if (isGalleryOpen() && e.getY() < galleryListView.top()) {
//                    animateGalleryListView(false);
//                    return true;
//                }
                return false;
            }

            @Override
            public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                if (recordControl.isTouch() || cameraView != null && cameraView.isDualTouch() || scaling || zoomControlView != null && zoomControlView.isTouch()) {
                    return false;
                }
                if (takingVideo || takingPhoto || !cameraOpened) {
                    return false;
                }
                if (!scrollingX) {
                    sty += distanceY;
                    if (!scrollingY && Math.abs(sty) >= touchSlop) {
                        scrollingY = true;
                    }
                }
                if (scrollingY) {
                    if (collageLayoutView != null) {
                        collageLayoutView.setTranslationY(collageLayoutView.getTranslationY() - distanceY);
                        zoomControlView.setTag(null);
                        if (zoomControlHideRunnable != null) {
                            AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable);
                            zoomControlHideRunnable = null;
                        }
                        if (cameraControlContainer.getTag() == null) {
                            cameraControlContainer.setTag(1);
                            AnimatorSet animatorSet = new AnimatorSet();
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(cameraControlContainer, View.ALPHA, 0.0f),
                                    ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, 0.0f),
                                    ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 0.0f),
                                    ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 0.0f));
                            animatorSet.setDuration(220);
                            animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
                            animatorSet.start();
                        }
                    }
                }
//                if (scrollingY) {
//                    int galleryMax = windowView.getMeasuredHeight() - (int) (AndroidUtilities.displaySize.y * 0.35f) - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight());
//                    if (galleryListView == null || galleryListView.getTranslationY() >= galleryMax) {
//                        ty = containerView.getTranslationY1();
//                    } else {
//                        ty = galleryListView.getTranslationY() - galleryMax;
//                    }
//                    if (galleryListView != null && galleryListView.listView.canScrollVertically(-1)) {
//                        distanceY = Math.max(0, distanceY);
//                    }
//                    ty -= distanceY;
//                    ty = Math.max(-galleryMax, ty);
//                    if (currentPage == PAGE_PREVIEW) {
//                        ty = Math.max(0, ty);
//                    }
//                    if (ty >= 0) {
//                        containerView.setTranslationY(ty);
//                        if (galleryListView != null) {
//                            galleryListView.setTranslationY(galleryMax);
//                        }
//                    } else {
//                        containerView.setTranslationY(0);
//                        if (galleryListView == null) {
//                            createGalleryListView();
//                        }
//                        galleryListView.setTranslationY(galleryMax + ty);
//                    }
//                }
                if (!scrollingY) {
                    stx += distanceX;
                    if (!scrollingX && Math.abs(stx) >= touchSlop) {
                        scrollingX = true;
                    }
                }
                if (scrollingX) {
                    modeSwitcherView.scrollX(distanceX);
                }
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {

            }

            @Override
            public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (recordControl.isTouch() || cameraView != null && cameraView.isDualTouch() || scaling || zoomControlView != null && zoomControlView.isTouch()) {
                    return false;
                }
                flingDetected = true;
                allowModeScroll = true;
                boolean r = false;
                if (scrollingY) {
//                    if (Math.abs(containerView.getTranslationY1()) >= dp(1)) {
//                        if (velocityY > 0 && Math.abs(velocityY) > 2000 && Math.abs(velocityY) > Math.abs(velocityX) || dismissProgress > .4f) {
//                            close(true);
//                        } else {
//                            animateContainerBack();
//                        }
//                        r = true;
//                    } else if (galleryListView != null && !galleryClosing) {
//                        if (Math.abs(velocityY) > 200 && (!galleryListView.listView.canScrollVertically(-1) || !wasGalleryOpen)) {
//                            animateGalleryListView(!takingVideo && velocityY < 0);
//                            r = true;
//                        } else {
//                            animateGalleryListView(!takingVideo && galleryListView.getTranslationY() < galleryListView.getPadding());
//                            r = true;
//                        }
//                    }

                    if (collageLayoutView != null) {
                        if (!takingVideo && Math.abs(collageLayoutView.getTranslationY()) > collageLayoutView.getMeasuredHeight() / 6.0f) {
                            closeCamera(true);
                        } else {
                            returnCamera();
                        }
                    }
                }
                if (scrollingX) {
                    r = modeSwitcherView.stopScroll(velocityX) || r;
                }
//                galleryClosing = false;
                scrollingY = false;
                scrollingX = false;
                return r;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (cameraView != null) {
                    cameraView.allowToTapFocus();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (cameraView == null || takingPhoto || !cameraView.isInited() || !cameraOpened) {
                    return false;
                }
                cameraView.switchCamera();
                recordControl.rotateFlip(180);
//                saveCameraFace(cameraDelegate.cameraView.isFrontface());
                if (useDisplayFlashlight()) {
                    flashViews.flashIn(null);
                } else {
                    flashViews.flashOut();
                }
                return true;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                if (cameraView != null) {
                    cameraView.clearTapFocus();
                }
                return false;
            }

            @Override
            public boolean hasDoubleTap(MotionEvent e) {
                return cameraOpened && cameraView != null && cameraView.isInited() && !takingPhoto && !recordControl.isTouch();
            }
        }
    }

    private boolean videoTimerShown = true;

    private void showVideoTimer(boolean show, boolean animated) {
        if (videoTimerShown == show || videoTimerView == null) {
            return;
        }

        videoTimerShown = show;
        if (animated) {
            videoTimerView.animate().alpha(show ? 1 : 0).setDuration(350).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
                if (!show) {
                    videoTimerView.setRecording(false, false);
                }
            }).start();
        } else {
            videoTimerView.clearAnimation();
            videoTimerView.setAlpha(show ? 1 : 0);
            if (!show) {
                videoTimerView.setRecording(false, false);
            }
        }
    }

    private void updateActionBarButtons(boolean animated) {
        showVideoTimer(cameraOpened && isVideo && !collageListView.isVisible() && !inCheck(), animated);
        collageButton.setSelected(collageLayoutView.hasLayout());
        setActionBarButtonVisible(backButton, collageListView == null || !collageListView.isVisible(), animated);
        setActionBarButtonVisible(flashButton, !animatedRecording && cameraOpened && flashButtonMode != null && !collageListView.isVisible() && !inCheck(), animated);
        setActionBarButtonVisible(dualButton, !animatedRecording && cameraOpened && cameraView != null && cameraView.dualAvailable() && !collageListView.isVisible() && !collageLayoutView.hasLayout(), animated);
        setActionBarButtonVisible(collageButton, cameraOpened && !collageListView.isVisible(), animated);
        setActionBarButtonVisible(collageRemoveButton, collageListView.isVisible(), animated);
        final float collageProgress = collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f;
        recordControl.setCollageProgress(collageProgress, animated);
        animateRecording(animatedRecording, animated);
    }

    private boolean inCheck() {
        final float collageProgress = collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f;
        return !animatedRecording && collageProgress >= 1.0f;
    }

    private void updateActionBarButtonsOffsets() {
        float right = 0;
        collageRemoveButton.setTranslationX(-right);
        right += dp(46) * collageRemoveButton.getAlpha();
        dualButton.setTranslationX(-right);
        right += dp(46) * dualButton.getAlpha();
        collageButton.setTranslationX(-right);
        right += dp(46) * collageButton.getAlpha();
        flashButton.setTranslationX(-right);
        right += dp(46) * flashButton.getAlpha();

        float left = 0;
        backButton.setTranslationX(left);
        left += dp(46) * backButton.getAlpha();

        collageListView.setBounds(left + dp(8), right + dp(8));
    }

    public void setActionBarButtonVisible(View view, boolean visible, boolean animated) {
        if (view == null) return;
        if (animated) {
            view.setVisibility(View.VISIBLE);
            view.animate()
                    .alpha(visible ? 1.0f : 0.0f)
                    .setUpdateListener(animation -> updateActionBarButtonsOffsets())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            updateActionBarButtonsOffsets();
                            if (!visible) {
                                view.setVisibility(View.GONE);
                            }
                        }
                    })
                    .setDuration(320)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
        } else {
            view.animate().cancel();
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            view.setAlpha(visible ? 1.0f : 0.0f);
            updateActionBarButtonsOffsets();
        }
    }

    public ChatAttachAlertPhotoLayout(ChatAttachAlert alert, Context context, boolean forceDarkTheme, boolean needCamera, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);
        this.forceDarkTheme = forceDarkTheme;
        this.needCamera = needCamera;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.cameraInitied);
        FrameLayout container = alert.getContainer();

        blurManager = new BlurringShader.BlurManager(container);

        showAvatarConstructor = parentAlert.avatarPicker != 0;

        cameraDrawable = context.getResources().getDrawable(R.drawable.instant_camera).mutate();

        ActionBarMenu menu = parentAlert.actionBar.createMenu();
        dropDownContainer = new ActionBarMenuItem(context, menu, 0, 0, resourcesProvider) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setText(dropDown.getText());
            }
        };
        dropDownContainer.setSubMenuOpenSide(1);
        parentAlert.actionBar.addView(dropDownContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 64 : 56, 0, 40, 0));
        dropDownContainer.setOnClickListener(view -> dropDownContainer.toggleSubMenu());

        dropDown = new TextView(context);
        dropDown.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dropDown.setGravity(Gravity.LEFT);
        dropDown.setSingleLine(true);
        dropDown.setLines(1);
        dropDown.setMaxLines(1);
        dropDown.setEllipsize(TextUtils.TruncateAt.END);
        dropDown.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        dropDown.setText(LocaleController.getString(R.string.ChatGallery));
        dropDown.setTypeface(AndroidUtilities.bold());
        dropDownDrawable = context.getResources().getDrawable(R.drawable.ic_arrow_drop_down).mutate();
        dropDownDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
        dropDown.setCompoundDrawablePadding(dp(4));
        dropDown.setPadding(0, 0, dp(10), 0);
        dropDownContainer.addView(dropDown, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        checkCamera(false);

        captionItem = new MessagePreviewView.ToggleButton(
                context,
                R.raw.position_below, getString(R.string.CaptionAbove),
                R.raw.position_above, getString(R.string.CaptionBelow),
                resourcesProvider
        );
        captionItem.setState(!parentAlert.captionAbove, false);

        previewItem = parentAlert.selectedMenuItem.addSubItem(preview, R.drawable.msg_view_file, LocaleController.getString(R.string.AttachMediaPreviewButton));

        parentAlert.selectedMenuItem.addColoredGap(preview_gap);
        parentAlert.selectedMenuItem.addSubItem(open_in, R.drawable.msg_openin, LocaleController.getString(R.string.OpenInExternalApp));
        compressItem = parentAlert.selectedMenuItem.addSubItem(compress, R.drawable.msg_filehq, LocaleController.getString(R.string.SendWithoutCompression));
        parentAlert.selectedMenuItem.addSubItem(group, R.drawable.msg_ungroup, LocaleController.getString(R.string.SendWithoutGrouping));
        parentAlert.selectedMenuItem.addColoredGap(media_gap);
        spoilerItem = parentAlert.selectedMenuItem.addSubItem(spoiler, R.drawable.msg_spoiler, LocaleController.getString(R.string.EnablePhotoSpoiler));
        parentAlert.selectedMenuItem.addSubItem(caption, captionItem);
        starsItem = parentAlert.selectedMenuItem.addSubItem(stars, R.drawable.menu_feature_paid, getString(R.string.PaidMediaButton));
        parentAlert.selectedMenuItem.setFitSubItems(true);

        gridView = new RecyclerListView(context, resourcesProvider) {
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < parentAlert.scrollOffsetY[0] - dp(80)) {
                    return false;
                }
                return super.onTouchEvent(e);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < parentAlert.scrollOffsetY[0] - dp(80)) {
                    return false;
                }
                return super.onInterceptTouchEvent(e);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                PhotoViewer.getInstance().checkCurrentImageVisibility();
            }
        };
        gridView.setFastScrollEnabled(RecyclerListView.FastScroll.DATE_TYPE);
        gridView.setFastScrollVisible(true);
        gridView.getFastScroll().setAlpha(0f);
        gridView.getFastScroll().usePadding = false;
        gridView.setAdapter(adapter = new PhotoAttachAdapter(context, needCamera));
        adapter.createCache();
        gridView.setClipToPadding(false);
        gridView.setItemAnimator(null);
        gridView.setLayoutAnimation(null);
        gridView.setVerticalScrollBarEnabled(false);
        gridView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        gridView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            boolean parentPinnedToTop;

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (gridView.getChildCount() <= 0) {
                    return;
                }
                parentAlert.updateLayout(ChatAttachAlertPhotoLayout.this, true, dy);
                if (adapter.getTotalItemsCount() > SHOW_FAST_SCROLL_MIN_COUNT) {
                    if (parentPinnedToTop != parentAlert.pinnedToTop) {
                        parentPinnedToTop = parentAlert.pinnedToTop;
                        gridView.getFastScroll().animate().alpha(parentPinnedToTop ? 1f : 0f).setDuration(100).start();
                    }
                } else {
                    gridView.getFastScroll().setAlpha(0);
                }
                if (dy != 0) {
                    checkCameraViewPosition();
                }
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = dp(13) + (parentAlert.selectedMenuItem != null ? dp(parentAlert.selectedMenuItem.getAlpha() * 26) : 0);
                    int backgroundPaddingTop = parentAlert.getBackgroundPaddingTop();
                    int top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight() + parentAlert.topCommentContainer.getMeasuredHeight() * parentAlert.topCommentContainer.getAlpha()) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > dp(7)) {
                            gridView.smoothScrollBy(0, holder.itemView.getTop() - dp(7));
                        }
                    }
                }
            }
        });
        layoutManager = new GridLayoutManager(context, itemSize) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public int calculateDyToMakeVisible(View view, int snapPreference) {
                        int dy = super.calculateDyToMakeVisible(view, snapPreference);
                        dy -= (gridView.getPaddingTop() - dp(7));
                        return dy;
                    }

                    @Override
                    protected int calculateTimeForDeceleration(int dx) {
                        return super.calculateTimeForDeceleration(dx) * 2;
                    }
                };
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        };
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position == adapter.itemsCount - 1) {
                    return layoutManager.getSpanCount();
                }
                return itemSize + (position % itemsPerRow != itemsPerRow - 1 ? dp(5) : 0);
            }
        });
        gridView.setLayoutManager(layoutManager);
        gridView.setOnItemClickListener((view, position, x, y) -> {
            if (!mediaEnabled || parentAlert.destroyed) {
                return;
            }
            BaseFragment fragment = parentAlert.baseFragment;
            if (fragment == null) {
                fragment = LaunchActivity.getLastFragment();
            }
            if (fragment == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 23) {
                if (adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry && position == 0 && noCameraPermissions) {
                    try {
                        fragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 18);
                    } catch (Exception ignore) {

                    }
                    return;
                } else if (noGalleryPermissions) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        try {
                            fragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                        } catch (Exception ignore) {
                        }
                    } else {
                        try {
                            fragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                        } catch (Exception ignore) {
                        }
                    }
                    return;
                }
            }
            if (position != 0 || !needCamera || selectedAlbumEntry != galleryAlbumEntry) {
                if (selectedAlbumEntry == galleryAlbumEntry && needCamera) {
                    position--;
                }
                if (showAvatarConstructor) {
                    if (position == 0) {
                        if (!(view instanceof AvatarConstructorPreviewCell)) {
                            return;
                        }
                        showAvatarConstructorFragment((AvatarConstructorPreviewCell) view, null);
                        parentAlert.dismiss();
                    }
                    position--;
                }
                ArrayList<Object> arrayList = getAllPhotosArray();
                if (position < 0 || position >= arrayList.size()) {
                    return;
                }
                if (parentAlert.delegate != null && parentAlert.delegate.selectItemOnClicking() && arrayList.get(position) instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) arrayList.get(position);
                    selectedPhotos.clear();
                    if (photoEntry != null) {
                        addToSelectedPhotos(photoEntry, -1);
                    }
                    parentAlert.applyCaption();
                    parentAlert.delegate.didPressedButton(7, true, true, 0, 0, parentAlert.isCaptionAbove(), false);
                    selectedPhotos.clear();
                    cameraPhotos.clear();
                    selectedPhotosOrder.clear();
                    selectedPhotos.clear();
                    return;
                }
                PhotoViewer.getInstance().setParentActivity(fragment, resourcesProvider);
                PhotoViewer.getInstance().setParentAlert(parentAlert);
                PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder);
                ChatActivity chatActivity;
                int type;
                if (parentAlert.isPhotoPicker && parentAlert.isStickerMode) {
                    type = PhotoViewer.SELECT_TYPE_STICKER;
                    if (parentAlert.baseFragment instanceof ChatActivity) {
                        chatActivity = (ChatActivity) parentAlert.baseFragment;
                    } else {
                        chatActivity = null;
                    }
                } else if (parentAlert.avatarPicker != 0) {
                    chatActivity = null;
                    type = PhotoViewer.SELECT_TYPE_AVATAR;
                } else if (parentAlert.baseFragment instanceof ChatActivity) {
                    chatActivity = (ChatActivity) parentAlert.baseFragment;
                    type = 0;
                } else if (parentAlert.allowEnterCaption) {
                    chatActivity = null;
                    type = 0;
                } else {
                    chatActivity = null;
                    type = 4;
                }
                if (!parentAlert.delegate.needEnterComment()) {
                    AndroidUtilities.hideKeyboard(fragment.getFragmentView().findFocus());
                    AndroidUtilities.hideKeyboard(parentAlert.getContainer().findFocus());
                }
                if (selectedPhotos.size() > 0 && selectedPhotosOrder.size() > 0) {
                    Object o = selectedPhotos.get(selectedPhotosOrder.get(0));
                    if (o instanceof MediaController.PhotoEntry) {
                        MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                        photoEntry1.caption = parentAlert.getCommentView().getText();
                    }
                    if (o instanceof MediaController.SearchImage) {
                        MediaController.SearchImage photoEntry1 = (MediaController.SearchImage) o;
                        photoEntry1.caption = parentAlert.getCommentView().getText();
                    }
                }
                if (parentAlert.getAvatarFor() != null) {
                    boolean isVideo = false;
                    if (arrayList.get(position) instanceof MediaController.PhotoEntry) {
                        isVideo = ((MediaController.PhotoEntry) arrayList.get(position)).isVideo;
                    }
                    parentAlert.getAvatarFor().isVideo = isVideo;
                }

                boolean hasSpoiler = arrayList.get(position) instanceof MediaController.PhotoEntry && ((MediaController.PhotoEntry) arrayList.get(position)).hasSpoiler;
                Object object = arrayList.get(position);
                if (object instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                    if (checkSendMediaEnabled(photoEntry)) {
                        return;
                    }
                }
                if (hasSpoiler) {
                    setCurrentSpoilerVisible(position, false);
                }
                int finalPosition = position;
                BaseFragment finalFragment = fragment;
                AndroidUtilities.runOnUIThread(() -> {
                    int avatarType = type;
                    if (parentAlert.isPhotoPicker && !parentAlert.isStickerMode) {
                        PhotoViewer.getInstance().setParentActivity(finalFragment);
                        PhotoViewer.getInstance().setMaxSelectedPhotos(0, false);
                        avatarType = PhotoViewer.SELECT_TYPE_WALLPAPER;
                    }
                    PhotoViewer.getInstance().openPhotoForSelect(arrayList, finalPosition, avatarType, false, photoViewerProvider, chatActivity);
                    PhotoViewer.getInstance().setAvatarFor(parentAlert.getAvatarFor());
                    if (parentAlert.isPhotoPicker && !parentAlert.isStickerMode) {
                        PhotoViewer.getInstance().closePhotoAfterSelect = false;
                    }
                    if (parentAlert.isStickerMode) {
                        PhotoViewer.getInstance().enableStickerMode(null, false, parentAlert.customStickerHandler);
                    }
                    if (captionForAllMedia()) {
                        PhotoViewer.getInstance().setCaption(parentAlert.getCommentView().getText());
                    }
                }, hasSpoiler ? 250 : 0);
            } else {
                if (SharedConfig.inappCamera) {
                    openCamera(true);
                } else {
                    if (parentAlert.delegate != null) {
                        parentAlert.delegate.didPressedButton(0, false, true, 0, 0, parentAlert.isCaptionAbove(), false);
                    }
                }
            }
        });
        gridView.setOnItemLongClickListener((view, position) -> {
            if (parentAlert.storyMediaPicker) {
                return false;
            }
            if (position == 0 && selectedAlbumEntry == galleryAlbumEntry) {
                if (parentAlert.delegate != null) {
                    parentAlert.delegate.didPressedButton(0, false, true, 0, 0, parentAlert.isCaptionAbove(), false);
                }
                return true;
            } else if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                itemRangeSelector.setIsActive(view, true, position, shouldSelect = !cell.isChecked());
            }
            return false;
        });
        itemRangeSelector = new RecyclerViewItemRangeSelector(new RecyclerViewItemRangeSelector.RecyclerViewItemRangeSelectorDelegate() {
            @Override
            public int getItemCount() {
                return adapter.getItemCount();
            }

            @Override
            public void setSelected(View view, int index, boolean selected) {
                if (selected != shouldSelect || !(view instanceof PhotoAttachPhotoCell)) {
                    return;
                }
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                cell.callDelegate();
            }

            @Override
            public boolean isSelected(int index) {
                MediaController.PhotoEntry entry = adapter.getPhoto(index);
                return entry != null && selectedPhotos.containsKey(entry.imageId);
            }

            @Override
            public boolean isIndexSelectable(int index) {
                return adapter.getItemViewType(index) == 0;
            }

            @Override
            public void onStartStopSelection(boolean start) {
                alertOnlyOnce = start ? 1 : 0;
                gridView.hideSelector(true);
            }
        });
        gridView.addOnItemTouchListener(itemRangeSelector);

        progressView = new EmptyTextProgressView(context, null, resourcesProvider);
        progressView.setText(LocaleController.getString(R.string.NoPhotos));
        progressView.setOnTouchListener(null);
        progressView.setTextSize(16);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (loading) {
            progressView.showProgress();
        } else {
            progressView.showTextView();
        }

        Paint recordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        recordPaint.setColor(0xffda564d);

        // TODO camera container
        cameraControlContainer = new FrameLayout(context);
        cameraControlContainer.setVisibility(View.GONE);
        cameraControlContainer.setAlpha(0.0f);
        cameraControlContainer.setFocusable(true);
        container.addView(cameraControlContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        cameraTouchView = new CameraTouchView(context);
        cameraControlContainer.addView(cameraTouchView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        flashViews = new FlashViews(context, null, cameraControlContainer, null);
        flashViews.add(new FlashViews.Invertable() {
            @Override
            public void setInvert(float invert) {
                AndroidUtilities.setLightNavigationBar(container, invert > 0.5f);
                AndroidUtilities.setLightStatusBar(container, invert > 0.5f);
            }

            @Override
            public void invalidate() {
            }
        });
        cameraControlContainer.addView(flashViews.backgroundView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cameraControlContainer.addView(flashViews.foregroundView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int topActionPadding = AndroidUtilities.statusBarHeight;
        actionBarContainer = new FrameLayout(context);
        FrameLayout.LayoutParams barContainerParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        barContainerParams.topMargin = topActionPadding;
        cameraControlContainer.addView(actionBarContainer, barContainerParams);

        actionBarButtons = new LinearLayout(context);
        actionBarButtons.setOrientation(LinearLayout.HORIZONTAL);
        actionBarButtons.setGravity(Gravity.RIGHT);
        actionBarContainer.addView(actionBarButtons, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.FILL_HORIZONTAL));

        zoomControlView = new ZoomControlView(context);
        zoomControlView.setVisibility(View.GONE);
        zoomControlView.setAlpha(0.0f);
        container.addView(zoomControlView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 50, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 166));
        zoomControlView.setDelegate(zoom -> {
            if (cameraView != null) {
                cameraView.setZoom(cameraZoom = zoom);
            }
            showZoomControls(true, true);
        });

        counterTextView = new TextView(context);
        counterTextView.setBackgroundResource(R.drawable.photos_rounded);
        counterTextView.setVisibility(View.GONE);
        counterTextView.setTextColor(0xffffffff);
        counterTextView.setGravity(Gravity.CENTER);
        counterTextView.setPivotX(0);
        counterTextView.setPivotY(0);
        counterTextView.setTypeface(AndroidUtilities.bold());
        counterTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.photos_arrow, 0);
        counterTextView.setCompoundDrawablePadding(dp(4));
        counterTextView.setPadding(dp(16), 0, dp(16), 0);
        container.addView(counterTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 38, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 166));
        counterTextView.setOnClickListener(v -> {
            if (cameraView == null) {
                return;
            }
            openPhotoViewer(null, false, false);
        });

        backButton = new FlashViews.ImageViewInvertable(context);
        backButton.setContentDescription(getString(R.string.Close));
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setImageResource(R.drawable.ic_close_white);
        backButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        backButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        backButton.setOnClickListener(e -> {
            closeCamera(true);
        });
        actionBarContainer.addView(backButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.LEFT));
        flashViews.add(backButton);

        flashButton = new ToggleButton2(context);
        flashButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        flashButton.setOnClickListener(e -> {
            if (cameraView == null) {
                return;
            }
            String current = getCurrentFlashMode();
            String next = getNextFlashMode();
            if (current == null || current.equals(next)) {
                return;
            }
            setCurrentFlashMode(next);
            setCameraFlashModeIcon(next, true);
        });
        flashButton.setOnLongClickListener(e -> {
            if (cameraView == null || !cameraView.isFrontface()) {
                return false;
            }

            checkFrontfaceFlashModes();
            flashButton.setSelected(true);
            flashViews.previewStart();
            ItemOptions.makeOptions(cameraControlContainer, resourcesProvider, flashButton)
                    .addView(
                            new SliderView(getContext(), SliderView.TYPE_WARMTH)
                                    .setValue(flashViews.warmth)
                                    .setOnValueChange(v -> {
                                        flashViews.setWarmth(v);
                                    })
                    )
                    .addSpaceGap()
                    .addView(
                            new SliderView(getContext(), SliderView.TYPE_INTENSITY)
                                    .setMinMax(.65f, 1f)
                                    .setValue(flashViews.intensity)
                                    .setOnValueChange(v -> {
                                        flashViews.setIntensity(v);
                                    })
                    )
                    .setOnDismiss(() -> {
                        saveFrontFaceFlashMode();
                        flashViews.previewEnd();
                        flashButton.setSelected(false);
                    })
                    .setDimAlpha(0)
                    .setGravity(Gravity.RIGHT)
                    .translate(dp(46), -dp(4))
                    .setBackgroundColor(0xbb1b1b1b)
                    .show();
            return true;
        });
        flashButton.setVisibility(View.GONE);
        flashButton.setAlpha(0f);
        flashViews.add(flashButton);
        actionBarContainer.addView(flashButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        dualButton = new ToggleButton(context, R.drawable.media_dual_camera2_shadow, R.drawable.media_dual_camera2);
        dualButton.setOnClickListener(v -> {
            if (cameraView == null) {
                return;
            }
            cameraView.toggleDual();
            dualButton.setValue(cameraView.isDual());
        });
        dualButton.setVisibility(DualCameraView.dualAvailableStatic(context) ? View.VISIBLE : View.GONE);
        flashViews.add(dualButton);
        actionBarContainer.addView(dualButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        collageButton = new CollageLayoutButton(context);
        collageButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        if (lastCollageLayout == null) {
            lastCollageLayout = CollageLayout.getLayouts().get(6);
        }
        collageButton.setOnClickListener(v -> {
            if (!cameraOpened || animatedRecording) return;
            if (cameraView != null && cameraView.isDual()) {
                cameraView.toggleDual();
            }
            if (!collageListView.isVisible() && !collageLayoutView.hasLayout()) {
                collageLayoutView.setLayout(lastCollageLayout, true);
                collageListView.setSelected(lastCollageLayout);
                collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout), true);
                collageButton.setSelected(true);
                if (cameraView != null) {
                    cameraView.recordHevc = !collageLayoutView.hasLayout();
                }
            }
            collageListView.setVisible(!collageListView.isVisible(), true);
            updateActionBarButtons(true);
        });
        collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout), false);
        collageButton.setSelected(false);
        collageButton.setVisibility(View.VISIBLE);
        collageButton.setAlpha(1.0f);
        flashViews.add(collageButton);
        actionBarContainer.addView(collageButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        collageRemoveButton = new ToggleButton2(context);
        collageRemoveButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        collageRemoveButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(new CollageLayout("../../.."), true), false);
        collageRemoveButton.setVisibility(View.GONE);
        collageRemoveButton.setAlpha(0.0f);
        collageRemoveButton.setOnClickListener(v -> {
            closeCollage();
        });
        flashViews.add(collageRemoveButton);
        actionBarContainer.addView(collageRemoveButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        collageListView = new CollageLayoutButton.CollageLayoutListView(context, flashViews);
        collageListView.listView.scrollToPosition(6);
        collageListView.setSelected(null);
        collageListView.setOnLayoutClick(layout -> {
            collageLayoutView.setLayout(lastCollageLayout = layout, true);
            collageListView.setSelected(layout);
            if (cameraView != null) {
                cameraView.recordHevc = !collageLayoutView.hasLayout();
            }
            collageButton.setDrawable(new CollageLayoutButton.CollageLayoutDrawable(layout));
            setActionBarButtonVisible(collageRemoveButton, collageListView.isVisible(), true);
            recordControl.setCollageProgress(collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f, true);
        });
        actionBarContainer.addView(collageListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.RIGHT));

        videoTimerView = new VideoTimerView(context);
        showVideoTimer(false, false);
        actionBarContainer.addView(videoTimerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER | Gravity.FILL_HORIZONTAL, 56, 0, 56, 6));
        flashViews.add(videoTimerView);

        recordControl = new RecordControl(context);
        cameraControlContainer.addView(recordControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 60));
        recordControl.startAsVideo(isVideo);
        recordControl.setContentDescription(LocaleController.getString(R.string.AccDescrShutter));
        recordControl.setDelegate(recordControlDelegate);
        recordControl.setMaxDuration(-1);
        flashViews.add(recordControl);

        modeSwitcherView = new PhotoVideoSwitcherView(context) {
            @Override
            protected boolean allowTouch() {
                return !inCheck();
            }
        };
        modeSwitcherView.setOnSwitchModeListener(newIsVideo -> {
            if (takingPhoto || takingVideo) {
                return;
            }

            isVideo = newIsVideo;
            showVideoTimer(isVideo && !collageListView.isVisible(), true);
            modeSwitcherView.switchMode(isVideo);
            recordControl.startAsVideo(isVideo);
        });
        modeSwitcherView.setOnSwitchingModeListener(t -> {
            recordControl.startAsVideoT(t);
        });
        cameraControlContainer.addView(modeSwitcherView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        flashViews.add(modeSwitcherView);

        // TODO удалить кнопку для записи видео
        tooltipTextView = new TextView(context);
        tooltipTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tooltipTextView.setTextColor(0xffffffff);
        tooltipTextView.setText(LocaleController.getString(R.string.TapForVideo));
        tooltipTextView.setShadowLayer(dp(3.33333f), 0, dp(0.666f), 0x4c000000);
        tooltipTextView.setPadding(dp(6), 0, dp(6), 0);
//        cameraPanel.addView(tooltipTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 16));

        cameraPhotoRecyclerView = new RecyclerListView(context, resourcesProvider) {
            @Override
            public void requestLayout() {
                if (cameraPhotoRecyclerViewIgnoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        cameraPhotoRecyclerView.setVerticalScrollBarEnabled(true);
        cameraPhotoRecyclerView.setAdapter(cameraAttachAdapter = new PhotoAttachAdapter(context, false));
        cameraAttachAdapter.createCache();
        cameraPhotoRecyclerView.setClipToPadding(false);
        cameraPhotoRecyclerView.setPadding(dp(8), 0, dp(8), 0);
        cameraPhotoRecyclerView.setItemAnimator(null);
        cameraPhotoRecyclerView.setLayoutAnimation(null);
        cameraPhotoRecyclerView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        cameraPhotoRecyclerView.setVisibility(View.INVISIBLE);
        cameraPhotoRecyclerView.setAlpha(0.0f);
        container.addView(cameraPhotoRecyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80));
        cameraPhotoLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        cameraPhotoRecyclerView.setLayoutManager(cameraPhotoLayoutManager);
        cameraPhotoRecyclerView.setOnItemClickListener((view, position) -> {
            if (view instanceof PhotoAttachPhotoCell) {
                ((PhotoAttachPhotoCell) view).callDelegate();
            }
        });

        updateActionBarButtonsOffsets();
    }

    public void showAvatarConstructorFragment(AvatarConstructorPreviewCell view, TLRPC.VideoSize emojiMarkupStrat) {
        AvatarConstructorFragment avatarConstructorFragment = new AvatarConstructorFragment(parentAlert.parentImageUpdater, parentAlert.getAvatarFor());
        avatarConstructorFragment.finishOnDone = !(parentAlert.getAvatarFor() != null && parentAlert.getAvatarFor().type == ImageUpdater.TYPE_SUGGEST_PHOTO_FOR_USER);
        parentAlert.baseFragment.presentFragment(avatarConstructorFragment);
        if (view != null) {
            avatarConstructorFragment.startFrom(view);
        }
        if (emojiMarkupStrat != null) {
            avatarConstructorFragment.startFrom(emojiMarkupStrat);
        }
        avatarConstructorFragment.setDelegate((gradient, documentId, document, previewView) -> {
            selectedPhotos.clear();
            Bitmap bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            GradientTools gradientTools = new GradientTools();
            if (gradient != null) {
                gradientTools.setColors(gradient.color1, gradient.color2, gradient.color3, gradient.color4);
            } else {
                gradientTools.setColors(AvatarConstructorFragment.defaultColors[0][0], AvatarConstructorFragment.defaultColors[0][1], AvatarConstructorFragment.defaultColors[0][2], AvatarConstructorFragment.defaultColors[0][3]);
            }
            gradientTools.setBounds(0, 0, 800, 800);
            canvas.drawRect(0, 0, 800, 800, gradientTools.paint);

            File file = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), SharedConfig.getLastLocalId() + "avatar_background.png");
            try {
                file.createNewFile();

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 0, bos);
                byte[] bitmapdata = bos.toByteArray();

                FileOutputStream fos = new FileOutputStream(file);
                fos.write(bitmapdata);
                fos.flush();
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            float scale = AvatarConstructorFragment.STICKER_DEFAULT_SCALE;
            int imageX, imageY;
            imageX = imageY = (int) (800 * (1f - scale) / 2f);
            int imageSize = (int) (800 * scale);

            ImageReceiver imageReceiver = previewView.getImageReceiver();
            if (imageReceiver.getAnimation() != null) {
                Bitmap firstFrame = imageReceiver.getAnimation().getFirstFrame(null);
                ImageReceiver firstFrameReceiver = new ImageReceiver();
                firstFrameReceiver.setImageBitmap(firstFrame);
                firstFrameReceiver.setImageCoords(imageX, imageY, imageSize, imageSize);
                firstFrameReceiver.setRoundRadius((int) (imageSize * AvatarConstructorFragment.STICKER_DEFAULT_ROUND_RADIUS));
                firstFrameReceiver.draw(canvas);
                firstFrameReceiver.clearImage();
                firstFrame.recycle();
            } else {
                if (imageReceiver.getLottieAnimation() != null) {
                    imageReceiver.getLottieAnimation().setCurrentFrame(0, false, true);
                }
                imageReceiver.setImageCoords(imageX, imageY, imageSize, imageSize);
                imageReceiver.setRoundRadius((int) (imageSize * AvatarConstructorFragment.STICKER_DEFAULT_ROUND_RADIUS));
                imageReceiver.draw(canvas);
            }

            File thumb = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), SharedConfig.getLastLocalId() + "avatar_background.png");
            try {
                thumb.createNewFile();

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 0, bos);
                byte[] bitmapdata = bos.toByteArray();

                FileOutputStream fos = new FileOutputStream(thumb);
                fos.write(bitmapdata);
                fos.flush();
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            MediaController.PhotoEntry photoEntry;
            if (previewView.hasAnimation()) {
                photoEntry = new MediaController.PhotoEntry(0, 0, 0, file.getPath(), 0, false, 0, 0, 0);
                photoEntry.thumbPath = thumb.getPath();

                if (previewView.documentId != 0) {
                    TLRPC.TL_videoSizeEmojiMarkup emojiMarkup = new TLRPC.TL_videoSizeEmojiMarkup();
                    emojiMarkup.emoji_id = previewView.documentId;
                    emojiMarkup.background_colors.add(previewView.backgroundGradient.color1);
                    if (previewView.backgroundGradient.color2 != 0) {
                        emojiMarkup.background_colors.add(previewView.backgroundGradient.color2);
                    }
                    if (previewView.backgroundGradient.color3 != 0) {
                        emojiMarkup.background_colors.add(previewView.backgroundGradient.color3);
                    }
                    if (previewView.backgroundGradient.color4 != 0) {
                        emojiMarkup.background_colors.add(previewView.backgroundGradient.color4);
                    }
                    photoEntry.emojiMarkup = emojiMarkup;
                } else if (previewView.document != null) {
                    TLRPC.TL_videoSizeStickerMarkup emojiMarkup = new TLRPC.TL_videoSizeStickerMarkup();
                    emojiMarkup.sticker_id = previewView.document.id;
                    emojiMarkup.stickerset = MessageObject.getInputStickerSet(previewView.document);
                    emojiMarkup.background_colors.add(previewView.backgroundGradient.color1);
                    if (previewView.backgroundGradient.color2 != 0) {
                        emojiMarkup.background_colors.add(previewView.backgroundGradient.color2);
                    }
                    if (previewView.backgroundGradient.color3 != 0) {
                        emojiMarkup.background_colors.add(previewView.backgroundGradient.color3);
                    }
                    if (previewView.backgroundGradient.color4 != 0) {
                        emojiMarkup.background_colors.add(previewView.backgroundGradient.color4);
                    }
                    photoEntry.emojiMarkup = emojiMarkup;
                }

                photoEntry.editedInfo = new VideoEditedInfo();
                photoEntry.editedInfo.originalPath = file.getPath();
                photoEntry.editedInfo.resultWidth = 800;
                photoEntry.editedInfo.resultHeight = 800;
                photoEntry.editedInfo.originalWidth = 800;
                photoEntry.editedInfo.originalHeight = 800;
                photoEntry.editedInfo.isPhoto = true;
                photoEntry.editedInfo.bitrate = -1;
                photoEntry.editedInfo.muted = true;

                photoEntry.editedInfo.start = photoEntry.editedInfo.startTime = 0;
                photoEntry.editedInfo.endTime = previewView.getDuration();
                photoEntry.editedInfo.framerate = 30;

                photoEntry.editedInfo.avatarStartTime = 0;
                photoEntry.editedInfo.estimatedSize = (int) (photoEntry.editedInfo.endTime / 1000.0f * 115200);
                photoEntry.editedInfo.estimatedDuration = photoEntry.editedInfo.endTime;

                VideoEditedInfo.MediaEntity mediaEntity = new VideoEditedInfo.MediaEntity();
                mediaEntity.type = 0;

                if (document == null) {
                    document = AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, documentId);
                }
                if (document == null) {
                    return;
                }
                mediaEntity.viewWidth = (int) (800 * scale);
                mediaEntity.viewHeight = (int) (800 * scale);
                mediaEntity.width = scale;
                mediaEntity.height = scale;
                mediaEntity.x = (1f - scale) / 2f;
                mediaEntity.y = (1f - scale) / 2f;
                mediaEntity.document = document;
                mediaEntity.parentObject = null;
                mediaEntity.text = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true).getAbsolutePath();
                mediaEntity.roundRadius = AvatarConstructorFragment.STICKER_DEFAULT_ROUND_RADIUS;
                if (MessageObject.isAnimatedStickerDocument(document, true) || MessageObject.isVideoStickerDocument(document)) {
                    boolean isAnimatedSticker = MessageObject.isAnimatedStickerDocument(document, true);
                    mediaEntity.subType |= isAnimatedSticker ? 1 : 4;
                }
                if (MessageObject.isTextColorEmoji(document)) {
                    mediaEntity.color = 0xFFFFFFFF;
                    mediaEntity.subType |= 8;
                }

                photoEntry.editedInfo.mediaEntities = new ArrayList<>();
                photoEntry.editedInfo.mediaEntities.add(mediaEntity);
            } else {
                photoEntry = new MediaController.PhotoEntry(0, 0, 0, thumb.getPath(), 0, false, 0, 0, 0);
            }
            selectedPhotos.put(-1, photoEntry);
            selectedPhotosOrder.add(-1);
            parentAlert.delegate.didPressedButton(7, true, false, 0, 0, parentAlert.isCaptionAbove(), false);
            if (!avatarConstructorFragment.finishOnDone) {
                if (parentAlert.baseFragment != null) {
                    parentAlert.baseFragment.removeSelfFromStack();
                }
                avatarConstructorFragment.finishFragment();
            }
        });
    }

    private void closeCollage() {
        if (collageLayoutView != null) {
            collageLayoutView.setLayout(null, true);
            collageLayoutView.clear(true);
            collageListView.setSelected(null);
            if (cameraView != null) {
                cameraView.recordHevc = !collageLayoutView.hasLayout();
            }
            collageListView.setVisible(false, true);
            updateActionBarButtons(true);
        }
    }

    private boolean checkSendMediaEnabled(MediaController.PhotoEntry photoEntry) {
        if (!videoEnabled && photoEntry.isVideo) {
            if (parentAlert.checkCanRemoveRestrictionsByBoosts()) {
                return true;
            }
            BulletinFactory.of(parentAlert.sizeNotifierFrameLayout, resourcesProvider).createErrorBulletin(
                    LocaleController.getString(R.string.GlobalAttachVideoRestricted)
            ).show();
            return true;
        } else if (!photoEnabled && !photoEntry.isVideo) {
            if (parentAlert.checkCanRemoveRestrictionsByBoosts()) {
                return true;
            }
            BulletinFactory.of(parentAlert.sizeNotifierFrameLayout, resourcesProvider).createErrorBulletin(
                    LocaleController.getString(R.string.GlobalAttachPhotoRestricted)
            ).show();
            return true;
        }
        return false;
    }

    private int maxCount() {
        if (parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).getChatMode() == ChatActivity.MODE_QUICK_REPLIES) {
            return parentAlert.baseFragment.getMessagesController().quickReplyMessagesLimit - ((ChatActivity) parentAlert.baseFragment).messages.size();
        }
        return Integer.MAX_VALUE;
    }

    public int addToSelectedPhotos(MediaController.PhotoEntry object, int index) {
        Object key = object.imageId;
        if (selectedPhotos.containsKey(key)) {
            object.starsAmount = 0;
            object.hasSpoiler = false;

            selectedPhotos.remove(key);
            int position = selectedPhotosOrder.indexOf(key);
            if (position >= 0) {
                selectedPhotosOrder.remove(position);
            }
            updatePhotosCounter(false);
            updateCheckedPhotoIndices();
            if (index >= 0) {
                object.reset();
                photoViewerProvider.updatePhotoAtIndex(index);
            }
            return position;
        } else {
            object.starsAmount = getStarsPrice();
            object.hasSpoiler = getStarsPrice() > 0;
            object.isChatPreviewSpoilerRevealed = false;
            object.isAttachSpoilerRevealed = false;

            boolean changed = checkSelectedCount(true);
            selectedPhotos.put(key, object);
            selectedPhotosOrder.add(key);
            if (changed) {
                updateCheckedPhotos();
            } else {
                updatePhotosCounter(true);
            }
            return -1;
        }
    }

    private boolean checkSelectedCount(boolean beforeAdding) {
        boolean changed = false;
        if (getStarsPrice() > 0) {
            while (selectedPhotos.size() > 10 - (beforeAdding ? 1 : 0) && !selectedPhotosOrder.isEmpty()) {
                Object key = selectedPhotosOrder.get(0);
                Object firstPhoto = selectedPhotos.get(key);
                if (!(firstPhoto instanceof MediaController.PhotoEntry)) {
                    break;
                }
                addToSelectedPhotos((MediaController.PhotoEntry) firstPhoto, -1);
                changed = true;
            }
        }
        return changed;
    }

    public long getStarsPrice() {
        for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry.getValue();
            return photoEntry.starsAmount;
        }
        return 0;
    }

    public void setStarsPrice(long stars) {
        if (!selectedPhotos.isEmpty()) {
            for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry.getValue();
                photoEntry.starsAmount = stars;
                photoEntry.hasSpoiler = stars > 0;
                photoEntry.isChatPreviewSpoilerRevealed = false;
                photoEntry.isAttachSpoilerRevealed = false;
            }
        }
        onSelectedItemsCountChanged(getSelectedItemsCount());
        if (checkSelectedCount(false)) {
            updateCheckedPhotos();
        }
    }

    private void updatePhotoStarsPrice() {
        gridView.forAllChild(view -> {
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                cell.setHasSpoiler(cell.getPhotoEntry() != null && cell.getPhotoEntry().hasSpoiler, 250f);
                cell.setStarsPrice(cell.getPhotoEntry() != null ? cell.getPhotoEntry().starsAmount : 0, selectedPhotos.size() > 1);
            }
        });
    }

    public void clearSelectedPhotos() {
        spoilerItem.setText(LocaleController.getString(R.string.EnablePhotoSpoiler));
        spoilerItem.setAnimatedIcon(R.raw.photo_spoiler);
        parentAlert.selectedMenuItem.showSubItem(compress);
        if (!selectedPhotos.isEmpty()) {
            for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry.getValue();
                photoEntry.reset();
            }
            selectedPhotos.clear();
            selectedPhotosOrder.clear();
        }
        if (!cameraPhotos.isEmpty()) {
            for (int a = 0, size = cameraPhotos.size(); a < size; a++) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) cameraPhotos.get(a);
                new File(photoEntry.path).delete();
                if (photoEntry.imagePath != null) {
                    new File(photoEntry.imagePath).delete();
                }
                if (photoEntry.thumbPath != null) {
                    new File(photoEntry.thumbPath).delete();
                }
            }
            cameraPhotos.clear();
        }
        adapter.notifyDataSetChanged();
        cameraAttachAdapter.notifyDataSetChanged();
    }

    private void updateAlbumsDropDown() {
        dropDownContainer.removeAllSubItems();
        if (mediaEnabled) {
            ArrayList<MediaController.AlbumEntry> albums;
            if (shouldLoadAllMedia()) {
                albums = MediaController.allMediaAlbums;
            } else {
                albums = MediaController.allPhotoAlbums;
            }
            dropDownAlbums = new ArrayList<>(albums);
            Collections.sort(dropDownAlbums, (o1, o2) -> {
                if (o1.bucketId == 0 && o2.bucketId != 0) {
                    return -1;
                } else if (o1.bucketId != 0 && o2.bucketId == 0) {
                    return 1;
                }
                int index1 = albums.indexOf(o1);
                int index2 = albums.indexOf(o2);
                if (index1 > index2) {
                    return 1;
                } else if (index1 < index2) {
                    return -1;
                } else {
                    return 0;
                }

            });
        } else {
            dropDownAlbums = new ArrayList<>();
        }
        if (dropDownAlbums.isEmpty()) {
            dropDown.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        } else {
            dropDown.setCompoundDrawablesWithIntrinsicBounds(null, null, dropDownDrawable, null);
            for (int a = 0, N = dropDownAlbums.size(); a < N; a++) {
                MediaController.AlbumEntry album = dropDownAlbums.get(a);
                AlbumButton btn = new AlbumButton(getContext(), album.coverPhoto, album.bucketName, album.photos.size(), resourcesProvider);
                dropDownContainer.getPopupLayout().addView(btn);
                final int i = a + 10;
                btn.setOnClickListener(v -> {
                    parentAlert.actionBar.getActionBarMenuOnItemClick().onItemClick(i);
                    dropDownContainer.toggleSubMenu();
                });
            }
        }
    }

    private boolean processTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        if (!pressed && event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            zoomControlView.getHitRect(hitRect);
            if (zoomControlView.getTag() != null && hitRect.contains((int) event.getX(), (int) event.getY())) {
                return false;
            }
            if (!takingPhoto && !dragging) {
                if (event.getPointerCount() == 2) {
                    pinchStartDistance = (float) Math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0));
                    zooming = true;
                } else {
                    maybeStartDraging = true;
                    lastY = event.getY();
                    zooming = false;
                }
                zoomWas = false;
                pressed = true;
            }
        } else if (pressed) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (zooming && event.getPointerCount() == 2 && !dragging) {
                    float newDistance = (float) Math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0));
                    if (!zoomWas) {
                        if (Math.abs(newDistance - pinchStartDistance) >= AndroidUtilities.getPixelsInCM(0.4f, false)) {
                            pinchStartDistance = newDistance;
                            zoomWas = true;
                        }
                    } else {
                        if (cameraView != null) {
                            float diff = (newDistance - pinchStartDistance) / dp(100);
                            pinchStartDistance = newDistance;
                            cameraZoom += diff;
                            if (cameraZoom < 0.0f) {
                                cameraZoom = 0.0f;
                            } else if (cameraZoom > 1.0f) {
                                cameraZoom = 1.0f;
                            }
                            zoomControlView.setZoom(cameraZoom, false);
                            parentAlert.getSheetContainer().invalidate();
                            cameraView.setZoom(cameraZoom);
                            showZoomControls(true, true);
                        }
                    }
                } else {
                    float newY = event.getY();
                    float dy = (newY - lastY);
                    if (maybeStartDraging) {
                        if (Math.abs(dy) > AndroidUtilities.getPixelsInCM(0.4f, false)) {
                            maybeStartDraging = false;
                            dragging = true;
                        }
                    } else if (dragging) {
                        if (collageLayoutView != null) {
                            collageLayoutView.setTranslationY(collageLayoutView.getTranslationY() + dy);
                            lastY = newY;
                            zoomControlView.setTag(null);
                            if (zoomControlHideRunnable != null) {
                                AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable);
                                zoomControlHideRunnable = null;
                            }
                            if (cameraControlContainer.getTag() == null) {
                                cameraControlContainer.setTag(1);
                                AnimatorSet animatorSet = new AnimatorSet();
                                animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(cameraControlContainer, View.ALPHA, 0.0f),
                                        ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, 0.0f),
                                        ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 0.0f),
                                        ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 0.0f));
                                animatorSet.setDuration(220);
                                animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
                                animatorSet.start();
                            }
                        }
                    }
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                pressed = false;
                zooming = false;
                if (zooming) {
                    zooming = false;
                } else if (dragging) {
                    dragging = false;
                    if (collageLayoutView != null) {
                        if (Math.abs(collageLayoutView.getTranslationY()) > collageLayoutView.getMeasuredHeight() / 6.0f) {
                            closeCamera(true);
                        } else {
                            AnimatorSet animatorSet = new AnimatorSet();
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(collageLayoutView, View.TRANSLATION_Y, 0.0f),
                                    ObjectAnimator.ofFloat(cameraControlContainer, View.ALPHA, 1.0f),
                                    ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 1.0f),
                                    ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 1.0f));
                            animatorSet.setDuration(250);
                            animatorSet.setInterpolator(interpolator);
                            animatorSet.start();
                            cameraControlContainer.setTag(null);
                        }
                    }
                } else if (cameraView != null && !zoomWas) {
                    cameraView.getLocationOnScreen(viewPosition);
                    float viewX = event.getRawX() - viewPosition[0];
                    float viewY = event.getRawY() - viewPosition[1];
                    cameraView.focusToPoint((int) viewX, (int) viewY);
                }
            }
        }
        return true;
    }

    private void resetRecordState() {
        if (parentAlert.destroyed) {
            return;
        }
        tooltipTextView.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

        AndroidUtilities.unlockOrientation(AndroidUtilities.findActivity(getContext()));
    }

    public void openPhotoViewer(MediaController.PhotoEntry entry, final boolean sameTakePictureOrientation, boolean external) {
        if (entry != null) {
            cameraPhotos.add(entry);
            selectedPhotos.put(entry.imageId, entry);
            selectedPhotosOrder.add(entry.imageId);
            parentAlert.updateCountButton(0);
            adapter.notifyDataSetChanged();
            cameraAttachAdapter.notifyDataSetChanged();
        }
        if (entry != null && !external && cameraPhotos.size() > 1) {
            updatePhotosCounter(false);
            if (cameraView != null) {
                zoomControlView.setZoom(0.0f, false);
                cameraZoom = 0.0f;
                cameraView.setZoom(0.0f);
                CameraController.getInstance().startPreview(cameraView.getCameraSessionObject());
            }
            return;
        }
        if (cameraPhotos.isEmpty() && selectedPhotos.isEmpty()) {
            return;
        }
        cancelTakingPhotos = true;

        BaseFragment fragment = parentAlert.baseFragment;
        if (fragment == null) {
            fragment = LaunchActivity.getLastFragment();
        }
        if (fragment == null) {
            return;
        }
        PhotoViewer.getInstance().setParentActivity(fragment.getParentActivity(), resourcesProvider);
        PhotoViewer.getInstance().setParentAlert(parentAlert);
        PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder);

        ChatActivity chatActivity;
        int type;
        if (parentAlert.isPhotoPicker && parentAlert.isStickerMode) {
            type = PhotoViewer.SELECT_TYPE_STICKER;
            chatActivity = (ChatActivity) parentAlert.baseFragment;
        } else if (parentAlert.avatarPicker != 0) {
            type = PhotoViewer.SELECT_TYPE_AVATAR;
            chatActivity = null;
        } else if (parentAlert.baseFragment instanceof ChatActivity) {
            chatActivity = (ChatActivity) parentAlert.baseFragment;
            type = 2;
        } else {
            chatActivity = null;
            type = 5;
        }
        ArrayList<Object> arrayList;
        int index;
        if (parentAlert.avatarPicker != 0) {
            arrayList = new ArrayList<>();
            arrayList.add(entry);
            index = 0;
        } else if (cameraPhotos.isEmpty()) {
            arrayList = new ArrayList<>(selectedPhotos.values());
            index = 0;
        } else {
            arrayList = getAllPhotosArray();
            index = cameraPhotos.size() - 1;
        }
        if (parentAlert.getAvatarFor() != null && entry != null) {
            parentAlert.getAvatarFor().isVideo = entry.isVideo;
        }
        openPhotoViewerInternal(sameTakePictureOrientation, arrayList, index, type, chatActivity);
    }

    private void openPhotoViewerInternal(boolean sameTakePictureOrientation, ArrayList<Object> arrayList, int index, int type, ChatActivity chatActivity) {
        PhotoViewer.getInstance().openPhotoForSelect(arrayList, index, type, false, new BasePhotoProvider() {

            @Override
            public void onOpen() {
                pauseCameraPreview();
            }

            @Override
            public void onClose() {
                resumeCameraPreview();
            }

            public void onEditModeChanged(boolean isEditMode) {
                onPhotoEditModeChanged(isEditMode);
            }

            @Override
            public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
                return null;
            }

            @Override
            public boolean cancelButtonPressed() {
                if (cameraOpened && collageLayoutView != null && cameraView != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (collageLayoutView != null && !parentAlert.isDismissed() && Build.VERSION.SDK_INT >= 21) {
                            collageLayoutView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
                        }
                    }, 1000);
                    zoomControlView.setZoom(0.0f, false);
                    cameraZoom = 0.0f;
                    cameraView.setZoom(0.0f);
                    CameraController.getInstance().startPreview(cameraView.getCameraSession());
                }
                if (cancelTakingPhotos && cameraPhotos.size() == 1) {
                    for (int a = 0, size = cameraPhotos.size(); a < size; a++) {
                        MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) cameraPhotos.get(a);
                        new File(photoEntry.path).delete();
                        if (photoEntry.imagePath != null) {
                            new File(photoEntry.imagePath).delete();
                        }
                        if (photoEntry.thumbPath != null) {
                            new File(photoEntry.thumbPath).delete();
                        }
                    }
                    cameraPhotos.clear();
                    selectedPhotosOrder.clear();
                    selectedPhotos.clear();
                    counterTextView.setVisibility(View.INVISIBLE);
                    cameraPhotoRecyclerView.setVisibility(View.GONE);
                    adapter.notifyDataSetChanged();
                    cameraAttachAdapter.notifyDataSetChanged();
                    parentAlert.updateCountButton(0);
                }
                return true;
            }

            @Override
            public void needAddMorePhotos() {
                cancelTakingPhotos = false;
                if (mediaFromExternalCamera) {
                    parentAlert.delegate.didPressedButton(0, true, true, 0, 0, parentAlert.isCaptionAbove(), false);
                    return;
                }
                if (!cameraOpened) {
                    openCamera(false);
                }
                if (collageLayoutView.hasLayout()) {
                    collageLayoutView.clear(false);
                    recordControl.setCollageProgress(0.0f, false);
                }
                setCameraFlashModeIcon(getCurrentFlashMode(), false);
                counterTextView.setVisibility(View.VISIBLE);
                cameraPhotoRecyclerView.setVisibility(View.VISIBLE);
                counterTextView.setAlpha(1.0f);
                updatePhotosCounter(false);
            }

            @Override
            public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument) {
                parentAlert.sent = true;
                if (cameraPhotos.isEmpty() || parentAlert.destroyed) {
                    return;
                }
                if (videoEditedInfo != null && index >= 0 && index < cameraPhotos.size()) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) cameraPhotos.get(index);
                    photoEntry.editedInfo = videoEditedInfo;
                }
                if (!(parentAlert.baseFragment instanceof ChatActivity) || !((ChatActivity) parentAlert.baseFragment).isSecretChat()) {
                    for (int a = 0, size = cameraPhotos.size(); a < size; a++) {
                        MediaController.PhotoEntry entry = (MediaController.PhotoEntry) cameraPhotos.get(a);
                        if (entry.ttl > 0) {
                            continue;
                        }
                        AndroidUtilities.addMediaToGallery(entry.path);
                    }
                }
                parentAlert.applyCaption();
                closeCamera(false);
                parentAlert.delegate.didPressedButton(forceDocument ? 4 : 8, true, notify, scheduleDate, 0, parentAlert.isCaptionAbove(), forceDocument);
                cameraPhotos.clear();
                selectedPhotosOrder.clear();
                selectedPhotos.clear();
                adapter.notifyDataSetChanged();
                cameraAttachAdapter.notifyDataSetChanged();
                parentAlert.dismiss(true);
            }

            @Override
            public boolean scaleToFill() {
                if (parentAlert.destroyed) {
                    return false;
                }
                int locked = Settings.System.getInt(getContext().getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                return sameTakePictureOrientation || locked == 1;
            }

            @Override
            public void willHidePhotoViewer() {
                int count = gridView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = gridView.getChildAt(a);
                    if (view instanceof PhotoAttachPhotoCell) {
                        PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                        cell.showImage();
                        cell.showCheck(true);
                    }
                }
            }

            @Override
            public boolean canScrollAway() {
                return false;
            }

            @Override
            public boolean canCaptureMorePhotos() {
                return parentAlert.maxSelectedPhotos != 1;
            }

            @Override
            public boolean allowCaption() {
                return !parentAlert.isPhotoPicker;
            }
        }, chatActivity);
        PhotoViewer.getInstance().setAvatarFor(parentAlert.getAvatarFor());
        if (parentAlert.isStickerMode) {
            PhotoViewer.getInstance().enableStickerMode(null, false, parentAlert.customStickerHandler);
            PhotoViewer.getInstance().prepareSegmentImage();
        }
    }

    private void showZoomControls(boolean show, boolean animated) {
        if (zoomControlView.getTag() != null && show || zoomControlView.getTag() == null && !show) {
            if (show) {
                if (zoomControlHideRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable);
                }
                AndroidUtilities.runOnUIThread(zoomControlHideRunnable = () -> {
                    showZoomControls(false, true);
                    zoomControlHideRunnable = null;
                }, 2000);
            }
            return;
        }
        if (zoomControlAnimation != null) {
            zoomControlAnimation.cancel();
        }
        zoomControlView.setTag(show ? 1 : null);
        zoomControlAnimation = new AnimatorSet();
        zoomControlAnimation.setDuration(180);
        zoomControlAnimation.playTogether(ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, show ? 1.0f : 0.0f));
        zoomControlAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                zoomControlAnimation = null;
            }
        });
        zoomControlAnimation.start();
        if (show) {
            AndroidUtilities.runOnUIThread(zoomControlHideRunnable = () -> {
                showZoomControls(false, true);
                zoomControlHideRunnable = null;
            }, 2000);
        }
    }

    protected void updatePhotosCounter(boolean added) {
        if (counterTextView == null || parentAlert.avatarPicker != 0 || parentAlert.storyMediaPicker) {
            return;
        }
        boolean hasVideo = false;
        boolean hasPhotos = false;
        for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry.getValue();
            if (photoEntry.isVideo) {
                hasVideo = true;
            } else {
                hasPhotos = true;
            }
            if (hasVideo && hasPhotos) {
                break;
            }
        }
        int newSelectedCount = Math.max(1, selectedPhotos.size());
        if (hasVideo && hasPhotos) {
            counterTextView.setText(LocaleController.formatPluralString("Media", selectedPhotos.size()).toUpperCase());
            if (newSelectedCount != currentSelectedCount || added) {
                parentAlert.selectedTextView.setText(LocaleController.formatPluralString("MediaSelected", newSelectedCount));
            }
        } else if (hasVideo) {
            counterTextView.setText(LocaleController.formatPluralString("Videos", selectedPhotos.size()).toUpperCase());
            if (newSelectedCount != currentSelectedCount || added) {
                parentAlert.selectedTextView.setText(LocaleController.formatPluralString("VideosSelected", newSelectedCount));
            }
        } else {
            counterTextView.setText(LocaleController.formatPluralString("Photos", selectedPhotos.size()).toUpperCase());
            if (newSelectedCount != currentSelectedCount || added) {
                parentAlert.selectedTextView.setText(LocaleController.formatPluralString("PhotosSelected", newSelectedCount));
            }
        }
        if (!selectedPhotos.isEmpty() && cameraExpanded) {
            counterTextView.setVisibility(View.VISIBLE);
        } else {
            counterTextView.setVisibility(View.INVISIBLE);
        }
        parentAlert.setCanOpenPreview(newSelectedCount > 1);
        currentSelectedCount = newSelectedCount;
    }

    private PhotoAttachPhotoCell getCellForIndex(int index) {
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = gridView.getChildAt(a);
            if (view.getTop() >= gridView.getMeasuredHeight() - parentAlert.getClipLayoutBottom()) {
                continue;
            }
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                if (cell.getImageView().getTag() != null && (Integer) cell.getImageView().getTag() == index) {
                    return cell;
                }
            }
        }
        return null;
    }

    private void setCameraFlashModeIcon(ImageView imageView, String mode) {
        switch (mode) {
            case Camera.Parameters.FLASH_MODE_OFF:
                imageView.setImageResource(R.drawable.flash_off);
                imageView.setContentDescription(LocaleController.getString(R.string.AccDescrCameraFlashOff));
                break;
            case Camera.Parameters.FLASH_MODE_ON:
                imageView.setImageResource(R.drawable.flash_on);
                imageView.setContentDescription(LocaleController.getString(R.string.AccDescrCameraFlashOn));
                break;
            case Camera.Parameters.FLASH_MODE_AUTO:
                imageView.setImageResource(R.drawable.flash_auto);
                imageView.setContentDescription(LocaleController.getString(R.string.AccDescrCameraFlashAuto));
                break;
        }
    }

    private String flashButtonMode;

    private void setCameraFlashModeIcon(String mode, boolean animated) {
        flashButton.clearAnimation();
        if (cameraView != null && cameraView.isDual() || animatedRecording) {
            mode = null;
        }
        flashButtonMode = mode;
        if (mode == null) {
            setActionBarButtonVisible(flashButton, false, animated);
            return;
        }
        final int resId;
        switch (mode) {
            case Camera.Parameters.FLASH_MODE_ON:
                resId = R.drawable.media_photo_flash_on2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOn));
                break;
            case Camera.Parameters.FLASH_MODE_AUTO:
                resId = R.drawable.media_photo_flash_auto2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashAuto));
                break;
            default:
            case Camera.Parameters.FLASH_MODE_OFF:
                resId = R.drawable.media_photo_flash_off2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOff));
                break;
        }
        flashButton.setIcon(flashButtonResId = resId, animated && flashButtonResId != resId);
        setActionBarButtonVisible(flashButton, cameraOpened && !collageListView.isVisible() && flashButtonMode != null && !inCheck(), animated);
    }

    public void checkCamera(boolean request) {
        if (parentAlert.destroyed || !needCamera) {
            return;
        }
        boolean old = deviceHasGoodCamera;
        boolean old2 = noCameraPermissions;
        BaseFragment fragment = parentAlert.baseFragment;
        if (fragment == null) {
            fragment = LaunchActivity.getLastFragment();
        }
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        if (!SharedConfig.inappCamera) {
            deviceHasGoodCamera = false;
        } else {
            if (Build.VERSION.SDK_INT >= 23) {
                if (noCameraPermissions = (fragment.getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
                    if (request) {
                        try {
                            parentAlert.baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE}, 17);
                        } catch (Exception ignore) {

                        }
                    }
                    deviceHasGoodCamera = false;
                } else {
                    if (request || SharedConfig.hasCameraCache) {
                        CameraController.getInstance().initCamera(null);
                    }
                    deviceHasGoodCamera = CameraController.getInstance().isCameraInitied();
                }
            } else {
                if (request || SharedConfig.hasCameraCache) {
                    CameraController.getInstance().initCamera(null);
                }
                deviceHasGoodCamera = CameraController.getInstance().isCameraInitied();
            }
        }
        if ((old != deviceHasGoodCamera || old2 != noCameraPermissions) && adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (!parentAlert.destroyed && parentAlert.isShowing() && deviceHasGoodCamera && parentAlert.getBackDrawable().getAlpha() != 0 && !cameraOpened) {
            showCamera();
        }
    }

    boolean cameraExpanded;

    private void openCamera(boolean animated) {
//        CameraRecorder.SourceView.Delegate delegate = new CameraRecorder.SourceView.Delegate() {
//            @Override
//            public void show() {
////                onShown();
////                closeCamera(false);
////                adapter.notifyDataSetChanged();
////                cameraAttachAdapter.notifyDataSetChanged();
////                resumeCameraPreview();
//            }
//
//            @Override
//            public void hide() {
////                cameraOpened = false;
////                onHide();
//            }
//        };
//        CameraRecorder.getInstance(this).open(CameraRecorder.SourceView.fromCameraAlertCell(cameraCell, delegate), animated);
//
//        if (parentAlert.commentTextView.isKeyboardVisible() && isFocusable()) {
//            parentAlert.commentTextView.closeKeyboard();
//        }
//        parentAlert.delegate.onCameraOpened();
//        AndroidUtilities.hideKeyboard(this);

        // TODO openCamera
        if (cameraView == null || cameraInitAnimation != null || parentAlert.isDismissed()) {
            return;
        }
        cameraView.isHandleTouch = true;
        cameraView.initTexture();
        updateVideoViews();
        if (recordControl != null) {
            recordControl.stopRecordingLoading(false);
        }
        videoTimerView.setDuration(0, true);
        animateRecording(false, false);

        if (shouldLoadAllMedia()) {
            tooltipTextView.setVisibility(VISIBLE);
        } else {
            tooltipTextView.setVisibility(GONE);
        }
        if (cameraPhotos.isEmpty()) {
            counterTextView.setVisibility(View.INVISIBLE);
            cameraPhotoRecyclerView.setVisibility(View.GONE);
        } else {
            counterTextView.setVisibility(View.VISIBLE);
            cameraPhotoRecyclerView.setVisibility(View.VISIBLE);
        }
        if (parentAlert.getCommentView().isKeyboardVisible() && isFocusable()) {
            parentAlert.getCommentView().closeKeyboard();
        }
        if (collageLayoutView != null) {
            collageLayoutView.clear(true);
            recordControl.setCollageProgress(0.0f, false);
        }
        zoomControlView.setVisibility(View.VISIBLE);
        zoomControlView.setAlpha(0.0f);
        cameraControlContainer.setVisibility(View.VISIBLE);
        cameraControlContainer.setTag(null);
        animateCameraValues[0] = 0;
        animateCameraValues[1] = itemSize;
        animateCameraValues[2] = itemSize;
        additionCloseCameraY = 0;
        cameraExpanded = true;
        if (cameraView != null) {
            cameraView.setFpsLimit(-1);
        }
        setCameraFlashModeIcon(getCurrentFlashMode(), true);
        AndroidUtilities.hideKeyboard(this);
        AndroidUtilities.setLightNavigationBar(parentAlert.getWindow(), false);
        parentAlert.getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
        if (animated) {
            setCameraOpenProgress(0);
            cameraAnimationInProgress = true;
            notificationsLocker.lock();
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(this, "cameraOpenProgress", 0.0f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(cameraControlContainer, View.ALPHA, 1.0f));
            animators.add(ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 1.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 1.0f));
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.setDuration(350);
            animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    notificationsLocker.unlock();
                    cameraAnimationInProgress = false;
                    if (collageLayoutView != null) {
                        if (Build.VERSION.SDK_INT >= 21) {
                            collageLayoutView.invalidateOutline();
                        } else {
                            collageLayoutView.invalidate();
                        }
                    }
                    if (cameraOpened) {
                        parentAlert.delegate.onCameraOpened();
                    }
                    if (Build.VERSION.SDK_INT >= 21 && collageLayoutView != null) {
                        collageLayoutView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
                    }
                }
            });
            animatorSet.start();
        } else {
            setCameraOpenProgress(1.0f);
            cameraControlContainer.setAlpha(1.0f);
            counterTextView.setAlpha(1.0f);
            cameraPhotoRecyclerView.setAlpha(1.0f);
            parentAlert.delegate.onCameraOpened();
            if (collageLayoutView != null && Build.VERSION.SDK_INT >= 21) {
                collageLayoutView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
            }
        }
        cameraOpened = true;
        if (collageLayoutView != null) {
            collageLayoutView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (Build.VERSION.SDK_INT >= 19) {
            gridView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }

        if (!LiteMode.isEnabled(LiteMode.FLAGS_CHAT) && cameraView != null && cameraView.isInited()) {
            cameraView.showTexture(true, animated);
        }

        updateActionBarButtons(false);
        updateActionBarButtonsOffsets();
    }

    public void loadGalleryPhotos() {
        MediaController.AlbumEntry albumEntry;
        if (shouldLoadAllMedia()) {
            albumEntry = MediaController.allMediaAlbumEntry;
        } else {
            albumEntry = MediaController.allPhotosAlbumEntry;
        }
        if (albumEntry == null && Build.VERSION.SDK_INT >= 21) {
            MediaController.loadGalleryPhotosAlbums(0);
        }
    }

    private boolean shouldLoadAllMedia() {
        return !parentAlert.isPhotoPicker && (parentAlert.baseFragment instanceof ChatActivity || parentAlert.storyMediaPicker || parentAlert.avatarPicker == 2);
    }

    public void showCamera() {
        if (parentAlert.paused || !mediaEnabled) {
            return;
        }
        if (collageLayoutView == null) {
            collageLayoutView = new CollageLayoutView2(getContext(), blurManager, parentAlert.getContainer(), new DarkThemeResourceProvider()) {
                @Override
                protected void onLayoutUpdate(CollageLayout layout) {
                    collageListView.setVisible(false, true);
                    if (layout != null && layout.parts.size() > 1) {
                        collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout = layout), true);
                        collageButton.setSelected(true, true);
                    } else {
                        collageButton.setSelected(false, true);
                    }
                    updateActionBarButtons(true);
                }
            };
            if (Build.VERSION.SDK_INT >= 21) {
                collageLayoutView.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        int maxY = (int) Math.min(parentAlert.getCommentTextViewTop() - (parentAlert.mentionContainer != null ? parentAlert.mentionContainer.clipBottom() + dp(8) : 0) + currentPanTranslationY + parentAlert.getContainerView().getTranslationY() - collageLayoutView.getTranslationY(), view.getMeasuredHeight());
                        if (cameraOpened) {
                            maxY = view.getMeasuredHeight();
                        } else if (cameraAnimationInProgress) {
                            maxY = AndroidUtilities.lerp(maxY, view.getMeasuredHeight(), cameraOpenProgress);
                        }
                        if (cameraAnimationInProgress) {
                            AndroidUtilities.rectTmp.set(animationClipLeft + cameraViewOffsetX * (1f - cameraOpenProgress), animationClipTop + cameraViewOffsetY * (1f - cameraOpenProgress), animationClipRight, animationClipBottom);
                            outline.setRect((int) AndroidUtilities.rectTmp.left, (int) AndroidUtilities.rectTmp.top, (int) AndroidUtilities.rectTmp.right, Math.min(maxY, (int) AndroidUtilities.rectTmp.bottom));
                        } else if (!cameraAnimationInProgress && !cameraOpened) {
                            int rad = dp(8 * parentAlert.cornerRadius);
                            outline.setRoundRect((int) cameraViewOffsetX, (int) cameraViewOffsetY, view.getMeasuredWidth() + rad, Math.min(maxY, view.getMeasuredHeight()) + rad, rad);
                        } else {
                            outline.setRect(0, 0, view.getMeasuredWidth(), Math.min(maxY, view.getMeasuredHeight()));
                        }
                    }
                });
                collageLayoutView.setClipToOutline(true);
            }
            collageLayoutView.setResetState(() -> {
                updateActionBarButtons(true);
            });
            collageLayoutView.setCancelGestures(() -> {
                if (cameraTouchView != null) {
                    cameraTouchView.cancelGestures();
                }
            });
            // TODO container
            parentAlert.getContainer().addView(collageLayoutView, 1, new FrameLayout.LayoutParams(itemSize, itemSize));
            if (recordControl != null) {
                recordControl.setCollageProgress(collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f, true);
            }
        }
        if (cameraView == null) {
            final boolean lazy = !LiteMode.isEnabled(LiteMode.FLAGS_CHAT);
            cameraView = new DualCameraView(getContext(), isCameraFrontfaceBeforeEnteringEditMode != null ? isCameraFrontfaceBeforeEnteringEditMode : parentAlert.openWithFrontFaceCamera, lazy) {

                Bulletin.Delegate bulletinDelegate = new Bulletin.Delegate() {
                    @Override
                    public int getBottomOffset(int tag) {
                        return dp(126) + parentAlert.getBottomInset();
                    }
                };

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    if (AndroidUtilities.makingGlobalBlurBitmap) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 21) {
                        super.dispatchDraw(canvas);
                    } else {
                        int maxY = (int) Math.min(parentAlert.getCommentTextViewTop() + currentPanTranslationY + parentAlert.getContainerView().getTranslationY() - collageLayoutView.getTranslationY() - (parentAlert.mentionContainer != null ? parentAlert.mentionContainer.clipBottom() + dp(8) : 0), getMeasuredHeight());
                        if (cameraAnimationInProgress) {
                            AndroidUtilities.rectTmp.set(animationClipLeft + cameraViewOffsetX * (1f - cameraOpenProgress), animationClipTop + cameraViewOffsetY * (1f - cameraOpenProgress), animationClipRight, Math.min(maxY, animationClipBottom));
                        } else if (!cameraAnimationInProgress && !cameraOpened) {
                            AndroidUtilities.rectTmp.set(cameraViewOffsetX, cameraViewOffsetY, getMeasuredWidth(), Math.min(maxY, getMeasuredHeight()));
                        } else {
                            AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), Math.min(maxY, getMeasuredHeight()));
                        }
                        canvas.save();
                        canvas.clipRect(AndroidUtilities.rectTmp);
                        super.dispatchDraw(canvas);
                        canvas.restore();
                    }
                }

                @Override
                protected void onAttachedToWindow() {
                    super.onAttachedToWindow();
                    Bulletin.addDelegate(cameraView, bulletinDelegate);
                }

                @Override
                protected void onDetachedFromWindow() {
                    super.onDetachedFromWindow();
                    Bulletin.removeDelegate(cameraView);
                }

                @Override
                protected void receivedAmplitude(double amplitude) {
                    if (recordControl != null) {
                        recordControl.setAmplitude(Utilities.clamp((float) (amplitude / WaveDrawable.MAX_AMPLITUDE), 1, 0), true);
                    }
                }

                @Override
                public void toggleDual() {
                    super.toggleDual();
                    dualButton.setValue(cameraView.isDual());
                    setCameraFlashModeIcon(getCurrentFlashMode(), true);
                }
            };
            if (recordControl != null) {
                recordControl.setAmplitude(0, false);
            }
            cameraView.recordHevc = !collageLayoutView.hasLayout();
            collageLayoutView.setCameraView(cameraView);
            cameraView.isChangeAspectRationDual = true;
            dualButton.setVisibility(cameraView.dualAvailable() ? View.VISIBLE : View.GONE);
            cameraView.isHandleTouch = false;
            if (cameraView.isDual()) {
                cameraView.toggleDual();
            }
            if (cameraCell != null && lazy) {
                cameraView.setThumbDrawable(cameraCell.getDrawable());
            }
            cameraView.setRecordFile(AndroidUtilities.generateVideoPath(parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat()));
            cameraView.setFocusable(true);
            cameraView.setFpsLimit(30);
            cameraView.setContentDescription(LocaleController.getString(R.string.AccDescrInstantCamera));
            // TODO addView(cameraView
            //            parentAlert.getContainer().addView(cameraView, 1, new FrameLayout.LayoutParams(itemSize, itemSize));

            cameraView.setDelegate(() -> {
                String currentFlashMode = getCurrentFlashMode();
                if (TextUtils.equals(currentFlashMode, getNextFlashMode())) {
                    currentFlashMode = null;
                }
                setCameraFlashModeIcon(cameraOpened ? currentFlashMode : null, true);
                if (zoomControlView != null) {
                    zoomControlView.setZoom(cameraZoom = 0, false);
                }
                if (!cameraOpened) {
                    cameraInitAnimation = new AnimatorSet();
                    cameraInitAnimation.playTogether(
                            ObjectAnimator.ofFloat(cameraView, View.ALPHA, 0.0f, 1.0f),
                            ObjectAnimator.ofFloat(cameraIcon, View.ALPHA, 0.0f, 1.0f));
                    cameraInitAnimation.setDuration(180);
                    cameraInitAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animation.equals(cameraInitAnimation)) {
                                canSaveCameraPreview = true;
                                cameraInitAnimation = null;
                                if (!isHidden) {
                                    int count = gridView.getChildCount();
                                    for (int a = 0; a < count; a++) {
                                        View child = gridView.getChildAt(a);
                                        if (child instanceof PhotoAttachCameraCell) {
                                            child.setVisibility(View.INVISIBLE);
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            cameraInitAnimation = null;
                        }
                    });
                    cameraInitAnimation.start();
                }
                if (afterCameraInitRunnable != null) {
                    afterCameraInitRunnable.run();
                }
            });

            if (cameraIcon == null) {
                cameraIcon = new FrameLayout(getContext()) {
                    @Override
                    protected void onDraw(Canvas canvas) {
                        int maxY = (int) Math.min(parentAlert.getCommentTextViewTop() + currentPanTranslationY + parentAlert.getContainerView().getTranslationY() - collageLayoutView.getTranslationY(), getMeasuredHeight());
                        if (cameraOpened) {
                            maxY = getMeasuredHeight();
                        } else if (cameraAnimationInProgress) {
                            maxY = AndroidUtilities.lerp(maxY, getMeasuredHeight(), cameraOpenProgress);
                        }
                        int w = cameraDrawable.getIntrinsicWidth();
                        int h = cameraDrawable.getIntrinsicHeight();
                        int x = (itemSize - w) / 2;
                        int y = (itemSize - h) / 2;
                        if (cameraViewOffsetY != 0) {
                            y -= cameraViewOffsetY;
                        }
                        boolean clip = maxY < getMeasuredHeight();
                        if (clip) {
                            canvas.save();
                            canvas.clipRect(0, 0, getMeasuredWidth(), maxY);
                        }
                        cameraDrawable.setBounds(x, y, x + w, y + h);
                        cameraDrawable.draw(canvas);
                        if (clip) {
                            canvas.restore();
                        }
                    }
                };
                cameraIcon.setWillNotDraw(false);
                cameraIcon.setClipChildren(true);
            }
            parentAlert.getContainer().addView(cameraIcon, 2, new FrameLayout.LayoutParams(itemSize, itemSize));

            collageLayoutView.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraView.setEnabled(mediaEnabled);
            cameraIcon.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraIcon.setEnabled(mediaEnabled);
            if (isHidden) {
                collageLayoutView.setVisibility(GONE);
                cameraIcon.setVisibility(GONE);
            }
            if (cameraOpened) {
                cameraIcon.setAlpha(0f);
            } else {
                checkCameraViewPosition();
            }
            invalidate();
        }
        if (zoomControlView != null) {
            zoomControlView.setZoom(0.0f, false);
            cameraZoom = 0.0f;
        }
        if (!cameraOpened) {
            collageLayoutView.setTranslationX(cameraViewLocation[0]);
            collageLayoutView.setTranslationY(cameraViewLocation[1] + currentPanTranslationY);
            cameraIcon.setTranslationX(cameraViewLocation[0]);
            cameraIcon.setTranslationY(cameraViewLocation[1] + cameraViewOffsetY + currentPanTranslationY);
        }
    }

    public void hideCamera(boolean async) {
        if (!deviceHasGoodCamera || cameraView == null) {
            return;
        }
        saveLastCameraBitmap();
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = gridView.getChildAt(a);
            if (child instanceof PhotoAttachCameraCell) {
                child.setVisibility(View.VISIBLE);
                ((PhotoAttachCameraCell) child).updateBitmap();
                break;
            }
        }
        cameraView.destroy(async, null);
        if (cameraInitAnimation != null) {
            cameraInitAnimation.cancel();
            cameraInitAnimation = null;
        }
        AndroidUtilities.runOnUIThread(() -> {
            parentAlert.getContainer().removeView(collageLayoutView);
            if (collageLayoutView != null) {
                collageLayoutView.clear(true);
                collageLayoutView.setCameraView(null);
                collageLayoutView = null;
            }
//            parentAlert.getContainer().removeView(cameraView);
            parentAlert.getContainer().removeView(cameraIcon);
            cameraView = null;
            cameraIcon = null;
        }, 300);
        canSaveCameraPreview = false;
    }

    private void saveLastCameraBitmap() {
        if (!canSaveCameraPreview) {
            return;
        }
        try {
            TextureView textureView = cameraView.getTextureView();
            Bitmap bitmap = textureView.getBitmap();
            if (bitmap != null) {
                Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), cameraView.getMatrix(), true);
                bitmap.recycle();
                bitmap = newBitmap;
                Bitmap lastBitmap = Bitmap.createScaledBitmap(bitmap, 80, (int) (bitmap.getHeight() / (bitmap.getWidth() / 80.0f)), true);
                if (lastBitmap != null) {
                    if (lastBitmap != bitmap) {
                        bitmap.recycle();
                    }
                    Utilities.blurBitmap(lastBitmap, 7, 1, lastBitmap.getWidth(), lastBitmap.getHeight(), lastBitmap.getRowBytes());
                    File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb.jpg");
                    FileOutputStream stream = new FileOutputStream(file);
                    lastBitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    lastBitmap.recycle();
                    stream.close();
                }
            }
        } catch (Throwable ignore) {

        }
    }

    public void onActivityResultFragment(int requestCode, Intent data, String currentPicturePath) {
        if (parentAlert.destroyed) {
            return;
        }
        mediaFromExternalCamera = true;
        if (requestCode == 0) {
            PhotoViewer.getInstance().setParentActivity(parentAlert.baseFragment.getParentActivity(), resourcesProvider);
            PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder);
            Pair<Integer, Integer> orientation = AndroidUtilities.getImageOrientation(currentPicturePath);
            int width = 0, height = 0;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(new File(currentPicturePath).getAbsolutePath(), options);
                width = options.outWidth;
                height = options.outHeight;
            } catch (Exception ignore) {
            }
            MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, currentPicturePath, orientation.first, false, width, height, 0).setOrientation(orientation);
            photoEntry.canDeleteAfter = true;
            openPhotoViewer(photoEntry, false, true);
        } else if (requestCode == 2) {
            String videoPath = null;
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("pic path " + currentPicturePath);
            }
            if (data != null && currentPicturePath != null) {
                if (new File(currentPicturePath).exists()) {
                    data = null;
                }
            }
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("video record uri " + uri.toString());
                    }
                    videoPath = AndroidUtilities.getPath(uri);
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("resolved path = " + videoPath);
                    }
                    if (videoPath == null || !(new File(videoPath).exists())) {
                        videoPath = currentPicturePath;
                    }
                } else {
                    videoPath = currentPicturePath;
                }
                if (!(parentAlert.baseFragment instanceof ChatActivity) || !((ChatActivity) parentAlert.baseFragment).isSecretChat()) {
                    AndroidUtilities.addMediaToGallery(currentPicturePath);
                }
                currentPicturePath = null;
            }
            if (videoPath == null && currentPicturePath != null) {
                File f = new File(currentPicturePath);
                if (f.exists()) {
                    videoPath = currentPicturePath;
                }
            }

            MediaMetadataRetriever mediaMetadataRetriever = null;
            long duration = 0;
            try {
                mediaMetadataRetriever = new MediaMetadataRetriever();
                mediaMetadataRetriever.setDataSource(videoPath);
                String d = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (d != null) {
                    duration = (int) Math.ceil(Long.parseLong(d) / 1000.0f);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                try {
                    if (mediaMetadataRetriever != null) {
                        mediaMetadataRetriever.release();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            final Bitmap bitmap = SendMessagesHelper.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
            String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".jpg";
            final File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
            try {
                FileOutputStream stream = new FileOutputStream(cacheFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 55, stream);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            SharedConfig.saveConfig();

            MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, lastImageId--, 0, videoPath, 0, true, bitmap.getWidth(), bitmap.getHeight(), 0);
            entry.duration = (int) duration;
            entry.thumbPath = cacheFile.getAbsolutePath();
            openPhotoViewer(entry, false, true);
        }
    }

    float additionCloseCameraY;

    public void closeCamera(boolean animated) {
        // TODO не закрывать камеру при записи видео
        if (takingPhoto || cameraView == null) {
            return;
        }
        cameraView.isHandleTouch = false;
        animateCameraValues[1] = itemSize;
        animateCameraValues[2] = itemSize;
        if (zoomControlHideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable);
            zoomControlHideRunnable = null;
        }
        setCameraFlashModeIcon(null, true);
        flashViews.flashOut();
        if (cameraView.isDual()) {
            cameraView.toggleDual();
        }
        closeCollage();
        AndroidUtilities.setLightNavigationBar(parentAlert.getWindow(), AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_windowBackgroundGray)) > 0.721);
        if (animated) {
            additionCloseCameraY = collageLayoutView.getTranslationY();

            cameraAnimationInProgress = true;
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(this, "cameraOpenProgress", 0.0f));
            animators.add(ObjectAnimator.ofFloat(cameraControlContainer, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 0.0f));

            notificationsLocker.lock();
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.setDuration(220);
            animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    notificationsLocker.unlock();
                    cameraExpanded = false;
                    parentAlert.getWindow().clearFlags(FLAG_KEEP_SCREEN_ON);
                    setCameraOpenProgress(0f);
                    cameraAnimationInProgress = false;
                    if (collageLayoutView != null) {
                        if (Build.VERSION.SDK_INT >= 21) {
                            collageLayoutView.invalidateOutline();
                        } else {
                            collageLayoutView.invalidate();
                        }
                    }
                    cameraOpened = false;

                    if (cameraControlContainer != null) {
                        cameraControlContainer.setVisibility(View.GONE);
                    }
                    if (zoomControlView != null) {
                        zoomControlView.setVisibility(View.GONE);
                        zoomControlView.setTag(null);
                    }
                    if (cameraPhotoRecyclerView != null) {
                        cameraPhotoRecyclerView.setVisibility(View.GONE);
                    }
                    if (cameraView != null) {
                        cameraView.setFpsLimit(30);
                        if (Build.VERSION.SDK_INT >= 21 && collageLayoutView != null) {
                            collageLayoutView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                        }
                    }
                }
            });
            animatorSet.start();
        } else {
            cameraExpanded = false;
            parentAlert.getWindow().clearFlags(FLAG_KEEP_SCREEN_ON);
            setCameraOpenProgress(0f);
            animateCameraValues[0] = 0;
            setCameraOpenProgress(0);
            cameraControlContainer.setAlpha(0);
            cameraControlContainer.setVisibility(View.GONE);
            zoomControlView.setAlpha(0);
            zoomControlView.setTag(null);
            zoomControlView.setVisibility(View.GONE);
            cameraPhotoRecyclerView.setAlpha(0);
            counterTextView.setAlpha(0);
            cameraPhotoRecyclerView.setVisibility(View.GONE);
            cameraOpened = false;
            if (cameraView != null) {
                cameraView.setFpsLimit(30);
                if (Build.VERSION.SDK_INT >= 21 && collageLayoutView != null) {
                    collageLayoutView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                }
            }
        }
        if (collageLayoutView != null) {
            collageLayoutView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
        if (Build.VERSION.SDK_INT >= 19) {
            gridView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }

        if (!LiteMode.isEnabled(LiteMode.FLAGS_CHAT) && cameraView != null) {
            cameraView.showTexture(false, animated);
        }
    }

    float animationClipTop;
    float animationClipBottom;
    float animationClipRight;
    float animationClipLeft;

    @Keep
    public void setCameraOpenProgress(float value) {
        if (cameraView == null && collageLayoutView != null) {
            return;
        }
        cameraOpenProgress = value;
        float startWidth = animateCameraValues[1];
        float startHeight = animateCameraValues[2];
        boolean isPortrait = AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y;
        float endWidth = parentAlert.getContainer().getWidth() - parentAlert.getLeftInset() - parentAlert.getRightInset();
        float endHeight = parentAlert.getContainer().getHeight();

        float fromX = cameraViewLocation[0];
        float fromY = cameraViewLocation[1];
        float toX = 0;
        float toY = additionCloseCameraY;

        if (value == 0) {
            cameraIcon.setTranslationX(cameraViewLocation[0]);
            cameraIcon.setTranslationY(cameraViewLocation[1] + cameraViewOffsetY);
        }


        int cameraViewW, cameraViewH;
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) collageLayoutView.getLayoutParams();

        float textureStartHeight = cameraView.getTextureHeight(startWidth, startHeight);
        float textureEndHeight = cameraView.getTextureHeight(endWidth, endHeight);

        float fromScale = textureStartHeight / textureEndHeight;
        float fromScaleY = startHeight / endHeight;
        float fromScaleX = startWidth / endWidth;

        if (cameraExpanded) {
            cameraViewW = (int) endWidth;
            cameraViewH = (int) endHeight;
            final float s = fromScale * (1f - value) + value;
            cameraView.getTextureView().setScaleX(s);
            cameraView.getTextureView().setScaleY(s);

            final float sX = fromScaleX * (1f - value) + value;
            final float sY = fromScaleY * (1f - value) + value;

            final float scaleOffsetY = (1 - sY) * endHeight / 2;
            final float scaleOffsetX = (1 - sX) * endWidth / 2;

            collageLayoutView.setTranslationX(fromX * (1f - value) + toX * value - scaleOffsetX);
            collageLayoutView.setTranslationY(fromY * (1f - value) + toY * value - scaleOffsetY);
            animationClipTop = fromY * (1f - value) - collageLayoutView.getTranslationY();
            animationClipBottom = ((fromY + startHeight) * (1f - value) - collageLayoutView.getTranslationY()) + endHeight * value;

            animationClipLeft = fromX * (1f - value) - collageLayoutView.getTranslationX();
            animationClipRight = ((fromX + startWidth) * (1f - value) - collageLayoutView.getTranslationX()) + endWidth * value;
        } else {
            cameraViewW = (int) startWidth;
            cameraViewH = (int) startHeight;
            cameraView.getTextureView().setScaleX(1f);
            cameraView.getTextureView().setScaleY(1f);
            animationClipTop = 0;
            animationClipBottom = endHeight;
            animationClipLeft = 0;
            animationClipRight = endWidth;

            collageLayoutView.setTranslationX(fromX);
            collageLayoutView.setTranslationY(fromY);
        }

        if (value <= 0.5f) {
            cameraIcon.setAlpha(1.0f - value / 0.5f);
        } else {
            cameraIcon.setAlpha(0.0f);
        }

        if (layoutParams.width != cameraViewW || layoutParams.height != cameraViewH) {
            layoutParams.width = cameraViewW;
            layoutParams.height = cameraViewH;
            collageLayoutView.requestLayout();
        }
        if (Build.VERSION.SDK_INT >= 21) {
            collageLayoutView.invalidateOutline();
        } else {
            collageLayoutView.invalidate();
        }
    }

    @Keep
    public float getCameraOpenProgress() {
        return cameraOpenProgress;
    }

    protected void checkCameraViewPosition() {
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().stickerMakerView != null && PhotoViewer.getInstance().stickerMakerView.isThanosInProgress) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            if (collageLayoutView != null) {
                collageLayoutView.invalidateOutline();
            }
            RecyclerView.ViewHolder holder = gridView.findViewHolderForAdapterPosition(itemsPerRow - 1);
            if (holder != null) {
                holder.itemView.invalidateOutline();
            }
            if (!adapter.needCamera || !deviceHasGoodCamera || selectedAlbumEntry != galleryAlbumEntry) {
                holder = gridView.findViewHolderForAdapterPosition(0);
                if (holder != null) {
                    holder.itemView.invalidateOutline();
                }
            }
        }
        if (collageLayoutView != null) {
            collageLayoutView.invalidate();
        }

        if (!deviceHasGoodCamera) {
            return;
        }
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = gridView.getChildAt(a);
            if (child instanceof PhotoAttachCameraCell) {
                if (Build.VERSION.SDK_INT >= 19) {
                    if (!child.isAttachedToWindow()) {
                        break;
                    }
                }

                float topLocal = child.getY() + gridView.getY() + getY();
                float top = topLocal + parentAlert.getSheetContainer().getY();
                float left = child.getX() + gridView.getX() + getX() + parentAlert.getSheetContainer().getX();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    left -= getRootWindowInsets().getSystemWindowInsetLeft();
                }

                float maxY = (Build.VERSION.SDK_INT >= 21 && !parentAlert.inBubbleMode ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() + parentAlert.topCommentContainer.getMeasuredHeight() * parentAlert.topCommentContainer.getAlpha();
                if (parentAlert.mentionContainer != null && parentAlert.mentionContainer.isReversed()) {
                    maxY = Math.max(maxY, parentAlert.mentionContainer.getY() + parentAlert.mentionContainer.clipTop() - parentAlert.currentPanTranslationY);
                }
                float newCameraViewOffsetY;
                if (topLocal < maxY) {
                    newCameraViewOffsetY = maxY - topLocal;
                } else {
                    newCameraViewOffsetY = 0;
                }

                if (newCameraViewOffsetY != cameraViewOffsetY) {
                    cameraViewOffsetY = newCameraViewOffsetY;
                    if (collageLayoutView != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            collageLayoutView.invalidateOutline();
                        } else {
                            collageLayoutView.invalidate();
                        }
                    }
                    if (cameraIcon != null) {
                        cameraIcon.invalidate();
                    }
                }

                int containerHeight = parentAlert.getSheetContainer().getMeasuredHeight();
                maxY = (int) (containerHeight - parentAlert.buttonsRecyclerView.getMeasuredHeight() + parentAlert.buttonsRecyclerView.getTranslationY());
                if (parentAlert.mentionContainer != null) {
                    maxY -= parentAlert.mentionContainer.clipBottom() - dp(6);
                }

                if (topLocal + child.getMeasuredHeight() > maxY) {
                    cameraViewOffsetBottomY = Math.min(-dp(5), topLocal - maxY) + child.getMeasuredHeight();
                } else {
                    cameraViewOffsetBottomY = 0;
                }

                cameraViewLocation[0] = left;
                cameraViewLocation[1] = top;
                applyCameraViewPosition();
                return;
            }
        }


        if (cameraViewOffsetY != 0 || cameraViewOffsetX != 0) {
            cameraViewOffsetX = 0;
            cameraViewOffsetY = 0;
            if (collageLayoutView != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    collageLayoutView.invalidateOutline();
                } else {
                    collageLayoutView.invalidate();
                }
            }
            if (cameraIcon != null) {
                cameraIcon.invalidate();
            }
        }

        cameraViewLocation[0] = dp(-400);
        cameraViewLocation[1] = 0;

        applyCameraViewPosition();
    }

    private void applyCameraViewPosition() {
        if (cameraView != null && collageLayoutView != null) {
            if (!cameraOpened) {
                collageLayoutView.setTranslationX(cameraViewLocation[0]);
                collageLayoutView.setTranslationY(cameraViewLocation[1] + currentPanTranslationY);
            }
            cameraIcon.setTranslationX(cameraViewLocation[0]);
            cameraIcon.setTranslationY(cameraViewLocation[1] + cameraViewOffsetY + currentPanTranslationY);
            int finalWidth = itemSize;
            int finalHeight = itemSize;

            LayoutParams layoutParams;
            if (!cameraOpened) {
                cameraView.setClipTop((int) cameraViewOffsetY);
                cameraView.setClipBottom((int) cameraViewOffsetBottomY);
                layoutParams = (LayoutParams) collageLayoutView.getLayoutParams();
                if (layoutParams.height != finalHeight || layoutParams.width != finalWidth) {
                    layoutParams.width = finalWidth;
                    layoutParams.height = finalHeight;
                    collageLayoutView.setLayoutParams(layoutParams);
                    final LayoutParams layoutParamsFinal = layoutParams;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (collageLayoutView != null) {
                            collageLayoutView.setLayoutParams(layoutParamsFinal);
                        }
                    });
                }
            }

            finalWidth = (int) (itemSize - cameraViewOffsetX);
            finalHeight = (int) (itemSize - cameraViewOffsetY - cameraViewOffsetBottomY);

            layoutParams = (LayoutParams) cameraIcon.getLayoutParams();
            if (layoutParams.height != finalHeight || layoutParams.width != finalWidth) {
                layoutParams.width = finalWidth;
                layoutParams.height = finalHeight;
                cameraIcon.setLayoutParams(layoutParams);
                final LayoutParams layoutParamsFinal = layoutParams;
                AndroidUtilities.runOnUIThread(() -> {
                    if (cameraIcon != null) {
                        cameraIcon.setLayoutParams(layoutParamsFinal);
                    }
                });
            }
        }
    }

    public HashMap<Object, Object> getSelectedPhotos() {
        return selectedPhotos;
    }

    public ArrayList<Object> getSelectedPhotosOrder() {
        return selectedPhotosOrder;
    }

    public void updateSelected(HashMap<Object, Object> newSelectedPhotos, ArrayList<Object> newPhotosOrder, boolean updateLayout) {
        selectedPhotos.clear();
        selectedPhotos.putAll(newSelectedPhotos);
        selectedPhotosOrder.clear();
        selectedPhotosOrder.addAll(newPhotosOrder);
        if (updateLayout) {
            updatePhotosCounter(false);
            updateCheckedPhotoIndices();

            final int count = gridView.getChildCount();
            for (int i = 0; i < count; ++i) {
                View child = gridView.getChildAt(i);
                if (child instanceof PhotoAttachPhotoCell) {
                    int position = gridView.getChildAdapterPosition(child);
                    if (adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                        position--;
                    }

                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) child;
                    if (parentAlert.avatarPicker != 0) {
                        cell.getCheckBox().setVisibility(GONE);
                    }
                    MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(position);
                    if (photoEntry != null) {
                        cell.setPhotoEntry(photoEntry, selectedPhotos.size() > 1, adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry, position == adapter.getItemCount() - 1);
                        if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                            cell.setChecked(selectedPhotosOrder.indexOf(photoEntry.imageId), selectedPhotos.containsKey(photoEntry.imageId), false);
                        } else {
                            cell.setChecked(-1, selectedPhotos.containsKey(photoEntry.imageId), false);
                        }
                    }
                }
            }
        }
    }

    private boolean isNoGalleryPermissions() {
        Activity activity = AndroidUtilities.findActivity(getContext());
        if (activity == null) {
            activity = parentAlert.baseFragment.getParentActivity();
        }
        return Build.VERSION.SDK_INT >= 23 && (
                activity == null ||
                        Build.VERSION.SDK_INT >= 33 && (
                                activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
                                        activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED
                        ) ||
                        Build.VERSION.SDK_INT < 33 && activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        );
    }

    public void checkStorage() {
        if (noGalleryPermissions && Build.VERSION.SDK_INT >= 23) {
            final Activity activity = parentAlert.baseFragment.getParentActivity();

            noGalleryPermissions = isNoGalleryPermissions();
            if (!noGalleryPermissions) {
                loadGalleryPhotos();
            }
            adapter.notifyDataSetChanged();
            cameraAttachAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void scrollToTop() {
        gridView.smoothScrollToPosition(0);
    }

    @Override
    public int needsActionBar() {
        return 1;
    }

    @Override
    public void onMenuItemClick(int id) {
        if (id == caption) {
            parentAlert.setCaptionAbove(!parentAlert.captionAbove);
            captionItem.setState(!parentAlert.captionAbove, true);
            return;
        }
        if (id == group || id == compress) {
            if (parentAlert.maxSelectedPhotos > 0 && selectedPhotosOrder.size() > 1) {
                TLRPC.Chat chat = parentAlert.getChat();
                if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
                    AlertsCreator.createSimpleAlert(getContext(), LocaleController.getString(R.string.Slowmode), LocaleController.getString(R.string.SlowmodeSendError), resourcesProvider).show();
                    return;
                }
            }
        }
        if (id == group) {
            if (parentAlert.editingMessageObject == null && parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(getContext(), ((ChatActivity) parentAlert.baseFragment).getDialogId(), (notify, scheduleDate) -> {
                    parentAlert.applyCaption();
                    parentAlert.delegate.didPressedButton(7, false, notify, scheduleDate, 0, parentAlert.isCaptionAbove(), false);
                }, resourcesProvider);
            } else {
                parentAlert.applyCaption();
                parentAlert.delegate.didPressedButton(7, false, true, 0, 0, parentAlert.isCaptionAbove(), false);
            }
        } else if (id == compress) {
            if (parentAlert.editingMessageObject == null && parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(getContext(), ((ChatActivity) parentAlert.baseFragment).getDialogId(), (notify, scheduleDate) -> {
                    parentAlert.applyCaption();
                    parentAlert.delegate.didPressedButton(4, true, notify, scheduleDate, 0, parentAlert.isCaptionAbove(), false);
                }, resourcesProvider);
            } else {
                parentAlert.applyCaption();
                parentAlert.delegate.didPressedButton(4, true, true, 0, 0, parentAlert.isCaptionAbove(), false);
            }
        } else if (id == spoiler) {
            if (parentAlert.getPhotoPreviewLayout() != null) {
                parentAlert.getPhotoPreviewLayout().startMediaCrossfade();
            }

            boolean spoilersEnabled = false;
            for (Map.Entry<Object, Object> en : selectedPhotos.entrySet()) {
                MediaController.PhotoEntry entry = (MediaController.PhotoEntry) en.getValue();
                if (entry.hasSpoiler) {
                    spoilersEnabled = true;
                    break;
                }
            }
            spoilersEnabled = !spoilersEnabled;
            boolean finalSpoilersEnabled = spoilersEnabled;
            AndroidUtilities.runOnUIThread(() -> {
                spoilerItem.setText(LocaleController.getString(finalSpoilersEnabled ? R.string.DisablePhotoSpoiler : R.string.EnablePhotoSpoiler));
                if (finalSpoilersEnabled) {
                    spoilerItem.setIcon(R.drawable.msg_spoiler_off);
                } else {
                    spoilerItem.setAnimatedIcon(R.raw.photo_spoiler);
                }
                if (finalSpoilersEnabled) {
                    parentAlert.selectedMenuItem.hideSubItem(compress);
                    if (getSelectedItemsCount() <= 1) {
                        parentAlert.selectedMenuItem.hideSubItem(media_gap);
                    }
                } else {
                    parentAlert.selectedMenuItem.showSubItem(compress);
                    if (getSelectedItemsCount() <= 1) {
                        parentAlert.selectedMenuItem.showSubItem(media_gap);
                    }
                }
            }, 200);

            List<Integer> selectedIds = new ArrayList<>();
            for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
                if (entry.getValue() instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry.getValue();
                    photoEntry.hasSpoiler = spoilersEnabled;
                    photoEntry.isChatPreviewSpoilerRevealed = false;
                    photoEntry.isAttachSpoilerRevealed = false;
                    selectedIds.add(photoEntry.imageId);
                }
            }

            gridView.forAllChild(view -> {
                if (view instanceof PhotoAttachPhotoCell) {
                    MediaController.PhotoEntry entry = ((PhotoAttachPhotoCell) view).getPhotoEntry();
                    ((PhotoAttachPhotoCell) view).setHasSpoiler(entry != null && selectedIds.contains(entry.imageId) && finalSpoilersEnabled);
                }
            });
            if (parentAlert.getCurrentAttachLayout() != this) {
                adapter.notifyDataSetChanged();
            }

            if (parentAlert.getPhotoPreviewLayout() != null) {
                parentAlert.getPhotoPreviewLayout().invalidateGroupsView();
            }
        } else if (id == open_in) {
            try {
                if (parentAlert.baseFragment instanceof ChatActivity || parentAlert.avatarPicker == 2) {
                    Intent videoPickerIntent = new Intent();
                    videoPickerIntent.setType("video/*");
                    videoPickerIntent.setAction(Intent.ACTION_GET_CONTENT);
                    videoPickerIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, FileLoader.DEFAULT_MAX_FILE_SIZE);

                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                    photoPickerIntent.setType("image/*");
                    Intent chooserIntent = Intent.createChooser(photoPickerIntent, null);
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{videoPickerIntent});

                    if (parentAlert.avatarPicker != 0) {
                        parentAlert.baseFragment.startActivityForResult(chooserIntent, 14);
                    } else {
                        parentAlert.baseFragment.startActivityForResult(chooserIntent, 1);
                    }
                } else {
                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                    photoPickerIntent.setType("image/*");
                    if (parentAlert.avatarPicker != 0) {
                        parentAlert.baseFragment.startActivityForResult(photoPickerIntent, 14);
                    } else {
                        parentAlert.baseFragment.startActivityForResult(photoPickerIntent, 1);
                    }
                }
                parentAlert.dismiss(true);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (id == preview) {
            parentAlert.updatePhotoPreview(parentAlert.getCurrentAttachLayout() != parentAlert.getPhotoPreviewLayout());
        } else if (id == stars) {
            StarsIntroActivity.showMediaPriceSheet(getContext(), getStarsPrice(), true, (price, done) -> {
                done.run();
                setStarsPrice(price);
            }, resourcesProvider);
        } else if (id >= 10) {
            selectedAlbumEntry = dropDownAlbums.get(id - 10);
            if (selectedAlbumEntry == galleryAlbumEntry) {
                dropDown.setText(LocaleController.getString(R.string.ChatGallery));
            } else {
                dropDown.setText(selectedAlbumEntry.bucketName);
            }
            adapter.notifyDataSetChanged();
            cameraAttachAdapter.notifyDataSetChanged();
            layoutManager.scrollToPositionWithOffset(0, -gridView.getPaddingTop() + dp(7));
        }
    }

    @Override
    public int getSelectedItemsCount() {
        return selectedPhotosOrder.size();
    }

    @Override
    public void onSelectedItemsCountChanged(int count) {
        final boolean hasCompress;
        final boolean hasGroup;
        if (count <= 1 || parentAlert.editingMessageObject != null) {
            hasGroup = false;
            parentAlert.selectedMenuItem.hideSubItem(group);
            if (count == 0) {
                parentAlert.selectedMenuItem.showSubItem(open_in);
                hasCompress = false;
                parentAlert.selectedMenuItem.hideSubItem(compress);
            } else if (documentsEnabled && getStarsPrice() <= 0 && parentAlert.editingMessageObject == null) {
                hasCompress = true;
                parentAlert.selectedMenuItem.showSubItem(compress);
            } else {
                hasCompress = false;
                parentAlert.selectedMenuItem.hideSubItem(compress);
            }
        } else {
            if (getStarsPrice() <= 0) {
                hasGroup = true;
                parentAlert.selectedMenuItem.showSubItem(group);
            } else {
                hasGroup = false;
                parentAlert.selectedMenuItem.hideSubItem(group);
            }
            if (documentsEnabled && getStarsPrice() <= 0) {
                hasCompress = true;
                parentAlert.selectedMenuItem.showSubItem(compress);
            } else {
                hasCompress = false;
                parentAlert.selectedMenuItem.hideSubItem(compress);
            }
        }
        if (count != 0) {
            parentAlert.selectedMenuItem.hideSubItem(open_in);
        }
        if (count > 1) {
            parentAlert.selectedMenuItem.showSubItem(preview_gap);
            parentAlert.selectedMenuItem.showSubItem(preview);
            compressItem.setText(LocaleController.getString(R.string.SendAsFiles));
        } else {
            parentAlert.selectedMenuItem.hideSubItem(preview_gap);
            parentAlert.selectedMenuItem.hideSubItem(preview);
            if (count != 0) {
                compressItem.setText(LocaleController.getString(R.string.SendAsFile));
            }
        }
        final boolean hasSpoiler = count > 0 && getStarsPrice() <= 0 && (parentAlert == null || parentAlert.baseFragment instanceof ChatActivity && !((ChatActivity) parentAlert.baseFragment).isSecretChat());
        final boolean hasCaption = count > 0 && parentAlert != null && parentAlert.hasCaption() && parentAlert.baseFragment instanceof ChatActivity;
        final boolean hasStars = count > 0 && (parentAlert != null && parentAlert.baseFragment instanceof ChatActivity && ChatObject.isChannelAndNotMegaGroup(((ChatActivity) parentAlert.baseFragment).getCurrentChat()) && ((ChatActivity) parentAlert.baseFragment).getCurrentChatInfo() != null && ((ChatActivity) parentAlert.baseFragment).getCurrentChatInfo().paid_media_allowed);
        if (!hasSpoiler) {
            spoilerItem.setText(LocaleController.getString(R.string.EnablePhotoSpoiler));
            spoilerItem.setAnimatedIcon(R.raw.photo_spoiler);
            parentAlert.selectedMenuItem.hideSubItem(spoiler);
        } else if (parentAlert != null) {
            parentAlert.selectedMenuItem.showSubItem(spoiler);
        }
        if (hasCaption) {
            captionItem.setVisibility(View.VISIBLE);
        } else {
            captionItem.setVisibility(View.GONE);
        }
        if ((hasSpoiler || hasCaption) && (hasCompress || hasGroup)) {
            parentAlert.selectedMenuItem.showSubItem(media_gap);
        } else {
            parentAlert.selectedMenuItem.hideSubItem(media_gap);
        }
        if (hasStars) {
            updateStarsItem();
            updatePhotoStarsPrice();
            parentAlert.selectedMenuItem.showSubItem(stars);
        } else {
            parentAlert.selectedMenuItem.hideSubItem(stars);
        }
    }

    private void updateStarsItem() {
        if (starsItem == null) return;
        long amount = getStarsPrice();
        if (amount > 0) {
            starsItem.setText(getString(R.string.PaidMediaPriceButton));
            starsItem.setSubtext(formatPluralString("Stars", (int) amount));
        } else {
            starsItem.setText(getString(R.string.PaidMediaButton));
            starsItem.setSubtext(null);
        }
    }

    @Override
    public void applyCaption(CharSequence text) {
        for (int a = 0; a < selectedPhotosOrder.size(); a++) {
            if (a == 0) {
                final Object key = selectedPhotosOrder.get(a);
                Object o = selectedPhotos.get(key);
                if (o instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                    photoEntry1 = photoEntry1.clone();
                    CharSequence[] caption = new CharSequence[]{text};
                    photoEntry1.entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(caption, false);
                    photoEntry1.caption = caption[0];
                    o = photoEntry1;
                } else if (o instanceof MediaController.SearchImage) {
                    MediaController.SearchImage photoEntry1 = (MediaController.SearchImage) o;
                    photoEntry1 = photoEntry1.clone();
                    CharSequence[] caption = new CharSequence[]{text};
                    photoEntry1.entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(caption, false);
                    photoEntry1.caption = caption[0];
                    o = photoEntry1;
                }
                selectedPhotos.put(key, o);
            }
        }
    }

    public boolean captionForAllMedia() {
        int captionCount = 0;
        for (int a = 0; a < selectedPhotosOrder.size(); a++) {
            Object o = selectedPhotos.get(selectedPhotosOrder.get(a));
            CharSequence caption = null;
            if (o instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                caption = photoEntry1.caption;
            } else if (o instanceof MediaController.SearchImage) {
                MediaController.SearchImage photoEntry1 = (MediaController.SearchImage) o;
                caption = photoEntry1.caption;
            }
            if (!TextUtils.isEmpty(caption)) {
                captionCount++;
            }
        }
        return captionCount <= 1;
    }

    @Override
    public void onDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.cameraInitied);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.albumsDidLoad);
    }

    @Override
    public void onPause() {
        if (recordControl == null) {
            return;
        }
        if (!requestingPermissions) {
            if (cameraView != null && recordControl.isRecording()) {
                CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), false);
                recordControl.stopRecording();
            }
            if (cameraOpened) {
                closeCamera(false);
            }
            hideCamera(true);
        } else {
            if (cameraView != null && recordControl.isRecording()) {
                recordControl.stopRecording();
            }
            requestingPermissions = false;
        }
    }

    @Override
    public void onResume() {
        if (parentAlert.isShowing() && !parentAlert.isDismissed() && !PhotoViewer.getInstance().isVisible()) {
            checkCamera(false);
        }
        if (recordControl != null) {
            recordControl.updateGalleryImage(false);
        }
    }

    @Override
    public int getListTopPadding() {
        return gridView.getPaddingTop();
    }

    public int currentItemTop = 0;

    @Override
    public int getCurrentItemTop() {
        if (gridView.getChildCount() <= 0) {
            gridView.setTopGlowOffset(currentItemTop = gridView.getPaddingTop());
            progressView.setTranslationY(0);
            return Integer.MAX_VALUE;
        }
        View child = gridView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = dp(7);
        if (top >= dp(7) && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        progressView.setTranslationY(newOffset + (getMeasuredHeight() - newOffset - dp(50) - progressView.getMeasuredHeight()) / 2);
        gridView.setTopGlowOffset(newOffset);
        return currentItemTop = newOffset;
    }

    @Override
    public int getFirstOffset() {
        return getListTopPadding() + dp(56);
    }

    @Override
    public void checkColors() {
        if (cameraIcon != null) {
            cameraIcon.invalidate();
        }
        int textColor = forceDarkTheme ? Theme.key_voipgroup_actionBarItems : Theme.key_dialogTextBlack;
        Theme.setDrawableColor(cameraDrawable, getThemedColor(Theme.key_dialogCameraIcon));
        progressView.setTextColor(getThemedColor(Theme.key_emptyListPlaceholder));
        gridView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        RecyclerView.ViewHolder holder = gridView.findViewHolderForAdapterPosition(0);
        if (holder != null && holder.itemView instanceof PhotoAttachCameraCell) {
            ((PhotoAttachCameraCell) holder.itemView).getImageView().setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogCameraIcon), PorterDuff.Mode.MULTIPLY));
        }

        dropDown.setTextColor(getThemedColor(textColor));
        dropDownContainer.setPopupItemsColor(getThemedColor(forceDarkTheme ? Theme.key_voipgroup_actionBarItems : Theme.key_actionBarDefaultSubmenuItem), false);
        dropDownContainer.setPopupItemsColor(getThemedColor(forceDarkTheme ? Theme.key_voipgroup_actionBarItems : Theme.key_actionBarDefaultSubmenuItem), true);
        dropDownContainer.redrawPopup(getThemedColor(forceDarkTheme ? Theme.key_voipgroup_actionBarUnscrolled : Theme.key_actionBarDefaultSubmenuBackground));
        Theme.setDrawableColor(dropDownDrawable, getThemedColor(textColor));
    }

    @Override
    public void onInit(boolean hasVideo, boolean hasPhoto, boolean hasDocuments) {
        mediaEnabled = hasVideo || hasPhoto;
        videoEnabled = hasVideo;
        photoEnabled = hasPhoto;
        documentsEnabled = hasDocuments;
        if (collageLayoutView != null) {
            collageLayoutView.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraView.setEnabled(mediaEnabled);
        }
        if (cameraIcon != null) {
            cameraIcon.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraIcon.setEnabled(mediaEnabled);
        }
        if ((parentAlert.baseFragment instanceof ChatActivity || parentAlert.getChat() != null) && parentAlert.avatarPicker == 0) {
            galleryAlbumEntry = MediaController.allMediaAlbumEntry;
            if (mediaEnabled) {
                progressView.setText(LocaleController.getString(R.string.NoPhotos));
                progressView.setLottie(0, 0, 0);
            } else {
                TLRPC.Chat chat = parentAlert.getChat();
                progressView.setLottie(R.raw.media_forbidden, 150, 150);
                if (ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_MEDIA)) {
                    progressView.setText(LocaleController.getString(R.string.GlobalAttachMediaRestricted));
                } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                    progressView.setText(LocaleController.formatString("AttachMediaRestrictedForever", R.string.AttachMediaRestrictedForever));
                } else {
                    progressView.setText(LocaleController.formatString("AttachMediaRestricted", R.string.AttachMediaRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date)));
                }
            }
        } else {
            if (shouldLoadAllMedia()) {
                galleryAlbumEntry = MediaController.allMediaAlbumEntry;
            } else {
                galleryAlbumEntry = MediaController.allPhotosAlbumEntry;
            }
        }
        if (Build.VERSION.SDK_INT >= 23) {
            noGalleryPermissions = isNoGalleryPermissions();
        }
        if (galleryAlbumEntry != null) {
            for (int a = 0; a < Math.min(100, galleryAlbumEntry.photos.size()); a++) {
                MediaController.PhotoEntry photoEntry = galleryAlbumEntry.photos.get(a);
                photoEntry.reset();
            }
        }
        clearSelectedPhotos();
        updatePhotosCounter(false);
        cameraPhotoLayoutManager.scrollToPositionWithOffset(0, 1000000);
        layoutManager.scrollToPositionWithOffset(0, 1000000);

        dropDown.setText(LocaleController.getString(R.string.ChatGallery));

        selectedAlbumEntry = galleryAlbumEntry;
        if (selectedAlbumEntry != null) {
            loading = false;
            if (progressView != null) {
                progressView.showTextView();
            }
        }
        updateAlbumsDropDown();
    }

    @Override
    public boolean canScheduleMessages() {
        boolean hasTtl = false;
        for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
            Object object = entry.getValue();
            if (object instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                if (photoEntry.ttl != 0) {
                    hasTtl = true;
                    break;
                }
            } else if (object instanceof MediaController.SearchImage) {
                MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                if (searchImage.ttl != 0) {
                    hasTtl = true;
                    break;
                }
            }
        }
        if (hasTtl) {
            return false;
        }
        return true;
    }

    @Override
    public void onButtonsTranslationYUpdated() {
        checkCameraViewPosition();
        invalidate();
    }

    @Override
    public void setTranslationY(float translationY) {
        if (parentAlert.getSheetAnimationType() == 1) {
            float scale = -0.1f * (translationY / 40.0f);
            for (int a = 0, N = gridView.getChildCount(); a < N; a++) {
                View child = gridView.getChildAt(a);
                if (child instanceof PhotoAttachCameraCell) {
                    PhotoAttachCameraCell cell = (PhotoAttachCameraCell) child;
                    cell.getImageView().setScaleX(1.0f + scale);
                    cell.getImageView().setScaleY(1.0f + scale);
                } else if (child instanceof PhotoAttachPhotoCell) {
                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) child;
                    cell.getCheckBox().setScaleX(1.0f + scale);
                    cell.getCheckBox().setScaleY(1.0f + scale);
                }
            }
        }
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
        invalidate();
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    private ViewPropertyAnimator headerAnimator;

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        if (headerAnimator != null) {
            headerAnimator.cancel();
        }
        dropDownContainer.setVisibility(VISIBLE);
        if (!(previousLayout instanceof ChatAttachAlertPhotoLayoutPreview)) {
            clearSelectedPhotos();
            dropDown.setAlpha(1);
        } else {
            headerAnimator = dropDown.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            headerAnimator.start();
        }
        parentAlert.actionBar.setTitle("");

        layoutManager.scrollToPositionWithOffset(0, 0);
        if (previousLayout instanceof ChatAttachAlertPhotoLayoutPreview) {
            Runnable setScrollY = () -> {
                int currentItemTop = previousLayout.getCurrentItemTop(),
                        paddingTop = previousLayout.getListTopPadding();
                gridView.scrollBy(0, (currentItemTop > dp(8) ? paddingTop - currentItemTop : paddingTop));
            };
            gridView.post(setScrollY);
        }

        checkCameraViewPosition();

        resumeCameraPreview();
    }

    @Override
    public void onShown() {
        isHidden = false;
        if (collageLayoutView != null) {
            collageLayoutView.setVisibility(VISIBLE);
        }
        if (cameraIcon != null) {
            cameraIcon.setVisibility(VISIBLE);
        }
        if (cameraView != null) {
            int count = gridView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = gridView.getChildAt(a);
                if (child instanceof PhotoAttachCameraCell) {
                    child.setVisibility(View.INVISIBLE);
                    break;
                }
            }
        }
        if (checkCameraWhenShown) {
            checkCameraWhenShown = false;
            checkCamera(true);
        }
    }

    public void setCheckCameraWhenShown(boolean checkCameraWhenShown) {
        this.checkCameraWhenShown = checkCameraWhenShown;
    }

    @Override
    public void onHideShowProgress(float progress) {
        if (collageLayoutView != null) {
            collageLayoutView.setAlpha(progress);
            cameraIcon.setAlpha(progress);
            if (progress != 0 && collageLayoutView.getVisibility() != VISIBLE) {
                collageLayoutView.setVisibility(VISIBLE);
                cameraIcon.setVisibility(VISIBLE);
            } else if (progress == 0 && collageLayoutView.getVisibility() != INVISIBLE) {
                collageLayoutView.setVisibility(INVISIBLE);
                cameraIcon.setVisibility(INVISIBLE);
            }
        }
    }

    @Override
    public void onHide() {
        isHidden = true;
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = gridView.getChildAt(a);
            if (child instanceof PhotoAttachCameraCell) {
                PhotoAttachCameraCell cell = (PhotoAttachCameraCell) child;
                child.setVisibility(View.VISIBLE);
                saveLastCameraBitmap();
                cell.updateBitmap();
                break;
            }
        }

        if (headerAnimator != null) {
            headerAnimator.cancel();
        }
        headerAnimator = dropDown.animate().alpha(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.EASE_BOTH).withEndAction(() -> dropDownContainer.setVisibility(GONE));
        headerAnimator.start();

        pauseCameraPreview();
    }

    private void pauseCameraPreview() {
        try {
            if (cameraView != null) {
                CameraController.getInstance().stopPreview(cameraView.getCameraSessionObject());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void resumeCameraPreview() {
        try {
            checkCamera(false);
            if (cameraView != null) {
                CameraController.getInstance().startPreview(cameraView.getCameraSessionObject());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void onPhotoEditModeChanged(boolean isEditMode) {
//        if (needCamera && !noCameraPermissions) {
//            if (isEditMode) {
//                if (cameraView != null) {
//                    isCameraFrontfaceBeforeEnteringEditMode = cameraView.isFrontface();
//                    hideCamera(true);
//                }
//            } else {
//                afterCameraInitRunnable = () -> {
//                    pauseCameraPreview();
//                    afterCameraInitRunnable = null;
//                    isCameraFrontfaceBeforeEnteringEditMode = null;
//                };
//                showCamera();
//            }
//        }
    }

    public void pauseCamera(boolean pause) {
        if (needCamera && !noCameraPermissions) {
            if (pause) {
                if (cameraView != null) {
                    isCameraFrontfaceBeforeEnteringEditMode = cameraView.isFrontface();
                    hideCamera(true);
                }
            } else {
//                afterCameraInitRunnable = () -> {
//                    pauseCameraPreview();
//                    afterCameraInitRunnable = null;
//                    isCameraFrontfaceBeforeEnteringEditMode = null;
//                };
                showCamera();
            }
        }
    }

    @Override
    public void onHidden() {
        if (collageLayoutView != null) {
            collageLayoutView.setVisibility(GONE);
            cameraIcon.setVisibility(GONE);
        }
        for (Map.Entry<Object, Object> en : selectedPhotos.entrySet()) {
            if (en.getValue() instanceof MediaController.PhotoEntry) {
                ((MediaController.PhotoEntry) en.getValue()).isAttachSpoilerRevealed = false;
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (lastNotifyWidth != right - left) {
            lastNotifyWidth = right - left;
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
        super.onLayout(changed, left, top, right, bottom);
        checkCameraViewPosition();
    }

    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        ignoreLayout = true;
        if (AndroidUtilities.isTablet()) {
            itemsPerRow = 4;
        } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            itemsPerRow = 4;
        } else {
            itemsPerRow = 3;
        }
        LayoutParams layoutParams = (LayoutParams) getLayoutParams();
        layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

        itemSize = (availableWidth - dp(6 * 2) - dp(5 * 2)) / itemsPerRow;

        if (lastItemSize != itemSize) {
            lastItemSize = itemSize;
            AndroidUtilities.runOnUIThread(() -> adapter.notifyDataSetChanged());
        }

        layoutManager.setSpanCount(Math.max(1, itemSize * itemsPerRow + dp(5) * (itemsPerRow - 1)));
        int rows = (int) Math.ceil((adapter.getItemCount() - 1) / (float) itemsPerRow);
        int contentSize = rows * itemSize + (rows - 1) * dp(5);
        int newSize = Math.max(0, availableHeight - contentSize - ActionBar.getCurrentActionBarHeight() - dp(48 + 12));
        if (gridExtraSpace != newSize) {
            gridExtraSpace = newSize;
            adapter.notifyDataSetChanged();
        }
        int paddingTop;
        if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            paddingTop = (int) (availableHeight / 3.5f);
        } else {
            paddingTop = (availableHeight / 5 * 2);
        }
        paddingTop -= dp(52);
        if (paddingTop < 0) {
            paddingTop = 0;
        }
        if (gridView.getPaddingTop() != paddingTop) {
            gridView.setPadding(dp(6), paddingTop, dp(6), dp(48));
        }
        dropDown.setTextSize(!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 18 : 20);
        ignoreLayout = false;
    }

    @Override
    public boolean canDismissWithTouchOutside() {
        return !cameraOpened;
    }

    @Override
    public void onPanTransitionStart(boolean keyboardVisible, int contentHeight) {
        super.onPanTransitionStart(keyboardVisible, contentHeight);
        checkCameraViewPosition();
        if (collageLayoutView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                collageLayoutView.invalidateOutline();
            } else {
                collageLayoutView.invalidate();
            }
        }
        if (cameraIcon != null) {
            cameraIcon.invalidate();
        }
    }

    @Override
    public void onContainerTranslationUpdated(float currentPanTranslationY) {
        this.currentPanTranslationY = currentPanTranslationY;
        checkCameraViewPosition();
        if (collageLayoutView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                collageLayoutView.invalidateOutline();
            } else {
                collageLayoutView.invalidate();
            }
        }
        if (cameraIcon != null) {
            cameraIcon.invalidate();
        }
        invalidate();
    }

    @Override
    public void onOpenAnimationEnd() {
        checkCamera(parentAlert != null && parentAlert.baseFragment instanceof ChatActivity);
    }

    @Override
    public void onDismissWithButtonClick(int item) {
        hideCamera(item != 0 && item != 2);
    }

    @Override
    public boolean onDismiss() {
        if (cameraAnimationInProgress) {
            return true;
        }
        if (cameraOpened) {
            closeCamera(true);
            return true;
        }
        hideCamera(true);
        return false;
    }

    @Override
    public boolean onContainerViewTouchEvent(MotionEvent event) {
        if (cameraAnimationInProgress) {
            return true;
        } else if (cameraOpened) {
            // TODO подумать про этот тач
            return processTouchEvent(event);
        }
        return false;
    }

    @Override
    public boolean onCustomMeasure(View view, int width, int height) {
        boolean isPortrait = width < height;
        if (view == cameraIcon) {
            cameraIcon.measure(View.MeasureSpec.makeMeasureSpec(itemSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((int) (itemSize - cameraViewOffsetBottomY - cameraViewOffsetY), View.MeasureSpec.EXACTLY));
            return true;
        } else if (view == collageLayoutView) {
            if (cameraOpened && !cameraAnimationInProgress) {
                collageLayoutView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height + parentAlert.getBottomInset(), View.MeasureSpec.EXACTLY));
                return true;
            }
        } else if (view == cameraControlContainer) {
            cameraControlContainer.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height + parentAlert.getBottomInset(), View.MeasureSpec.EXACTLY));
            return true;
        } else if (view == zoomControlView) {
            if (isPortrait) {
                zoomControlView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(50), View.MeasureSpec.EXACTLY));
            } else {
                zoomControlView.measure(View.MeasureSpec.makeMeasureSpec(dp(50), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            }
            return true;
        } else if (view == cameraPhotoRecyclerView) {
            cameraPhotoRecyclerViewIgnoreLayout = true;
            if (isPortrait) {
                cameraPhotoRecyclerView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(80), View.MeasureSpec.EXACTLY));
                if (cameraPhotoLayoutManager.getOrientation() != LinearLayoutManager.HORIZONTAL) {
                    cameraPhotoRecyclerView.setPadding(dp(8), 0, dp(8), 0);
                    cameraPhotoLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                    cameraAttachAdapter.notifyDataSetChanged();
                }
            } else {
                cameraPhotoRecyclerView.measure(View.MeasureSpec.makeMeasureSpec(dp(80), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                if (cameraPhotoLayoutManager.getOrientation() != LinearLayoutManager.VERTICAL) {
                    cameraPhotoRecyclerView.setPadding(0, dp(8), 0, dp(8));
                    cameraPhotoLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
                    cameraAttachAdapter.notifyDataSetChanged();
                }
            }
            cameraPhotoRecyclerViewIgnoreLayout = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
        int width = (right - left);
        int height = (bottom - top);
        boolean isPortrait = width < height;
        if (view == cameraControlContainer) {
            if (isPortrait) {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    cameraControlContainer.layout(0, 0, right, bottom - AndroidUtilities.dp(96));
                } else {
                    cameraControlContainer.layout(0, 0, right, bottom);
                }
            } else {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    cameraControlContainer.layout(0, 0, right, bottom - AndroidUtilities.dp(96));
                } else {
                    cameraControlContainer.layout(0, 0, right, bottom);
                }
            }
            return true;
        } else if (view == zoomControlView) {
            if (isPortrait) {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    zoomControlView.layout(0, bottom - dp(176 + 96 + 38 + 50), width, bottom - dp(176 + 96 + 38));
                } else {
                    zoomControlView.layout(0, bottom - dp(176 + 50), width, bottom - dp(176));
                }
            } else {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    zoomControlView.layout(right - dp(176 + 96 + 38 + 50), 0, right - dp(176 + 96 + 38), height);
                } else {
                    zoomControlView.layout(right - dp(176 + 50), 0, right - dp(176), height);
                }
            }
            return true;
        } else if (view == counterTextView) {
            int cx;
            int cy;
            if (isPortrait) {
                cx = (width - counterTextView.getMeasuredWidth()) / 2;
                cy = bottom - dp(163 + 16 + 38);
                counterTextView.setRotation(0);
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    cy -= dp(96);
                }
            } else {
                cx = right - dp(113 + 16 + 38);
                cy = height / 2 + counterTextView.getMeasuredWidth() / 2;
                counterTextView.setRotation(-90);
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    cx -= dp(96);
                }
            }
            counterTextView.layout(cx, cy, cx + counterTextView.getMeasuredWidth(), cy + counterTextView.getMeasuredHeight());
            return true;
        } else if (view == cameraPhotoRecyclerView) {
            if (isPortrait) {
                int cy = height - dp(88);
                view.layout(0, cy, view.getMeasuredWidth(), cy + view.getMeasuredHeight());
            } else {
                int cx = left + width - dp(88);
                view.layout(cx, 0, cx + view.getMeasuredWidth(), view.getMeasuredHeight());
            }
            return true;
        }
        return false;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.albumsDidLoad) {
            if (recordControl != null) {
                recordControl.updateGalleryImage(false);
            }
            if (adapter != null) {
                if (shouldLoadAllMedia()) {
                    galleryAlbumEntry = MediaController.allMediaAlbumEntry;
                } else {
                    galleryAlbumEntry = MediaController.allPhotosAlbumEntry;
                }
                if (selectedAlbumEntry == null || parentAlert != null && parentAlert.isStickerMode) {
                    selectedAlbumEntry = galleryAlbumEntry;
                } else if (shouldLoadAllMedia()) {
                    for (int a = 0; a < MediaController.allMediaAlbums.size(); a++) {
                        MediaController.AlbumEntry entry = MediaController.allMediaAlbums.get(a);
                        if (entry.bucketId == selectedAlbumEntry.bucketId && entry.videoOnly == selectedAlbumEntry.videoOnly) {
                            selectedAlbumEntry = entry;
                            break;
                        }
                    }
                }
                loading = false;
                progressView.showTextView();
                adapter.notifyDataSetChanged();
                cameraAttachAdapter.notifyDataSetChanged();
                if (!selectedPhotosOrder.isEmpty() && galleryAlbumEntry != null) {
                    for (int a = 0, N = selectedPhotosOrder.size(); a < N; a++) {
                        Integer imageId = (Integer) selectedPhotosOrder.get(a);
                        Object currentEntry = selectedPhotos.get(imageId);
                        MediaController.PhotoEntry entry = galleryAlbumEntry.photosByIds.get(imageId);
                        if (entry != null) {
                            if (currentEntry instanceof MediaController.PhotoEntry) {
                                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) currentEntry;
                                entry.copyFrom(photoEntry);
                            }
                            selectedPhotos.put(imageId, entry);
                        }
                    }
                }
                updateAlbumsDropDown();
            }
        } else if (id == NotificationCenter.cameraInitied) {
            checkCamera(false);
        }
    }

    private class PhotoAttachAdapter extends RecyclerListView.FastScrollAdapter {

        private Context mContext;
        private boolean needCamera;
        private ArrayList<RecyclerListView.Holder> viewsCache = new ArrayList<>(8);
        private int itemsCount;
        private int photosStartRow;
        private int photosEndRow;

        public PhotoAttachAdapter(Context context, boolean camera) {
            mContext = context;
            needCamera = camera;
        }

        public void createCache() {
            for (int a = 0; a < 8; a++) {
                viewsCache.add(createHolder());
            }
        }

        public RecyclerListView.Holder createHolder() {
            PhotoAttachPhotoCell cell = new PhotoAttachPhotoCell(mContext, resourcesProvider);
            if (Build.VERSION.SDK_INT >= 21 && this == adapter) {
                cell.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        PhotoAttachPhotoCell photoCell = (PhotoAttachPhotoCell) view;
                        if (photoCell.getTag() == null) {
                            return;
                        }
                        int position = (Integer) photoCell.getTag();
                        if (needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                            position++;
                        }
                        if (showAvatarConstructor) {
                            position++;
                        }
                        if (position == 0) {
                            int rad = dp(8 * parentAlert.cornerRadius);
                            outline.setRoundRect(0, 0, view.getMeasuredWidth() + rad, view.getMeasuredHeight() + rad, rad);
                        } else if (position == itemsPerRow - 1) {
                            int rad = dp(8 * parentAlert.cornerRadius);
                            outline.setRoundRect(-rad, 0, view.getMeasuredWidth(), view.getMeasuredHeight() + rad, rad);
                        } else {
                            outline.setRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                        }
                    }
                });
                cell.setClipToOutline(true);
            }
            cell.setDelegate(v -> {
                if (!mediaEnabled || parentAlert.avatarPicker != 0) {
                    return;
                }
                int index = (Integer) v.getTag();
                MediaController.PhotoEntry photoEntry = v.getPhotoEntry();
                if (checkSendMediaEnabled(photoEntry)) {
                    return;
                }
                if (selectedPhotos.size() + 1 > maxCount()) {
                    BulletinFactory.of(parentAlert.sizeNotifierFrameLayout, resourcesProvider).createErrorBulletin(AndroidUtilities.replaceTags(LocaleController.formatPluralString("BusinessRepliesToastLimit", parentAlert.baseFragment.getMessagesController().quickReplyMessagesLimit))).show();
                    return;
                }
                boolean added = !selectedPhotos.containsKey(photoEntry.imageId);
                if (added && parentAlert.maxSelectedPhotos >= 0 && selectedPhotos.size() >= parentAlert.maxSelectedPhotos) {
                    if (parentAlert.allowOrder && parentAlert.baseFragment instanceof ChatActivity) {
                        ChatActivity chatActivity = (ChatActivity) parentAlert.baseFragment;
                        TLRPC.Chat chat = chatActivity.getCurrentChat();
                        if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
                            if (alertOnlyOnce != 2) {
                                AlertsCreator.createSimpleAlert(getContext(), LocaleController.getString(R.string.Slowmode), LocaleController.getString(R.string.SlowmodeSelectSendError), resourcesProvider).show();
                                if (alertOnlyOnce == 1) {
                                    alertOnlyOnce = 2;
                                }
                            }
                        }
                    }
                    return;
                }
                int num = added ? selectedPhotosOrder.size() : -1;
                if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                    v.setChecked(num, added, true);
                } else {
                    v.setChecked(-1, added, true);
                }
                addToSelectedPhotos(photoEntry, index);
                int updateIndex = index;
                if (PhotoAttachAdapter.this == cameraAttachAdapter) {
                    if (adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                        updateIndex++;
                    }
                    adapter.notifyItemChanged(updateIndex);
                } else {
                    cameraAttachAdapter.notifyItemChanged(updateIndex);
                }
                parentAlert.updateCountButton(added ? 1 : 2);
                cell.setHasSpoiler(photoEntry.hasSpoiler);
                cell.setStarsPrice(photoEntry.starsAmount, selectedPhotos.size() > 1);
            });
            return new RecyclerListView.Holder(cell);
        }

        private MediaController.PhotoEntry getPhoto(int position) {
            if (needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                position--;
            }
            return getPhotoEntryAtPosition(position);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    if (needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                        position--;
                    }
                    if (showAvatarConstructor) {
                        position--;
                    }
                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) holder.itemView;
                    if (this == adapter) {
                        cell.setItemSize(itemSize);
                    } else {
                        cell.setIsVertical(cameraPhotoLayoutManager.getOrientation() == LinearLayoutManager.VERTICAL);
                    }
                    if (parentAlert.avatarPicker != 0 || parentAlert.storyMediaPicker) {
                        cell.getCheckBox().setVisibility(GONE);
                    } else {
                        cell.getCheckBox().setVisibility(VISIBLE);
                    }

                    MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(position);
                    if (photoEntry == null) {
                        return;
                    }
                    cell.setPhotoEntry(photoEntry, selectedPhotos.size() > 1, needCamera && selectedAlbumEntry == galleryAlbumEntry, position == getItemCount() - 1);
                    if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                        cell.setChecked(selectedPhotosOrder.indexOf(photoEntry.imageId), selectedPhotos.containsKey(photoEntry.imageId), false);
                    } else {
                        cell.setChecked(-1, selectedPhotos.containsKey(photoEntry.imageId), false);
                    }
                    if (!videoEnabled && photoEntry.isVideo) {
                        cell.setAlpha(0.3f);
                    } else if (!photoEnabled && !photoEntry.isVideo) {
                        cell.setAlpha(0.3f);
                    } else {
                        cell.setAlpha(1f);
                    }
                    cell.getImageView().setTag(position);
                    cell.setTag(position);
                    break;
                }
                case 1: {
                    cameraCell = (PhotoAttachCameraCell) holder.itemView;
                    if (cameraView != null && cameraView.isInited() && !isHidden) {
                        cameraCell.setVisibility(View.INVISIBLE);
                    } else {
                        cameraCell.setVisibility(View.VISIBLE);
                    }
                    cameraCell.setItemSize(itemSize);
                    break;
                }
                case 3: {
                    PhotoAttachPermissionCell cell = (PhotoAttachPermissionCell) holder.itemView;
                    cell.setItemSize(itemSize);
                    cell.setType(needCamera && noCameraPermissions && position == 0 ? 0 : 1);
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            RecyclerListView.Holder holder;
            switch (viewType) {
                case 0:
                    if (!viewsCache.isEmpty()) {
                        holder = viewsCache.get(0);
                        viewsCache.remove(0);
                    } else {
                        holder = createHolder();
                    }
                    break;
                case 1:
                    cameraCell = new PhotoAttachCameraCell(mContext, resourcesProvider);
                    if (Build.VERSION.SDK_INT >= 21) {
                        cameraCell.setOutlineProvider(new ViewOutlineProvider() {
                            @Override
                            public void getOutline(View view, Outline outline) {
                                int rad = dp(8 * parentAlert.cornerRadius);
                                outline.setRoundRect(0, 0, view.getMeasuredWidth() + rad, view.getMeasuredHeight() + rad, rad);
                            }
                        });
                        cameraCell.setClipToOutline(true);
                    }
                    holder = new RecyclerListView.Holder(cameraCell);
                    break;
                case 2:
                    holder = new RecyclerListView.Holder(new View(mContext) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(gridExtraSpace, MeasureSpec.EXACTLY));
                        }
                    });
                    break;
                case 3:
                default:
                    holder = new RecyclerListView.Holder(new PhotoAttachPermissionCell(mContext, resourcesProvider));
                    break;
                case 4:
                    AvatarConstructorPreviewCell avatarConstructorPreviewCell = new AvatarConstructorPreviewCell(mContext, parentAlert.forUser) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY));
                        }
                    };
                    holder = new RecyclerListView.Holder(avatarConstructorPreviewCell);
                    break;
            }
            return holder;
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof PhotoAttachCameraCell) {
                PhotoAttachCameraCell cell = (PhotoAttachCameraCell) holder.itemView;
                cell.updateBitmap();
            }
        }

        @Override
        public int getItemCount() {
            if (!mediaEnabled) {
                return 1;
            }
            int count = 0;
            if (needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                count++;
            }
            if (showAvatarConstructor) {
                count++;
            }
            if (noGalleryPermissions && this == adapter) {
                count++;
            }
            photosStartRow = count;
            count += cameraPhotos.size();
            if (selectedAlbumEntry != null) {
                count += selectedAlbumEntry.photos.size();
            }
            photosEndRow = count;
            if (this == adapter) {
                count++;
            }
            return itemsCount = count;
        }

        @Override
        public int getItemViewType(int position) {
            if (!mediaEnabled) {
                return 2;
            }
            int localPosition = position;
            if (needCamera && position == 0 && selectedAlbumEntry == galleryAlbumEntry) {
                if (noCameraPermissions) {
                    return 3;
                } else {
                    return 1;
                }
            }
            if (needCamera) {
                localPosition--;
            }
            if (showAvatarConstructor && localPosition == 0) {
                return VIEW_TYPE_AVATAR_CONSTRUCTOR;
            }
            if (this == adapter && position == itemsCount - 1) {
                return 2;
            } else if (noGalleryPermissions) {
                return 3;
            }
            return 0;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            if (this == adapter) {
                progressView.setVisibility(getItemCount() == 1 && selectedAlbumEntry == null || !mediaEnabled ? View.VISIBLE : View.INVISIBLE);
            }
        }

        @Override
        public float getScrollProgress(RecyclerListView listView) {
            int parentCount = itemsPerRow;
            int cellCount = (int) Math.ceil(itemsCount / (float) parentCount);
            if (listView.getChildCount() == 0) {
                return 0;
            }
            int cellHeight = listView.getChildAt(0).getMeasuredHeight();
            View firstChild = listView.getChildAt(0);
            int firstPosition = listView.getChildAdapterPosition(firstChild);
            if (firstPosition < 0) {
                return 0;
            }
            float childTop = firstChild.getTop();
            float listH = listView.getMeasuredHeight();
            float scrollY = (firstPosition / parentCount) * cellHeight - childTop;
            return Utilities.clamp(scrollY / (((float) cellCount) * cellHeight - listH), 1f, 0f);
        }

        @Override
        public String getLetter(int position) {
            MediaController.PhotoEntry entry = getPhoto(position);
            if (entry == null) {
                if (position <= photosStartRow) {
                    if (!cameraPhotos.isEmpty()) {
                        entry = (MediaController.PhotoEntry) cameraPhotos.get(0);
                    } else if (selectedAlbumEntry != null && selectedAlbumEntry.photos != null) {
                        entry = selectedAlbumEntry.photos.get(0);
                    }
                } else if (!selectedAlbumEntry.photos.isEmpty()) {
                    entry = selectedAlbumEntry.photos.get(selectedAlbumEntry.photos.size() - 1);
                }
            }
            if (entry != null) {
                long date = entry.dateTaken;
                if (Build.VERSION.SDK_INT <= 28) {
                    date /= 1000;
                }
                return LocaleController.formatYearMont(date, true);
            }
            return "";
        }

        @Override
        public boolean fastScrollIsVisible(RecyclerListView listView) {
            return (!cameraPhotos.isEmpty() || selectedAlbumEntry != null && !selectedAlbumEntry.photos.isEmpty()) && parentAlert.pinnedToTop && getTotalItemsCount() > SHOW_FAST_SCROLL_MIN_COUNT;
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            int viewHeight = listView.getChildAt(0).getMeasuredHeight();
            int totalHeight = (int) (Math.ceil(getTotalItemsCount() / (float) itemsPerRow) * viewHeight);
            int listHeight = listView.getMeasuredHeight();
            position[0] = (int) ((progress * (totalHeight - listHeight)) / viewHeight) * itemsPerRow;
            position[1] = (int) ((progress * (totalHeight - listHeight)) % viewHeight) + listView.getPaddingTop();
            if (position[0] == 0 && position[1] < getListTopPadding()) {
                position[1] = getListTopPadding();
            }
        }
    }
}
