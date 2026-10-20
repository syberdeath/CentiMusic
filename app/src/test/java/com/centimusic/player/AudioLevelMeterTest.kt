package com.centimusic.player

import androidx.media3.common.C
import com.centimusic.player.playback.AudioLevelMeter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class AudioLevelMeterTest {
    private val rate = 48_000

    @Before
    fun setUp() {
        AudioLevelMeter.acquire()
        AudioLevelMeter.configure(C.ENCODING_PCM_16BIT, 2, rate)
    }

    @After
    fun tearDown() = AudioLevelMeter.release()

    /** Stereo 16-bit sine; [amplitude] in 0..1. */
    private fun pcm(durationUs: Long, amplitude: Double): ByteBuffer {
        val frames = (rate * durationUs / 1_000_000L).toInt()
        val buf = ByteBuffer.allocate(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val s = (sin(2 * PI * 440 * i / rate) * amplitude * 32767).toInt().toShort()
            buf.putShort(s).putShort(s)
        }
        buf.flip()
        return buf
    }

    @Test
    fun `only levels that are already audible are exposed`() {
        AudioLevelMeter.onBuffer(pcm(200_000, 0.5), presentationTimeUs = 0)
        val out = FloatArray(20)

        AudioLevelMeter.onPosition(50_000) // slices starting at 0, 25 and 50 ms have begun playing; later ones have not
        assertEquals(3, AudioLevelMeter.snapshot(out))

        AudioLevelMeter.onPosition(200_000)
        assertEquals(8, AudioLevelMeter.snapshot(out))
    }

    @Test
    fun `louder audio gives a higher level and silence gives zero`() {
        AudioLevelMeter.onBuffer(pcm(100_000, 0.05), presentationTimeUs = 0)
        AudioLevelMeter.onBuffer(pcm(100_000, 0.8), presentationTimeUs = 100_000)
        AudioLevelMeter.onBuffer(pcm(100_000, 0.0), presentationTimeUs = 200_000)
        AudioLevelMeter.onPosition(300_000)
        val out = FloatArray(12)
        AudioLevelMeter.snapshot(out)
        val quiet = out[1]
        val loud = out[5]
        val silent = out[10]
        assertTrue("loud $loud > quiet $quiet", loud > quiet)
        assertTrue("quiet audio still registers", quiet > 0f)
        assertEquals(0f, silent, 0f)
    }

    @Test
    fun `a re-offered partially consumed buffer is not counted twice`() {
        val buffer = pcm(100_000, 0.5)
        AudioLevelMeter.onBuffer(buffer, presentationTimeUs = 0)
        AudioLevelMeter.onBuffer(buffer, presentationTimeUs = 0)
        AudioLevelMeter.onPosition(1_000_000)
        assertEquals(4, AudioLevelMeter.snapshot(FloatArray(20)))
    }

    @Test
    fun `levels older than a track change are ignored`() {
        AudioLevelMeter.onBuffer(pcm(200_000, 0.5), presentationTimeUs = 0)
        AudioLevelMeter.onPosition(200_000)
        assertEquals(4, AudioLevelMeter.snapshot(FloatArray(20), notBeforeUs = 100_000))
    }
}
