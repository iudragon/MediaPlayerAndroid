package com.leejunhyung.mediaplayer.service.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.leejunhyung.mediaplayer.client.MediaBrowserClient;
import com.leejunhyung.mediaplayer.service.MusicLibrary;
import com.leejunhyung.mediaplayer.service.PlaybackInfoListener;

import java.io.FileNotFoundException;
import java.io.IOException;

public class MediaPlayerAdapter extends BasePlayer {
    private final Context mContext;
    private MediaPlayer mMediaPlayer;
    private PlaybackInfoListener mPlaybackInfoListener;

    private MediaMetadataCompat mCurrentMedia;
    private String mFileName;
    private int mState;
    private boolean mCurrentMediaPlayedToCompletion;
    private int mSeekWhileNotPlaying = -1;

    public MediaPlayerAdapter(Context context, PlaybackInfoListener listener) {
        super(context);
        this.mContext = context.getApplicationContext();
        this.mPlaybackInfoListener = listener;
    }

    private void initialize() {
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnCompletionListener(
                    new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mediaPlayer) {
                            mPlaybackInfoListener.onPlaybackCompleted();
                            setNewState(PlaybackStateCompat.STATE_STOPPED);
                        }
                    }
            );
        }
    }

    @Override
    public void playFromMedia(MediaMetadataCompat metadata) {
        mCurrentMedia = metadata;
        final String mediaId = metadata.getDescription().getMediaId();
        playFile(MusicLibrary.getMusicFilename(mediaId));
    }

    @Override
    public MediaMetadataCompat getCurrentMedia() {
        return mCurrentMedia;
    }

    private void playFile(String fileName) {
        boolean mediaChanged = mFileName == null || !fileName.equals(mFileName);

        if (mCurrentMediaPlayedToCompletion) {
            mediaChanged = true;
            mCurrentMediaPlayedToCompletion = false;
        }

        if (!mediaChanged) {
            if (!isPlaying()) {
                play();
            }
            return;
        } else {
            release();
        }

        mFileName = fileName;

        initialize();

        try {

            AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(
                mContext.getResources().getIdentifier(
                        fileName.replace(".mp3", ""),
                        "raw",
                        mContext.getPackageName()
                )
            );

            mMediaPlayer.setDataSource(
                    afd.getFileDescriptor(),
                    afd.getStartOffset(),
                    afd.getLength()
            );

            mMediaPlayer.prepare();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("failed to find file " + mFileName, e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open file: " + mFileName, e);
        }

        play();
    }

    @Override
    protected void onStop() {
        setNewState(PlaybackStateCompat.STATE_STOPPED);
        release();
    }

    private void release() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    public boolean isPlaying() {
        return mMediaPlayer != null && mMediaPlayer.isPlaying();
    }

    @Override
    protected void onPlay() {
        if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
            setNewState(PlaybackStateCompat.STATE_PLAYING);
        }
    }

    @Override
    protected void onPause() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            setNewState(PlaybackStateCompat.STATE_PAUSED);
        }
    }

    private void setNewState(@PlaybackStateCompat.State int newPlayerState) {
        mState = newPlayerState;

        if (mState == PlaybackStateCompat.STATE_STOPPED) {
            mCurrentMediaPlayedToCompletion = true;
        }

        final long reportPosition;
        if (mSeekWhileNotPlaying >= 0) {
            reportPosition = mSeekWhileNotPlaying;

            if (mState == PlaybackStateCompat.STATE_PLAYING) {
                mSeekWhileNotPlaying = -1;
            }
        } else {
            reportPosition = mMediaPlayer == null ? 0 : mMediaPlayer.getCurrentPosition();
        }

        final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
        stateBuilder.setActions(getAvailableActions());
        stateBuilder.setState(mState,
                reportPosition,
                1.0f,
                SystemClock.elapsedRealtime());
        mPlaybackInfoListener.onPlaybackStateChange(stateBuilder.build());
    }

    @PlaybackStateCompat.Actions
    private long getAvailableActions() {
        long actions = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        switch (mState) {
            case PlaybackStateCompat.STATE_STOPPED:
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE;
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                actions |= PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_SEEK_TO;
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_STOP;
                break;
            default:
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_PAUSE;
        }

        return actions;
    }

    @Override
    public void seekTo(long position) {
        if (mMediaPlayer != null) {
            if (!mMediaPlayer.isPlaying()) {
                mSeekWhileNotPlaying = (int) position;
            }

            mMediaPlayer.seekTo((int) position);
            setNewState(mState);
        }
    }

    @Override
    public void setVolume(float volume) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume(volume, volume);
        }
    }
}
