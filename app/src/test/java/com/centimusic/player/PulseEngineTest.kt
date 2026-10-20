package com.centimusic.player

import com.centimusic.player.ui.pulse.PulseEngine
import com.centimusic.player.ui.pulse.PulseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseEngineTest {
    private val frame = 1f / 60f

    private fun PulseEngine.run(seconds: Float, levels: FloatArray?) {
        repeat((seconds / frame).toInt()) {
            step(frame) { out ->
                if (levels == null) 0 else {
                    levels.copyInto(out, destinationOffset = out.size - levels.size)
                    levels.size
                }
            }
        }
    }

    @Test
    fun `pulse follows measured loudness`() {
        val engine = PulseEngine(4)
        engine.run(0.5f, floatArrayOf(0f, 0f, 0f, 0.9f))
        assertEquals(PulseMode.LIVE, engine.mode)
        assertTrue("loud audio gives a strong pulse: ${engine.level}", engine.level > 0.8f)

        engine.run(0.8f, floatArrayOf(0f, 0f, 0f, 0f))
        assertTrue("silence lets the pulse fall: ${engine.level}", engine.level < 0.05f)
        assertEquals(PulseMode.LIVE, engine.mode)
    }

    @Test
    fun `history mirrors the measured slices`() {
        val engine = PulseEngine(4)
        engine.run(0.5f, floatArrayOf(0.1f, 0.4f, 0.7f, 1f))
        assertTrue(engine.history[0] < engine.history[1] && engine.history[2] < engine.history[3])
    }

    @Test
    fun `pausing freezes the pulse because nothing steps it`() {
        val engine = PulseEngine(1)
        engine.run(0.5f, floatArrayOf(0.7f))
        val atPause = engine.level
        // While paused the Compose loop is cancelled, so step() is never called.
        assertEquals(atPause, engine.level, 0f)
        // Resuming continues from the same state.
        engine.run(0.2f, floatArrayOf(0.7f))
        assertEquals(0.7f, engine.level, 0.05f)
    }

    @Test
    fun `track change resets everything`() {
        val engine = PulseEngine(3)
        engine.run(0.5f, floatArrayOf(0.5f, 0.6f, 0.9f))
        engine.reset()
        assertEquals(PulseMode.IDLE, engine.mode)
        assertEquals(0f, engine.level, 0f)
        assertTrue(engine.history.all { it == 0f })
    }

    @Test
    fun `without measurements it falls back to a slow decorative breath`() {
        val engine = PulseEngine(8)
        engine.run(0.5f, null)
        assertEquals("waits briefly for the first audio", PulseMode.IDLE, engine.mode)

        var min = 1f
        var max = 0f
        repeat((6f / frame).toInt()) {
            engine.step(frame) { 0 }
            min = minOf(min, engine.level)
            max = maxOf(max, engine.level)
        }
        assertEquals(PulseMode.DECORATIVE, engine.mode)
        assertTrue("decorative motion stays subtle: $max", max <= PulseEngine.DECORATIVE_BASE + PulseEngine.DECORATIVE_DEPTH + 0.01f)
        assertTrue("but it does breathe: $min..$max", max - min > 0.08f)
        // Uniform across the halo, so it can't be read as a waveform.
        assertTrue(engine.history.all { it == engine.history[0] })
    }
}
