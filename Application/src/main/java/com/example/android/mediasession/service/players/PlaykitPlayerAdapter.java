package com.example.android.mediasession.service.players;

import android.content.Context;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.example.android.mediasession.service.PlaybackInfoListener;
import com.example.android.mediasession.service.PlayerAdapter;
import com.example.android.mediasession.service.contentcatalogs.MusicLibrary;
import com.kaltura.playkit.PKMediaConfig;
import com.kaltura.playkit.PKMediaEntry;
import com.kaltura.playkit.PKMediaFormat;
import com.kaltura.playkit.PKMediaSource;
import com.kaltura.playkit.PlayKitManager;
import com.kaltura.playkit.Player;

import java.util.Collections;

public class PlaykitPlayerAdapter extends PlayerAdapter {

    private final Context context;
    private Player player;
    private PlaybackInfoListener playbackInfoListener;
    private MediaMetadataCompat currentMedia;
    private String filename;
    private boolean currentMediaPlayedToCompletion;
    private int state;
    private int changeMediaCounter = 0;

    public PlaykitPlayerAdapter(@NonNull Context context, PlaybackInfoListener listener) {
        super(context);
        this.context = context;
        this.playbackInfoListener = listener;
    }

    @Override
    public void playFromMedia(MediaMetadataCompat metadata) {
        currentMedia = metadata;
        final String mediaId = metadata.getDescription().getMediaId();
        playFile(MusicLibrary.getMusicFilename(mediaId));
    }

    private void playFile(String filename) {
        boolean mediaChanged = (this.filename == null || !filename.equals(this.filename));
        if (currentMediaPlayedToCompletion) {
            // Last audio file was played to completion, the resourceId hasn't changed, but the
            // player was released, so force a reload of the media file for playback.
            mediaChanged = true;
            currentMediaPlayedToCompletion = false;
        }
        if (!mediaChanged) {
            if (!isPlaying()) {
                play();
            }
            return;
        } else {
            release();
        }

        this.filename = filename;

        if (player == null) {
            player = PlayKitManager.loadPlayer(context, null);
        }

        player.prepare(changeMedia());
        play();
    }

    private PKMediaConfig changeMedia() {
        changeMediaCounter++;
        return changeMediaCounter % 2 == 1 ? new PKMediaConfig()
                .setMediaEntry(new PKMediaEntry()
                        .setSources(Collections.singletonList(new PKMediaSource()
                                .setMediaFormat(PKMediaFormat.dash)
                                .setUrl("https://cdnapisec.kaltura.com/p/2185291/sp/218529100/playManifest/entryId/1_u2eluoel/protocol/https/format/mpegdash/flavorIds/1_f8gxulez/a.mpd"))))
                :  new PKMediaConfig()
                .setMediaEntry(new PKMediaEntry()
                        .setSources(Collections.singletonList(new PKMediaSource()
                                .setMediaFormat(PKMediaFormat.dash)
                                .setUrl("https://cdnapisec.kaltura.com/p/2185291/sp/218529100/playManifest/entryId/1_axo40e78/protocol/https/format/mpegdash/flavorIds/1_9jx7sl9o/a.mpd"))));


    }

    private void release() {
        if (player != null) {
            player.stop();
            player = null;
        }
    }

    @Override
    public MediaMetadataCompat getCurrentMedia() {
        return currentMedia;
    }

    @Override
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    @Override
    protected void onPlay() {
        if (player != null) {
            player.play();
            setNewState(PlaybackStateCompat.STATE_PLAYING);
        }
    }

    @Override
    protected void onPause() {
        if (player != null) {
            player.pause();
            setNewState(PlaybackStateCompat.STATE_PAUSED);
        }
    }

    @Override
    protected void onStop() {
        // Regardless of whether or not the MediaPlayer has been created / started, the state must
        // be updated, so that MediaNotificationManager can take down the notification.
        setNewState(PlaybackStateCompat.STATE_STOPPED);
        release();
    }

    @Override
    public void seekTo(long position) {
        if (player != null) {
            player.seekTo(position);
            setNewState(state);
        }
    }

    @Override
    public void setVolume(float volume) {
        if (player != null) {
            player.setVolume(volume);
        }
    }

    // This is the main reducer for the player state machine.
    private void setNewState(@PlaybackStateCompat.State int newPlayerState) {
        state = newPlayerState;

        // Whether playback goes to completion, or whether it is stopped, the
        // mCurrentMediaPlayedToCompletion is set to true.
        if (state == PlaybackStateCompat.STATE_STOPPED) {
            currentMediaPlayedToCompletion = true;
        }

        final long currentPosition = player != null ? player.getCurrentPosition() : -1;

        final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
        stateBuilder.setActions(getAvailableActions());
        stateBuilder.setState(state,
                player.getCurrentPosition(),
                1.0f,
                SystemClock.elapsedRealtime());
        playbackInfoListener.onPlaybackStateChange(stateBuilder.build());
    }

    /**
     * Set the current capabilities available on this session. Note: If a capability is not
     * listed in the bitmask of capabilities then the MediaSession will not handle it. For
     * example, if you don't want ACTION_STOP to be handled by the MediaSession, then don't
     * included it in the bitmask that's returned.
     */
    @PlaybackStateCompat.Actions
    private long getAvailableActions() {
        long actions = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        switch (state) {
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
}
