package com.leejunhyung.mediaplayer.client;

import android.content.ComponentName;
import android.content.Context;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MediaBrowserClient {
    private final String TAG = "app";

    private final Context mContext;

    // wildcard expression reads as "any class that extends MediaBrowserServiceCompat" can come
    private final Class<? extends MediaBrowserServiceCompat> mMediaBrowserServiceClass;

    private final List<MediaControllerCompat.Callback> mCallbackList = new ArrayList<>();

    private final MediaBrowserConnectionCallback mMediaBrowserConnectionCallback;
    private final MediaControllerCallback mMediaControllerCallback;
    private final MediaBrowserSubscriptionCallback mMediaBrowserSubscriptionCallback;

    private MediaBrowserCompat mMediaBrowser;

    @Nullable
    private MediaControllerCompat mMediaController;

    public MediaBrowserClient(Context mContext, Class<? extends MediaBrowserServiceCompat> mMediaBrowserServiceClass) {
        this.mContext = mContext;
        this.mMediaBrowserServiceClass = mMediaBrowserServiceClass;

        mMediaBrowserConnectionCallback = new MediaBrowserConnectionCallback();
        mMediaBrowserSubscriptionCallback = new MediaBrowserSubscriptionCallback();
        mMediaControllerCallback = new MediaControllerCallback();
    }

    public void onStart() {
        if (mMediaBrowser == null) {
            mMediaBrowser = new MediaBrowserCompat(
                    mContext,
                    new ComponentName(mContext, mMediaBrowserServiceClass),
                    mMediaBrowserConnectionCallback,
                    null
            );

            mMediaBrowser.connect();
        }
    }

    public void onStop() {
        if (mMediaController != null) {
            mMediaController.unregisterCallback(mMediaControllerCallback);
            mMediaController = null;
        }

        if (mMediaBrowser != null && mMediaBrowser.isConnected()) {
            mMediaBrowser.disconnect();
            mMediaBrowser = null;
        }

        resetState();
    }

    protected void onConnected(@NonNull MediaControllerCompat mediaController) {
    }

    protected void onChildrenLoaded(@NonNull String parentId,
                                    @NonNull List<MediaBrowserCompat.MediaItem> children) {
    }

    protected void onDisconnected() {}

    @NonNull
    protected final MediaControllerCompat getMediaController() {
        if (mMediaController == null) {
            throw new IllegalStateException("MediaController is null!");
        }
        return mMediaController;
    }

    public MediaControllerCompat.TransportControls getTransportControls() {
        if (mMediaController == null) {
            throw new IllegalStateException("MediaController is null!");
        }
        return mMediaController.getTransportControls();
    }

    public void registerCallback(MediaControllerCompat.Callback callback) {
        if (callback != null) {
            mCallbackList.add(callback);

            if (mMediaController != null) {
                final MediaMetadataCompat metadata = mMediaController.getMetadata();

                if (metadata != null) {
                    callback.onMetadataChanged(metadata);
                }

                final PlaybackStateCompat playbackState = mMediaController.getPlaybackState();

                if (playbackState != null) {
                    callback.onPlaybackStateChanged(playbackState);
                }
            }
        }
    }

    private void resetState() {
        performOnAllCallbacks(new CallbackCommand() {
            @Override
            public void perform(@NonNull MediaControllerCompat.Callback callback) {
                callback.onPlaybackStateChanged(null);
            }
        });
    }

    private void performOnAllCallbacks(@NonNull CallbackCommand command) {
        for (MediaControllerCompat.Callback callback : mCallbackList) {
            if (callback != null) {
                command.perform(callback);
            }
        }
    }


    private interface CallbackCommand {
        void perform(@NonNull MediaControllerCompat.Callback callback);
    }

    private class MediaBrowserConnectionCallback extends MediaBrowserCompat.ConnectionCallback {
        @Override
        public void onConnected() {
            try {
                mMediaController = new MediaControllerCompat(mContext, mMediaBrowser.getSessionToken());
                mMediaController.registerCallback(mMediaControllerCallback);

                // sync
                mMediaControllerCallback.onMetadataChanged(mMediaController.getMetadata());
                mMediaControllerCallback.onPlaybackStateChanged(mMediaController.getPlaybackState());

                MediaBrowserClient.this.onConnected(mMediaController);
            } catch (RemoteException e) {
                Log.d(TAG, String.format("onConnected: Problem: %s", e.toString()));
                throw new RuntimeException(e);
            }

            mMediaBrowser.subscribe(mMediaBrowser.getRoot(), mMediaBrowserSubscriptionCallback);
        }
    }

    public class MediaBrowserSubscriptionCallback extends MediaBrowserCompat.SubscriptionCallback {
        @Override
        public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children) {
            MediaBrowserClient.this.onChildrenLoaded(parentId, children);
        }
    }

    private class MediaControllerCallback extends MediaControllerCompat.Callback {
        @Override
        public void onMetadataChanged(final MediaMetadataCompat metadata) {
            performOnAllCallbacks(new CallbackCommand() {
                @Override
                public void perform(@NonNull MediaControllerCompat.Callback callback) {
                    callback.onMetadataChanged(metadata);
                }
            });
        }

        @Override
        public void onPlaybackStateChanged(@Nullable final PlaybackStateCompat state) {
            performOnAllCallbacks(new CallbackCommand() {
                @Override
                public void perform(@NonNull MediaControllerCompat.Callback callback) {
                    callback.onPlaybackStateChanged(state);
                }
            });
        }

        @Override
        public void onSessionDestroyed() {
            resetState();
            onPlaybackStateChanged(null);

            MediaBrowserClient.this.onDisconnected();
        }
    }
}
