package com.leejunhyung.mediaplayer.service;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.leejunhyung.mediaplayer.service.player.MediaPlayerAdapter;
import com.leejunhyung.utils.RepeatType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MediaPlaybackService extends MediaBrowserServiceCompat {
    private static final String MY_MEDIA_ROOT_ID = "media_root_id";

    private MediaSessionCompat mMediaSession;
    private MediaPlayerAdapter mPlayer;
    private MediaNotificationManager mMediaNotificationManager;
    private boolean mServiceInStartedState;

    private final List<MediaSessionCompat.QueueItem> mPlaylist = new ArrayList<>();
    private MediaMetadataCompat mPreparedMedia;
    private int mQueueIndex = -1;
    private boolean isReadyToPlay() {
        return (!mPlaylist.isEmpty());
    }

    private RepeatType repeatType = RepeatType.NONE;

    @Override
    public void onCreate() {
        super.onCreate();

        mMediaSession = new MediaSessionCompat(this, MediaPlaybackService.class.getSimpleName());

        // Enable callbacks from MediaButtons and TransportControls
        mMediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        MediaSessionCompat.Callback mMediaSessionCallback;

        mMediaSessionCallback = new MediaSessionCallback();
        mMediaSession.setCallback(mMediaSessionCallback);

        // Set the session's token so that client activities can communicate with it.
        setSessionToken(mMediaSession.getSessionToken());

        mMediaNotificationManager = new MediaNotificationManager(this);

        mPlayer = new MediaPlayerAdapter(this, new MediaPlayerListener());
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPlayer.stop();
        mMediaSession.release();
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String s, int i, @Nullable Bundle bundle) {
        return new BrowserRoot(MY_MEDIA_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(@NonNull String s, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(MusicLibrary.getMediaItems());
    }

    private void prepare() {
        if (mQueueIndex < 0 && mPlaylist.isEmpty()) {
            return;
        }

        final String mediaId = mPlaylist.get(mQueueIndex).getDescription().getMediaId();
        mPreparedMedia = MusicLibrary.getMetadata(mediaId);
        mMediaSession.setMetadata(mPreparedMedia);

        if (!mMediaSession.isActive()) {
            mMediaSession.setActive(true);
        }
    }

    private void play() {
        if (!isReadyToPlay()) {
            return;
        }

        if (mPreparedMedia == null) {
            prepare();
        }

        mPlayer.playFromMedia(mPreparedMedia);
    }

    private void skipToNext() {
        mQueueIndex = (++mQueueIndex % mPlaylist.size());
        mPreparedMedia = null;
        play();
    }

    public class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            mPlaylist.add(new MediaSessionCompat.QueueItem(description, description.hashCode()));
            mQueueIndex = (mQueueIndex == -1) ? 0 : mQueueIndex;
            mMediaSession.setQueue(mPlaylist);
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            mPlaylist.remove(new MediaSessionCompat.QueueItem(description, description.hashCode()));
            mQueueIndex = (mPlaylist.isEmpty()) ? -1 : mQueueIndex;
            mMediaSession.setQueue(mPlaylist);
        }

        @Override
        public void onSetShuffleMode(int shuffleMode) {
            mQueueIndex = new Random().nextInt(mPlaylist.size());
            mPreparedMedia = null;
            onPlay();
        }

        @Override
        public void onSetRepeatMode(int repeatMode) {
            if (repeatType == RepeatType.NONE) {
                repeatType = RepeatType.ONE_SONG;
            } else if (repeatType == RepeatType.ONE_SONG) {
                repeatType = RepeatType.PLAY_LIST;
            } else {
                repeatType = RepeatType.NONE;
            }
        }

        @Override
        public void onPrepare() {
            prepare();
        }

        @Override
        public void onPlay() {
            play();
        }

        @Override
        public void onPause() {
            mPlayer.pause();
        }

        @Override
        public void onStop() {
            mPlayer.stop();
            mMediaSession.setActive(false);
        }


        @Override
        public void onSkipToNext() {
            skipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            mQueueIndex = mQueueIndex > 0 ? mQueueIndex - 1 : mPlaylist.size() - 1;
            mPreparedMedia = null;
            onPlay();
        }

        @Override
        public void onSeekTo(long pos) {
            mPlayer.seekTo(pos);
        }
    }

    public class MediaPlayerListener extends PlaybackInfoListener {
        private final ServiceManager mServiceManager;

        private MediaPlayerListener() {
            this.mServiceManager = new ServiceManager();
        }

        @Override
        public void onPlaybackStateChange(PlaybackStateCompat state) {
            boolean songEnded = state.getState() == PlaybackStateCompat.STATE_STOPPED;
            boolean endOfPlaylist = mQueueIndex == mPlaylist.size() - 1;

            if (songEnded) {
                switch (repeatType) {
                    case ONE_SONG:
                        mPreparedMedia = null;
                        play();
                        return;

                    case PLAY_LIST:
                        skipToNext();
                        return;

                    default:
                        if (!endOfPlaylist) {
                            skipToNext();
                            return;
                        }
                }
            }

            mMediaSession.setPlaybackState(state);

            switch (state.getState()) {
                case PlaybackStateCompat.STATE_PLAYING:
                    mServiceManager.moveServiceToStartedState(state);
                    break;
                case PlaybackStateCompat.STATE_PAUSED:
                    mServiceManager.updateNotificationForPause(state);
                    break;
                case PlaybackStateCompat.STATE_STOPPED:
                    mServiceManager.moveServiceOutOfStartedState(state);
                    break;
            }
        }

        class ServiceManager {
            private void moveServiceToStartedState(PlaybackStateCompat state) {
                Notification notification =
                        mMediaNotificationManager.getNotification(
                                mPlayer.getCurrentMedia(), state, getSessionToken()
                        );

                if (!mServiceInStartedState) {
                    ContextCompat.startForegroundService(
                            MediaPlaybackService.this,
                            new Intent(MediaPlaybackService.this, MediaPlaybackService.class)
                    );

                     mServiceInStartedState = true;
                }

                startForeground(MediaNotificationManager.NOTIFICATION_ID, notification);
            }

            private void updateNotificationForPause(PlaybackStateCompat state) {
                stopForeground(false);
                Notification notification =
                        mMediaNotificationManager.getNotification(
                                mPlayer.getCurrentMedia(), state, getSessionToken()
                        );

                mMediaNotificationManager.getNotificationManager()
                        .notify(MediaNotificationManager.NOTIFICATION_ID, notification);
            }

            private void moveServiceOutOfStartedState(PlaybackStateCompat state) {
                stopForeground(true);
                stopSelf();
                mServiceInStartedState = false;
            }
        }
    }
}
