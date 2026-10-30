package com.nezzar.nfcattendance.data

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * What a read SOUNDS like at the door. One value per answer the teacher has to
 * hear without looking up from the queue:
 *
 *   RECORDED   a clean tick - present, on time
 *   LATE       a two-note rise - present, but after the window
 *   DUPLICATE  a low double knock - already counted, nothing changed
 *   UNKNOWN    a low buzz - not on this roster, flagged
 *   REFUSED    one long low tone - there was nothing to record it against
 *
 * [cueFor] is pure: the map from the processor's answer to the sound is decided
 * once and tested on the JVM, so the door and the test suite cannot disagree.
 */
enum class ReadCue { RECORDED, LATE, DUPLICATE, UNKNOWN, REFUSED }

/**
 * The one place an [Outcome] becomes a sound. A late arrival is still a
 * successful read, so it keeps the success shape and only changes pitch.
 */
fun cueFor(outcome: Outcome, status: AttendanceStatus? = null): ReadCue = when (outcome) {
    Outcome.RECORDED ->
        if (status == AttendanceStatus.LATE) ReadCue.LATE else ReadCue.RECORDED
    Outcome.DUPLICATE -> ReadCue.DUPLICATE
    Outcome.UNKNOWN -> ReadCue.UNKNOWN
    Outcome.NO_SESSION, Outcome.PAUSED, Outcome.INVALID -> ReadCue.REFUSED
}

/** One note: a pitch held for [millis], then [gapMillis] of silence. */
data class ToneStep(
    val hertz: Double,
    val millis: Int,
    val gapMillis: Int = 0,
    val level: Float = 1f,
)

/** A whole sound: the notes, in the order the speaker plays them. */
data class ToneSpec(val steps: List<ToneStep>) {
    val totalMillis: Int get() = steps.sumOf { it.millis + it.gapMillis }
}

/**
 * The three tones the app ships. No audio files and no assets: a sine is
 * rendered into a PCM buffer at the moment it is played, so there is nothing to
 * lose, nothing to keep in sync, and the same audio on every phone.
 */
object ReadTones {

    const val SAMPLE_RATE = 44_100

    /** Fade in and out, in milliseconds. Without it a tone starts with a click. */
    private const val EDGE_MILLIS = 5

    /** Headroom: a full-scale sine on a phone speaker is harsh and clips the amp. */
    private const val HEADROOM = 0.55

    private val tick = ToneSpec(listOf(ToneStep(1_046.50, 90, 0)))
    private val rise = ToneSpec(
        listOf(
            ToneStep(783.99, 70, 15),
            ToneStep(1_046.50, 95, 0),
        ),
    )
    private val knock = ToneSpec(
        listOf(
            ToneStep(587.33, 60, 45),
            ToneStep(587.33, 60, 0),
        ),
    )
    private val buzz = ToneSpec(listOf(ToneStep(233.08, 240, 0)))
    private val refusal = ToneSpec(listOf(ToneStep(196.00, 320, 0)))

    fun forCue(cue: ReadCue): ToneSpec = when (cue) {
        ReadCue.RECORDED -> tick
        ReadCue.LATE -> rise
        ReadCue.DUPLICATE -> knock
        ReadCue.UNKNOWN -> buzz
        ReadCue.REFUSED -> refusal
    }

    /**
     * A mono 16-bit PCM buffer for [spec], with a short fade at both ends of every
     * note so no read starts or ends with a click. Pure arithmetic: no Android in
     * here, so [ReadCueTest] can prove the lengths and the peaks.
     */
    fun render(spec: ToneSpec, sampleRate: Int = SAMPLE_RATE): ShortArray {
        require(sampleRate > 0) { "sampleRate must be positive" }
        var total = 0
        for (step in spec.steps) {
            total += samplesFor(step.millis, sampleRate) + samplesFor(step.gapMillis, sampleRate)
        }
        val out = ShortArray(total)
        var cursor = 0
        for (step in spec.steps) {
            val count = samplesFor(step.millis, sampleRate)
            val fade = maxOf(1, samplesFor(EDGE_MILLIS, sampleRate))
            val level = step.level.coerceIn(0f, 1f) * HEADROOM
            for (i in 0 until count) {
                val envelope = min(1.0, min(i.toDouble() / fade, (count - i).toDouble() / fade))
                val wave = sin(2.0 * PI * step.hertz * i.toDouble() / sampleRate)
                val value = (wave * envelope * level * Short.MAX_VALUE).toInt()
                out[cursor + i] = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            cursor += count + samplesFor(step.gapMillis, sampleRate)
        }
        return out
    }

    private fun samplesFor(millis: Int, sampleRate: Int): Int =
        if (millis <= 0) 0 else Math.round(millis.toLong() * sampleRate / 1000.0).toInt()
}
