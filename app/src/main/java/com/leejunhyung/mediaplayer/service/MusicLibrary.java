package com.leejunhyung.mediaplayer.service;

import android.content.Context;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class MusicLibrary {
    private static final TreeMap<String, MediaMetadataCompat> music = new TreeMap<>();
    private static final HashMap<String, String> musicFileName = new HashMap<>();

    static {
        createMetadata(
                "final_masquerade",
                "final_masquerade",
                "linkin_park",
                "hunting_party",
                "https://upload.wikimedia.org/wikipedia/en/thumb/f/fa/Linkin_Park%2C_The_Hunting_Party%2C_album_art_final.jpg/220px-Linkin_Park%2C_The_Hunting_Party%2C_album_art_final.jpg",
                "rock",
                217,
                TimeUnit.SECONDS,
                "final_masquerade.mp3"
        );

        createMetadata(
                "war",
                "war",
                "linkin_park",
                "hunting_party",
                "https://upload.wikimedia.org/wikipedia/en/thumb/f/fa/Linkin_Park%2C_The_Hunting_Party%2C_album_art_final.jpg/220px-Linkin_Park%2C_The_Hunting_Party%2C_album_art_final.jpg",
                "rock",
                131,
                TimeUnit.SECONDS,
                "war.mp3"
        );
    }

    public static String getMusicFilename(String mediaId) {
        return musicFileName.containsKey(mediaId) ? musicFileName.get(mediaId) : null;
    }

    public static List<MediaBrowserCompat.MediaItem> getMediaItems() {
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        for (MediaMetadataCompat metadata : music.values()) {
            result.add(
                    new MediaBrowserCompat.MediaItem(
                            metadata.getDescription(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
        }

        return result;
    }

    public static MediaMetadataCompat getMetadata(String mediaId) {
        MediaMetadataCompat metadata = music.get(mediaId);
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();

        for (String key : new String[] {
                MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                MediaMetadataCompat.METADATA_KEY_ALBUM,
                MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                MediaMetadataCompat.METADATA_KEY_GENRE,
                MediaMetadataCompat.METADATA_KEY_TITLE
        }) {
            builder.putString(key, metadata.getString(key));
        }

        String KEY_DURATION = MediaMetadataCompat.METADATA_KEY_DURATION;

        builder.putLong(KEY_DURATION, metadata.getLong(KEY_DURATION));

        return builder.build();
    }

    private static void createMetadata(
            String mediaId,
            String title,
            String artist,
            String album,
            String albumArtUri,
            String genre,
            long duration,
            TimeUnit durationUnit,
            String musicFilename
    ) {
        music.put(
                mediaId,
                new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, albumArtUri)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                                TimeUnit.MILLISECONDS.convert(duration, durationUnit))
                        .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                        .build()
        );
        musicFileName.put(mediaId, musicFilename);
    }
}
