package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import com.google.android.exoplayer2.util.Log;
import com.google.android.gms.cast.framework.CastContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import androidx.media3.cast.CastPlayer;
import androidx.media3.cast.SessionAvailabilityListener;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import fi.iki.elonen.NanoHTTPD;

@UnstableApi
public class CastVideoPlayer implements SessionAvailabilityListener {

    @SuppressLint("StaticFieldLeak")
    private static volatile CastVideoPlayer Instance = null;

    public static CastVideoPlayer getInstance() {
        CastVideoPlayer localInstance = Instance;
        if (localInstance == null) {
            synchronized (CastVideoPlayer.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new CastVideoPlayer();
                }
            }
        }
        return localInstance;
    }

    private CastContext castContext;
    private CastPlayer castPlayer;
    private ArrayList<Uri> mediaUris = new ArrayList<>();
    private ArrayList<MediaItem> mediaQueue = new ArrayList<>();
    private Context appContext;
    private SimpleHttpServer httpServer;
    private ArrayList<VideoPlayer.Quality> videoQualities;

    public void initPlayer(Context context) {
        this.appContext = context.getApplicationContext();
        CastContext.getSharedInstance(context, Executors.newSingleThreadExecutor())
                .addOnSuccessListener(castContext -> {
                    this.castContext = castContext;
                });
    }

    public boolean isCastAvailable() {
        return castContext != null && castPlayer != null && castPlayer.isCastSessionAvailable();
    }

    public void createPlayer() {
        if (castPlayer != null) {
            return;
        }
        try {
            castPlayer = new CastPlayer(castContext);
            castPlayer.setSessionAvailabilityListener(this);
        } catch (RuntimeException e) {
            Log.d("TAGLOG", "CastPlayer error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void releasePlayer() {
        if (castPlayer != null) {
            castPlayer.setSessionAvailabilityListener(null);
            castPlayer.release();
            castPlayer = null;
        }
        mediaUris.clear();
        mediaQueue.clear();
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }

    public void setPlayerMedia(Uri uri, String type) {
        if (uri == null) {
            return;
        }
        mediaQueue.clear();
        mediaUris.clear();

        addMediaItemUri(uri, type);
    }

    public void setPlayerMedia(List<Uri> uris, String type) {
        if (uris == null) {
            return;
        }
        mediaQueue.clear();
        mediaUris.clear();

        for (Uri uri : uris) {
            addMediaItemUri(uri, type);
        }
    }

    private void addMediaItemUri(Uri uri, String type) {
        mediaUris.add(uri);

        String url = getUriServerUrl(uri);
        MediaItem item = createMediaItem(Uri.parse(url), type);
        mediaQueue.add(item);
        castPlayer.addMediaItem(item);

        Log.d("TAGLOG", "addMediaItemUri: " + url);
    }

    private MediaItem createMediaItem(Uri uri, String type) {
        String mimeTypes;
        switch (type) {
            case "dash":
                mimeTypes = MimeTypes.APPLICATION_MPD;
                break;
            case "hls":
                mimeTypes = MimeTypes.APPLICATION_M3U8;
                break;
            case "ss":
                mimeTypes = MimeTypes.APPLICATION_SS;
                break;
            default:
                mimeTypes = MimeTypes.VIDEO_MP4;
                break;
        }
        return new androidx.media3.common.MediaItem.Builder()
                .setUri(uri)
                .setMimeType(mimeTypes)
                .build();
    }

    private void startHttpServer() {
        stopHttpServer();
        httpServer = new SimpleHttpServer(8080, mediaUris, appContext);
        try {
            httpServer.start();
            Log.d("TAGLOG", "HTTP server started for uris " + TextUtils.join(", ", mediaUris));
        } catch (IOException e) {
            Log.d("TAGLOG", "Failed to start HTTP server: " + e.getMessage());
        }
    }

    private void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }

    private String getUriServerUrl(Uri uri) {
        String localIpAddress = getDeviceIpAddress();
        return "http://" + localIpAddress + ":8080/" + getUriName(uri);
    }

    private String getUriName(Uri uri) {
        if ("tg".equals(uri.getScheme())) {
            return uri.getLastPathSegment();
        }
        File file = new File(uri.getPath());
        if (file.exists()) {
            return file.getName();
        }
        return uri.getHost();
    }

    private String getDeviceIpAddress() {
        WifiManager wm = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        return Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
    }

    private static String getMimeTypeFromUri(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (extension != null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
        }
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private static class SimpleHttpServer extends NanoHTTPD {
        private final List<Uri> uris;
        private final Context context;

        public SimpleHttpServer(int port, List<Uri> uris, Context context) {
            super(port);
            this.uris = uris;
            this.context = context;
        }

        @Override
        public Response serve(NanoHTTPD.IHTTPSession session) {
            try {
                String uriPath = session.getUri().substring(1); // Remove leading slash
                Uri targetUri = null;
                for (Uri uri : uris) {
                    if (uri.getLastPathSegment() != null && uri.getLastPathSegment().equals(uriPath)) {
                        targetUri = uri;
                        break;
                    }
                }
                if (targetUri != null) {
                    InputStream inputStream = context.getContentResolver().openInputStream(targetUri);
                    if (inputStream != null) {
                        String mimeType = getMimeTypeFromUri(context, targetUri);
                        return newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, inputStream.available());
                    }
                }
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found");
            } catch (IOException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Internal server error");
            }
        }
    }

    @Override
    public void onCastSessionAvailable() {
        startHttpServer();
        castPlayer.setMediaItems(mediaQueue, true);
        castPlayer.setPlayWhenReady(true);
        castPlayer.prepare();
    }

    @Override
    public void onCastSessionUnavailable() {
        castPlayer.stop();
        stopHttpServer();
    }
}
