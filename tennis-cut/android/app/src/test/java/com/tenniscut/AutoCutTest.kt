package com.tenniscut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class AutoCutTest {
    /** Quiet background with a loud 200 Hz hum, plus sharp broadband clicks at the given times. */
    private fun envelopeOf(durationS: Double, clicks: List<Double>, rate: Int = 48000): FloatArray {
        val rng = Random(0)
        val x = FloatArray((durationS * rate).toInt()) {
            (0.005 * rng.nextDouble(-1.7, 1.7) + 0.2 * sin(2 * PI * 200 * it / rate)).toFloat()
        }
        for (c in clicks) {
            val start = (c * rate).toInt()
            for (k in 0 until (0.02 * rate).toInt()) {
                x[start + k] += (0.5 * rng.nextDouble(-1.7, 1.7) * exp(-k / (0.003 * rate))).toFloat()
            }
        }
        val detector = OnsetDetector()
        val resampler = Resampler(rate, AutoCut.SAMPLE_RATE)
        // Feed in odd-sized chunks, as a decoder would.
        var i = 0
        while (i < x.size) {
            val n = minOf(1777, x.size - i)
            resampler.push(x.copyOfRange(i, i + n), n, detector::push)
            i += n
        }
        return detector.envelope()
    }

    @Test fun findsClicksAtTheRightTime() {
        val truth = listOf(1.0, 2.2, 3.1, 5.5, 8.0)
        val hits = AutoCut.hits(envelopeOf(10.0, truth))
        assertEquals(truth.size, hits.size)
        hits.zip(truth).forEach { (h, t) -> assertTrue("hit $h vs $t", abs(h - t) <= 0.02) }
    }

    @Test fun closeTransientsCountOnce() {
        assertEquals(2, AutoCut.hits(envelopeOf(4.0, listOf(1.0, 1.1, 3.0))).size)
    }

    @Test fun ralliesGroupHitsAndDropLoneSounds() {
        val hits = listOf(10.0, 11.2, 12.5, 30.0, 50.0, 51.0, 52.1, 53.0)
        assertEquals(listOf(Rally(7.5, 14.5, 3), Rally(47.5, 55.0, 4)), AutoCut.rallies(hits, 100.0))
    }

    @Test fun fftOfASineHasOnePeak() {
        val n = 64
        val re = DoubleArray(n) { sin(2 * PI * 5 * it / n) }
        val im = DoubleArray(n)
        Fft.transform(re, im)
        val mags = DoubleArray(n / 2) { kotlin.math.hypot(re[it], im[it]) }
        assertEquals(5, mags.indices.maxBy { mags[it] })
        assertEquals(n / 2.0, mags[5], 1e-9)
    }
}
