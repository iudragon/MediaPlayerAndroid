package com.leejunhyung.mediaplayer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.support.v4.media.app.NotificationCompat.MediaStyle;

import com.leejunhyung.mediaplayer.R;
import com.leejunhyung.mediaplayer.ui.MainActivity;
import com.leejunhyung.utils.TextCaseUtils;

public class MediaNotificationManager {

    public static final int NOTIFICATION_ID = 412;

    private static final String TAG = MediaNotificationManager.class.getSimpleName();
    private static final String CHANNEL_ID = "com.example.android.musicplayer.channel";
    private static final int REQUEST_CODE = 501;

    private final MediaPlaybackService mService;

    private final NotificationCompat.Action mPlayAction;
    private final NotificationCompat.Action mPauseAction;
    private final NotificationCompat.Action mNextAction;
    private final NotificationCompat.Action mPrevAction;
    private final NotificationManager mNotificationManager;

    public MediaNotificationManager(MediaPlaybackService mService) {
        this.mService = mService;

        this.mNotificationManager = (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);

        mPlayAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService,
                        PlaybackStateCompat.ACTION_PLAY
                )
        );

        mPauseAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService,
                        PlaybackStateCompat.ACTION_PAUSE
                )
        );

        mNextAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_next,
                "",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
        );

        mPrevAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_previous,
                "",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
        );

        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();}
    }

    public NotificationManager getNotificationManager() {
        return mNotificationManager;
    }

    public Notification getNotification(
            MediaMetadataCompat metadata,
            @NonNull PlaybackStateCompat state,
            MediaSessionCompat.Token token
    ) {
        boolean isPlaying = state.getState() == PlaybackStateCompat.STATE_PLAYING;
        MediaDescriptionCompat description = metadata.getDescription();
        NotificationCompat.Builder builder =
                buildNotification(state, token, isPlaying, description);

        return builder.build();
    }

    private NotificationCompat.Builder buildNotification(
            @NonNull PlaybackStateCompat state,
            MediaSessionCompat.Token token,
            boolean isPlaying,
            MediaDescriptionCompat description
    ) {
        if (isAndroidOOrHigher()) {
            createChannel();
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mService, CHANNEL_ID);

        final String title =
                TextUtils.isEmpty(description.getTitle()) ? "" :
                        TextCaseUtils.toPascalCaseWithSpace(description.getTitle().toString());

        final String artist =
                TextUtils.isEmpty(description.getSubtitle()) ? "" :
                        TextCaseUtils.toPascalCaseWithSpace(description.getSubtitle().toString());

        builder
            .setColor(ContextCompat.getColor(mService, R.color.colorAccent))
            .setSmallIcon(R.drawable.ic_music_24px)
            .setContentIntent(createContentIntent())
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(BitmapFactory.decodeResource(
                    mService.getResources(), R.drawable.ic_music_48px))
            .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
                    mService, PlaybackStateCompat.ACTION_STOP
            ))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        if ((state.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0) {
            builder.addAction(mPrevAction);
        }

        builder.addAction(isPlaying ? mPauseAction : mPlayAction);

        if ((state.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
            builder.addAction(mNextAction);
        }

        return builder;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createChannel() {
        if (mNotificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            // The user-visible name of the channel.
            CharSequence name = "MediaSession";
            // The user-visible description of the channel.
            String description = "MediaSession and MediaPlayer";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            // Configure the notification channel.
            mChannel.setDescription(description);
            mChannel.enableLights(true);
            // Sets the notification light color for notifications posted to this
            // channel, if the device supports this feature.
            mChannel.setLightColor(Color.RED);
            mChannel.enableVibration(true);
            mChannel.setVibrationPattern(
                    new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            mNotificationManager.createNotificationChannel(mChannel);
            Log.d(TAG, "createChannel: New channel created");
        } else {
            Log.d(TAG, "createChannel: Existing channel reused");
        }
    }

    private boolean isAndroidOOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    private PendingIntent createContentIntent() {
        Intent openMainActivity = new Intent(mService, MainActivity.class);
        openMainActivity.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                mService, REQUEST_CODE, openMainActivity, PendingIntent.FLAG_CANCEL_CURRENT
        );
    }
}
