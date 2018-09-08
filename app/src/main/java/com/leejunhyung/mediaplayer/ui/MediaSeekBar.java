package com.leejunhyung.mediaplayer.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.widget.AppCompatSeekBar;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.LinearInterpolator;
import android.widget.SeekBar;

public class MediaSeekBar extends AppCompatSeekBar {
    private MediaControllerCompat mMediaController;
    private ControllerCallback mControllerCallback;

    private boolean mIsTracking = false;
    private ValueAnimator mProgressAnimator;
    private OnSeekBarChangeListener mOnSeekBarChangeListener = new OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mIsTracking = true;
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mMediaController.getTransportControls().seekTo(getProgress());
            mIsTracking = false;
        }
    };

    public MediaSeekBar(Context context) {
        super(context);
        super.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
    }

    public MediaSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        super.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
    }

    public MediaSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
    }

    @Override
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
        throw new UnsupportedOperationException("Cannot add listeners to a MediaSeekBar");
    }

    public void setMediaController(final MediaControllerCompat mediaController) {
        if (mediaController != null) {
            mControllerCallback = new ControllerCallback();
            mediaController.registerCallback(mControllerCallback);
        } else if (mMediaController != null) {
            mMediaController.unregisterCallback(mControllerCallback);
            mControllerCallback = null;
        }

        mMediaController = mediaController;
    }

    public void disconnectController() {
        if (mMediaController != null) {
            mMediaController.unregisterCallback(mControllerCallback);
            mControllerCallback = null;
            mMediaController = null;
        }
    }

    private class ControllerCallback
            extends MediaControllerCompat.Callback
        implements ValueAnimator.AnimatorUpdateListener {

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);

            if (mProgressAnimator != null) {
                mProgressAnimator.cancel();
                mProgressAnimator = null;
            }

            final int progress = state != null ?
                    (int) state.getPosition() : 0;

            setProgress(progress);

            if (state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                final int timeToEnd = (int) ((getMax() - progress >= 0 ? getMax() - progress : 0) / state.getPlaybackSpeed());

                mProgressAnimator = ValueAnimator.ofInt(progress, getMax())
                        .setDuration(timeToEnd);
                mProgressAnimator.setInterpolator(new LinearInterpolator());
                mProgressAnimator.addUpdateListener(this);
                mProgressAnimator.start();
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);

            final int max = metadata != null ?
                    (int) metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) : 0;

            setProgress(0);
            setMax(max);
        }

        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            if (mIsTracking) {
                valueAnimator.cancel();
                return;
            }

            final int animatedIntValue = (int) valueAnimator.getAnimatedValue();
            setProgress(animatedIntValue);
        }
    }

}
