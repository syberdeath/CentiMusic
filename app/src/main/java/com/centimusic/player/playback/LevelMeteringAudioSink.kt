package com.centimusic.player.playback

import android.content.Context
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * Passes everything through to ExoPlayer's real audio sink, while feeding decoded PCM and the
 * audible output position to [AudioLevelMeter]. Playback behaviour is unchanged.
 */
@UnstableApi
class LevelMeteringAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val isPcm = inputFormat.sampleMimeType == MimeTypes.AUDIO_RAW
        AudioLevelMeter.configure(
            pcmEncoding = if (isPcm) inputFormat.pcmEncoding else -1,
            channelCount = inputFormat.channelCount,
            rate = inputFormat.sampleRate,
        )
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        AudioLevelMeter.onBuffer(buffer, presentationTimeUs)
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        AudioLevelMeter.onPosition(position)
        return position
    }

    override fun flush() {
        AudioLevelMeter.reset()
        super.flush()
    }
}

/** DefaultRenderersFactory whose audio sink is wrapped by [LevelMeteringAudioSink]. */
@UnstableApi
class MeteringRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)?.let { LevelMeteringAudioSink(it) }
}
