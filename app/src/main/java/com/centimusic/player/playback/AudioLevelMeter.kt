package com.centimusic.player.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Loudness envelope of the audio CentiMusic is actually playing, measured from decoded PCM inside
 * ExoPlayer's audio sink (see [LevelMeteringAudioSink]) — no microphone or RECORD_AUDIO permission.
 *
 * Each [FRAME_US] slice of audio is stored with its presentation timestamp. The sink also reports
 * the timestamp currently coming out of the speaker, so readers get the level being *heard* now,
 * not the level of audio still queued in the output buffer.
 *
 * Writes happen on ExoPlayer's playback thread; reads on the main thread.
 */
object AudioLevelMeter {
    const val FRAME_US = 25_000L
    private const val CAPACITY = 512 // ~12.8 s of history
    private const val FLOOR_DB = -48f

    private val pts = LongArray(CAPACITY)
    private val levels = FloatArray(CAPACITY)
    private var head = 0 // next write slot
    private var count = 0

    private val consumers = AtomicInteger(0)

    @Volatile var supported = true
        private set

    /** Presentation time currently audible, in the sink's timebase; [C.TIME_UNSET] if unknown. */
    @Volatile var audibleUs = C.TIME_UNSET
        private set

    // Playback-thread state.
    private var encoding = C.ENCODING_INVALID
    private var channels = 0
    private var sampleRate = 0
    private var lastBufferPts = Long.MIN_VALUE
    private var acc = 0.0
    private var accFrames = 0
    private var accStartUs = 0L

    /** Measuring costs a little CPU, so it only runs while a visualizer is on screen. */
    val isActive: Boolean get() = consumers.get() > 0

    fun acquire() {
        consumers.incrementAndGet()
    }

    fun release() {
        if (consumers.decrementAndGet() <= 0) {
            consumers.set(0)
            reset()
        }
    }

    internal fun configure(pcmEncoding: Int, channelCount: Int, rate: Int) {
        encoding = pcmEncoding
        channels = channelCount
        sampleRate = rate
        supported = channelCount > 0 && rate > 0 && pcmEncoding in SUPPORTED
        reset()
    }

    @Synchronized
    internal fun reset() {
        head = 0
        count = 0
        lastBufferPts = Long.MIN_VALUE
        acc = 0.0
        accFrames = 0
        audibleUs = C.TIME_UNSET
    }

    internal fun onPosition(positionUs: Long) {
        if (positionUs != AudioSink.CURRENT_POSITION_NOT_SET) audibleUs = positionUs
    }

    /** Called for every handleBuffer; the sink may re-offer a partially consumed buffer, so dedupe by pts. */
    internal fun onBuffer(buffer: ByteBuffer, presentationTimeUs: Long) {
        if (!isActive || !supported || presentationTimeUs == lastBufferPts) return
        lastBufferPts = presentationTimeUs
        val data = buffer.duplicate().order(ByteOrder.nativeOrder())
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        val frameBytes = bytesPerSample * channels
        val frames = data.remaining() / frameBytes
        val samplesPerSlice = (sampleRate * FRAME_US / 1_000_000L).toInt().coerceAtLeast(1)
        if (accFrames == 0) accStartUs = presentationTimeUs
        var frameIndex = 0
        while (frameIndex < frames) {
            var mono = 0.0
            for (c in 0 until channels) mono += readSample(data)
            mono /= channels
            acc += mono * mono
            accFrames++
            frameIndex++
            if (accFrames >= samplesPerSlice) {
                push(accStartUs, toLevel(sqrt(acc / accFrames)))
                acc = 0.0
                accFrames = 0
                accStartUs = presentationTimeUs + frameIndex * 1_000_000L / sampleRate
            }
        }
    }

    private fun readSample(b: ByteBuffer): Double = when (encoding) {
        C.ENCODING_PCM_16BIT -> b.short / 32768.0
        C.ENCODING_PCM_FLOAT -> b.float.toDouble()
        C.ENCODING_PCM_32BIT -> b.int / 2147483648.0
        else -> { // 24-bit little endian
            val lo = b.get().toInt() and 0xFF
            val mid = b.get().toInt() and 0xFF
            val hi = b.get().toInt()
            ((hi shl 16) or (mid shl 8) or lo) / 8388608.0
        }
    }

    /** RMS → 0..1 on a dB scale, which matches perceived loudness far better than linear RMS. */
    private fun toLevel(rms: Double): Float {
        if (rms <= 1e-6) return 0f
        val db = 20f * log10(rms).toFloat()
        return ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)
    }

    @Synchronized
    private fun push(timeUs: Long, level: Float) {
        pts[head] = timeUs
        levels[head] = level
        head = (head + 1) % CAPACITY
        if (count < CAPACITY) count++
    }

    /**
     * Fills [out] with the most recent levels that are already audible (oldest first), skipping
     * anything older than [notBeforeUs]. Returns how many slots were filled from the right.
     */
    @Synchronized
    fun snapshot(out: FloatArray, notBeforeUs: Long = Long.MIN_VALUE): Int {
        out.fill(0f)
        val now = audibleUs
        if (now == C.TIME_UNSET || count == 0) return 0
        var filled = 0
        var i = (head - 1 + CAPACITY) % CAPACITY
        var seen = 0
        while (seen < count && filled < out.size) {
            val t = pts[i]
            if (t < notBeforeUs) break
            if (t <= now) {
                out[out.size - 1 - filled] = levels[i]
                filled++
            }
            i = (i - 1 + CAPACITY) % CAPACITY
            seen++
        }
        return filled
    }

    private val SUPPORTED = setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)
}
