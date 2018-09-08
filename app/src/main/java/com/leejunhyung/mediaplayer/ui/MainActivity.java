package com.leejunhyung.mediaplayer.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompatUtils;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.RequestOptions;
import com.leejunhyung.mediaplayer.R;
import com.leejunhyung.mediaplayer.service.MediaPlaybackService;
import com.leejunhyung.mediaplayer.client.MediaBrowserClient;
import com.leejunhyung.utils.GlideApp;
import com.leejunhyung.utils.RepeatType;
import com.leejunhyung.utils.TextCaseUtils;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import de.hdodenhof.circleimageview.CircleImageView;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;
import jp.wasabeef.glide.transformations.BlurTransformation;
import jp.wasabeef.glide.transformations.ColorFilterTransformation;

public class MainActivity extends AppCompatActivity {
    private ToggleButton mPlayPause;
    private ImageView mShuffle;
    private ImageView mRepeat;
    private ImageView mMainBackground;
    private CircleImageView mAlbumArt;
    private TextView mTitle;
    private TextView mArtist;
    private TextView mElapsedTime;
    private TextView mDuration;
    private MediaSeekBar mSeekBarAudio;

    private MediaBrowserClient mMediaBrowserClient;

    private boolean mIsPlaying;
    private RepeatType repeatType = RepeatType.NONE;
    private final CompositeDisposable mDurationUpdater = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ImageView mPlayNextSong;
        final ImageView mPlayPrevSong;

        mPlayPause = findViewById(R.id.play_pause);
        mShuffle = findViewById(R.id.btn_shuffle);
        mRepeat = findViewById(R.id.btn_repeat);
        mPlayNextSong = findViewById(R.id.btn_next_song);
        mPlayPrevSong = findViewById(R.id.btn_prev_song);
        mMainBackground = findViewById(R.id.img_background);
        mAlbumArt = findViewById(R.id.img_album_art);
        mTitle = findViewById(R.id.tv_title);
        mArtist = findViewById(R.id.tv_artist);
        mElapsedTime = findViewById(R.id.tv_elapsed_time);
        mDuration = findViewById(R.id.tv_duration);
        mSeekBarAudio = findViewById(R.id.seekbar_audio);

        final ClickListener clickListener = new ClickListener();
        mPlayPause.setOnClickListener(clickListener);
        mPlayNextSong.setOnClickListener(clickListener);
        mPlayPrevSong.setOnClickListener(clickListener);
        mShuffle.setOnClickListener(clickListener);
        mRepeat.setOnClickListener(clickListener);

        mMediaBrowserClient = new MediaBrowserConnection(this);
        mMediaBrowserClient.registerCallback(new MediaBrowserObserver());

        updateElapsedTime();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mMediaBrowserClient.onStart();
        updateElapsedTime();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mDurationUpdater.clear();
        mSeekBarAudio.disconnectController();
        mMediaBrowserClient.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDurationUpdater.dispose();
    }

    private class ClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.btn_next_song:
                    mMediaBrowserClient.getTransportControls().skipToNext();
                    break;
                case R.id.btn_prev_song:
                    mMediaBrowserClient.getTransportControls().skipToPrevious();
                    break;
                case R.id.play_pause:
                    if (mIsPlaying) {
                        mMediaBrowserClient.getTransportControls().pause();
                    } else {
                        mMediaBrowserClient.getTransportControls().play();
                    }
                    break;
                case R.id.btn_shuffle:
                    mMediaBrowserClient.getTransportControls().setShuffleMode(
                            PlaybackStateCompat.SHUFFLE_MODE_ALL);

                    break;
                case R.id.btn_repeat:
                    if (repeatType == RepeatType.NONE) {
                        repeatType = RepeatType.ONE_SONG;
                        mRepeat.setColorFilter(Color.WHITE);
                        mRepeat.setImageResource(R.drawable.ic_repeat_one);

                        mMediaBrowserClient.getTransportControls().setRepeatMode(
                                PlaybackStateCompat.REPEAT_MODE_ONE);
                    } else if (repeatType == RepeatType.ONE_SONG) {
                        repeatType = RepeatType.PLAY_LIST;
                        mRepeat.setImageResource(R.drawable.ic_repeat);

                        mMediaBrowserClient.getTransportControls().setRepeatMode(
                                PlaybackStateCompat.REPEAT_MODE_ALL);
                    } else {
                        repeatType = RepeatType.NONE;
                        mRepeat.setColorFilter(
                                ContextCompat.getColor(MainActivity.this, R.color.whiteTransparent));

                        mMediaBrowserClient.getTransportControls().setRepeatMode(
                                PlaybackStateCompat.REPEAT_MODE_NONE);
                    }

                    break;
            }
        }
    }

    private String getMinutesAndSeconds(long millis) {
        String minutesAndSeconds = "";

        int minutes = (int) ((millis % (1000 * 60 * 60)) / (1000 * 60));
        int seconds = (int) (((millis % (1000 * 60 * 60)) % (1000 * 60)) / 1000);

        return minutesAndSeconds +
                String.format(Locale.getDefault(), "%02d", minutes) +
                ":" +
                String.format(Locale.getDefault(), "%02d", seconds);
    }

    private void updateElapsedTime() {
        mDurationUpdater.add(
                Observable.interval(500, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeWith(
                            new DisposableObserver<Long>() {
                                @Override
                                public void onNext(Long aLong) {
                                    mElapsedTime.setText(
                                            getMinutesAndSeconds(mSeekBarAudio.getProgress()));
                                }

                                @Override
                                public void onError(Throwable e) {

                                }

                                @Override
                                public void onComplete() {

                                }
                            }
                    )
        );
    }

    private class MediaBrowserConnection extends MediaBrowserClient {
        private MediaBrowserConnection(Context mContext) {
            super(mContext, MediaPlaybackService.class);
        }

        @Override
        protected void onConnected(@NonNull MediaControllerCompat mediaController) {
            mSeekBarAudio.setMediaController(mediaController);
        }

        @Override
        protected void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children) {
            super.onChildrenLoaded(parentId, children);

            final MediaControllerCompat mediaController = getMediaController();

            for (final MediaBrowserCompat.MediaItem item : children) {
                mediaController.addQueueItem(item.getDescription());
            }

            mediaController.getTransportControls().prepare();
        }
    }

    // observe changes in play state, metadata, session, queue, etc.
    private class MediaBrowserObserver extends MediaControllerCompat.Callback {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            mIsPlaying = state != null &&
                    state.getState() == PlaybackStateCompat.STATE_PLAYING;
            mPlayPause.setChecked(mIsPlaying);
        }


        private void loadImages(String imageUri) {
            RequestBuilder<Drawable> glideRequestBuilder = GlideApp.with(MainActivity.this).load(imageUri);
            RequestOptions backgroundTransformOptions = new RequestOptions()
                    .transforms(
                            new CenterCrop(),
                            new BlurTransformation(60),
                            new ColorFilterTransformation(
                                    ContextCompat.getColor(MainActivity.this, R.color.colorBackgroundOverlay))
                    );

            glideRequestBuilder
                    .into(mAlbumArt);

            glideRequestBuilder
                    .apply(backgroundTransformOptions)
                    .into(mMainBackground);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            if (metadata == null) {
                return;
            }

            final String mInitialElapsedTime = "00:00";

            final String title = TextCaseUtils.toPascalCaseWithSpace(
                    metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));

            final String artist = TextCaseUtils.toPascalCaseWithSpace(
                    metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));

            final Long duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);

            final String albumArt = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);

            mTitle.setText(title);
            mArtist.setText(artist);
            mElapsedTime.setText(mInitialElapsedTime);
            mDuration.setText(getMinutesAndSeconds(duration));

            loadImages(albumArt);
        }

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
        }

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            super.onQueueChanged(queue);
        }
    }
}
