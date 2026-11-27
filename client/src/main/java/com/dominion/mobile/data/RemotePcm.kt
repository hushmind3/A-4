package com.dominion.mobile.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded PCM16 mono conversion. Resample with averaged input frames and a persistent
 * integer phase; the callback's direct buffer is never retained. */
class RemotePcm(seconds: Int = 30) {
    private val samples = ShortArray(WaveAudio.SAMPLE_RATE * seconds.also { require(it in 1..60) })
    private var count = 0
    private var rate = 0
    private var channels = 0
    private var phase = 0L
    private var sum = 0L
    private var window = 0
    private var peak = 0
    private var voiced = 0
    var failed = false
        private set

    @Synchronized fun accept(data: ByteBuffer?, bits: Int, sampleRate: Int, channelCount: Int, frames: Int) {
        if (failed || count == samples.size) return
        if (data == null || bits != 16 || sampleRate !in 8000..96000 ||
            channelCount !in 1..2 || frames !in 1..9600 ||
            data.remaining() < frames * channelCount * 2 ||
            (rate != 0 && (rate != sampleRate || channels != channelCount))) {
            failed = true; return
        }
        rate = sampleRate
        channels = channelCount
        val pcm = data.duplicate().order(ByteOrder.nativeOrder())
        repeat(frames) {
            if (count == samples.size) return
            var mono = 0
            repeat(channelCount) { mono += pcm.short.toInt() }
            mono /= channelCount
            sum += mono
            window++
            phase += WaveAudio.SAMPLE_RATE
            if (phase >= rate) {
                val value = (sum / window).toInt()
                while (phase >= rate && count < samples.size) {
                    samples[count++] = value.toShort()
                    peak = maxOf(peak, kotlin.math.abs(value))
                    if (kotlin.math.abs(value) >= 200) voiced++
                    phase -= rate
                }
                sum = 0
                window = 0
            }
        }
    }
    @Synchronized fun size(): Int = count
    @Synchronized fun wave(): ByteArray {
        check(!failed) { "Unsupported or changed remote PCM format" }
        require(count >= WaveAudio.SAMPLE_RATE / 2 && peak >= 300 &&
            voiced >= WaveAudio.SAMPLE_RATE / 20) { "No usable remote speech" }
        return WaveAudio.mono16(samples, count)
    }
}
