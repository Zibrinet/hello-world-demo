package com.tenniscut

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Automatic point finding when nothing was marked: racket hits are sharp sounds, a rally is a run
 * of hits, and the quiet gaps between rallies are the dead time we cut. Same method as
 * pipeline/tennis/audio_hits.py (band-limited spectral flux, adaptive peak picking).
 */
object AutoCut {
    const val SAMPLE_RATE = 16000
    const val HOP = 160 // 10 ms per envelope frame
    const val FRAME_S = HOP.toDouble() / SAMPLE_RATE
    const val THRESHOLD = 5.0
    const val MIN_GAP_S = 0.35
    const val RALLY_GAP_S = 4.5 // longer silence than this ends a rally
    const val MIN_HITS = 2 // a lone sound is more likely a dropped ball than a point
    const val BEFORE_FIRST_HIT_S = 2.5 // keeps the ball toss
    const val AFTER_LAST_HIT_S = 2.0

    /** Times (s) of likely racket hits in an onset envelope. */
    fun hits(env: FloatArray, threshold: Double = THRESHOLD): List<Double> {
        if (env.isEmpty()) return emptyList()
        val local = localMedian(env)
        val dev = FloatArray(env.size) { kotlin.math.abs(env[it] - local[it]) }
        val sigma = (1.4826 * median(dev)).coerceAtLeast(1e-9)
        val z = DoubleArray(env.size) { (env[it] - local[it]) / sigma }
        val minGap = (MIN_GAP_S / FRAME_S).toInt()
        // Strongest peaks first; drop any peak within minGap of one already kept.
        val candidates = (z.indices).filter { i ->
            z[i] >= threshold && (i == 0 || z[i] >= z[i - 1]) && (i == z.size - 1 || z[i] > z[i + 1])
        }.sortedByDescending { z[it] }
        val kept = ArrayList<Int>()
        for (i in candidates) if (kept.none { kotlin.math.abs(it - i) < minGap }) kept.add(i)
        return kept.sorted().map { (it * HOP + OnsetDetector.N_FFT / 2.0) / SAMPLE_RATE }
    }

    /** Groups hits into rallies (start and end include the toss and the last bounce). */
    fun rallies(hits: List<Double>, durationS: Double): List<Rally> {
        val groups = ArrayList<MutableList<Double>>()
        for (t in hits.sorted()) {
            if (groups.isEmpty() || t - groups.last().last() > RALLY_GAP_S) groups.add(mutableListOf(t))
            else groups.last().add(t)
        }
        // Windows cannot overlap: rallies are more than RALLY_GAP_S apart, which exceeds the padding.
        return groups.filter { it.size >= MIN_HITS }.map {
            Rally(maxOf(0.0, it.first() - BEFORE_FIRST_HIT_S), minOf(durationS, it.last() + AFTER_LAST_HIT_S), it.size)
        }
    }

    /** Decodes the video's sound and returns its onset envelope (one value per 10 ms). */
    fun envelope(video: File, onProgress: (Float) -> Unit): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(video.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/")
        } ?: return FloatArray(0).also { extractor.release() }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val detector = OnsetDetector()
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var resampler = Resampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), SAMPLE_RATE)
        var mono = FloatArray(0)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    resampler = Resampler(codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), SAMPLE_RATE)
                } else if (o >= 0) {
                    val pcm = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder()).asShortBuffer() // 16-bit PCM by default
                    val frames = pcm.remaining() / channels
                    if (mono.size < frames) mono = FloatArray(frames)
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until channels) sum += pcm.get(f * channels + c)
                        mono[f] = sum / channels / 32768f
                    }
                    resampler.push(mono, frames, detector::push)
                    codec.releaseOutputBuffer(o, false)
                    if (durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
        return detector.envelope()
    }

    private fun median(values: FloatArray): Double {
        val sorted = values.copyOf().also { it.sort() }
        return sorted[sorted.size / 2].toDouble()
    }

    /** Median over a 2 s window, evaluated every 0.5 s (plenty for a slowly changing background). */
    private fun localMedian(env: FloatArray): FloatArray {
        val half = (1.0 / FRAME_S).toInt()
        val block = (0.5 / FRAME_S).toInt()
        val out = FloatArray(env.size)
        var b = 0
        while (b < env.size) {
            val center = minOf(b + block / 2, env.size - 1)
            val window = env.copyOfRange(maxOf(0, center - half), minOf(env.size, center + half + 1))
            val m = median(window).toFloat()
            for (i in b until minOf(b + block, env.size)) out[i] = m
            b += block
        }
        return out
    }
}

/** Streaming spectral flux over 1-8 kHz: mean positive change in log magnitude between 10 ms frames. */
class OnsetDetector {
    companion object {
        const val N_FFT = 512
        private const val LOW_BIN = 32 // 1 kHz at 16 kHz / 512
        private const val HIGH_BIN = 256 // 8 kHz
        private const val LOG_GAIN = 100f
    }

    private val window = FloatArray(N_FFT) { (0.5 - 0.5 * cos(2 * PI * it / N_FFT)).toFloat() }
    private val ring = FloatArray(N_FFT)
    private var written = 0L
    private val re = DoubleArray(N_FFT)
    private val im = DoubleArray(N_FFT)
    private var prev: FloatArray? = null
    private var env = FloatArray(1 shl 16)
    private var frames = 0

    fun push(sample: Float) {
        ring[(written % N_FFT).toInt()] = sample
        written++
        if (written >= N_FFT && (written - N_FFT) % AutoCut.HOP == 0L) frame()
    }

    fun envelope(): FloatArray = env.copyOf(frames)

    private fun frame() {
        val start = written - N_FFT
        for (i in 0 until N_FFT) {
            re[i] = (ring[((start + i) % N_FFT).toInt()] * window[i]).toDouble()
            im[i] = 0.0
        }
        Fft.transform(re, im)
        val mag = FloatArray(HIGH_BIN - LOW_BIN + 1) {
            val k = LOW_BIN + it
            ln(1 + LOG_GAIN * sqrt(re[k] * re[k] + im[k] * im[k])).toFloat()
        }
        val p = prev ?: mag
        var flux = 0f
        for (i in mag.indices) flux += maxOf(0f, mag[i] - p[i])
        prev = mag
        if (frames == env.size) env = env.copyOf(env.size * 2)
        env[frames++] = flux / mag.size
    }
}

/** Linear-interpolation resampler that works on a stream of chunks. */
class Resampler(inRate: Int, outRate: Int) {
    private val step = inRate.toDouble() / outRate
    private var pos = 0.0 // index of the next output sample in the current chunk; -1 means the previous chunk's last sample
    private var last = 0f

    fun push(x: FloatArray, n: Int, out: (Float) -> Unit) {
        if (n == 0) return
        while (true) {
            val i = floor(pos).toInt()
            if (i + 1 >= n) break
            val a = if (i < 0) last else x[i]
            out(a + (x[i + 1] - a) * (pos - i).toFloat())
            pos += step
        }
        last = x[n - 1]
        pos -= n
    }
}

/** In-place iterative radix-2 FFT. */
object Fft {
    private val cache = HashMap<Int, Pair<DoubleArray, DoubleArray>>()

    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        val (cosT, sinT) = cache.getOrPut(n) {
            DoubleArray(n / 2) { cos(2 * PI * it / n) } to DoubleArray(n / 2) { -sin(2 * PI * it / n) }
        }
        var len = 2
        while (len <= n) {
            val stride = n / len
            for (s in 0 until n step len) {
                for (k in 0 until len / 2) {
                    val wr = cosT[k * stride]
                    val wi = sinT[k * stride]
                    val a = s + k
                    val b = a + len / 2
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
            }
            len = len shl 1
        }
    }
}
