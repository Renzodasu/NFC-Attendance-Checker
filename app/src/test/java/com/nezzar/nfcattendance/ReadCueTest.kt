package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.AttendanceStatus
import com.nezzar.nfcattendance.data.Outcome
import com.nezzar.nfcattendance.data.ReadCue
import com.nezzar.nfcattendance.data.ReadTones
import com.nezzar.nfcattendance.data.cueFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sound a read makes. Two rules matter and both are testable without a phone:
 * every outcome has a cue (silence is never an answer), and no two verdicts that a
 * teacher must tell apart share a sound.
 */
class ReadCueTest {

    @Test
    fun everyOutcomeHasASound() {
        // Nothing may fall through to silence: a read that makes no sound reads as
        // a tap the app ignored, and the queue stops trusting the phone.
        for (outcome in Outcome.entries) {
            val cue = cueFor(outcome, null)
            assertTrue("no cue for " + outcome, ReadTones.forCue(cue).steps.isNotEmpty())
        }
    }

    @Test
    fun aLateArrivalSucceedsWithADifferentSound() {
        assertEquals(ReadCue.RECORDED, cueFor(Outcome.RECORDED, AttendanceStatus.ON_TIME))
        assertEquals(ReadCue.LATE, cueFor(Outcome.RECORDED, AttendanceStatus.LATE))
        // Still a successful read, but the teacher must hear that it is late.
        assertNotEquals(ReadTones.forCue(ReadCue.RECORDED), ReadTones.forCue(ReadCue.LATE))
    }

    @Test
    fun refusalCannotBeMistakenForARecord() {
        // No session, paused, and an unreadable identifier are all refusals.
        assertEquals(ReadCue.REFUSED, cueFor(Outcome.NO_SESSION))
        assertEquals(ReadCue.REFUSED, cueFor(Outcome.PAUSED))
        assertEquals(ReadCue.REFUSED, cueFor(Outcome.INVALID))
        assertNotEquals(ReadTones.forCue(ReadCue.REFUSED), ReadTones.forCue(ReadCue.RECORDED))
    }

    @Test
    fun theFiveVerdictsAllSoundDifferent() {
        val specs = ReadCue.entries.map { ReadTones.forCue(it) }
        assertEquals("cues share a tone", specs.size, specs.toSet().size)
    }

    @Test
    fun recordedDuplicateAndUnknownAreDistinct() {
        val specs = listOf(ReadCue.RECORDED, ReadCue.DUPLICATE, ReadCue.UNKNOWN)
            .map { ReadTones.forCue(it) }
        assertEquals(3, specs.toSet().size)
    }

    @Test
    fun noSoundOutlastsTheGapBetweenTwoStudents() {
        // A cue that runs for a second would talk over the next student's tap.
        for (cue in ReadCue.entries) {
            val total = ReadTones.forCue(cue).totalMillis
            assertTrue("$cue runs $total ms", total in 1..500)
        }
    }

    @Test
    fun renderedBufferMatchesTheSpecifiedLength() {
        val spec = ReadTones.forCue(ReadCue.RECORDED)
        val pcm = ReadTones.render(spec)
        val expected = (spec.totalMillis.toLong() * ReadTones.SAMPLE_RATE / 1000L).toInt()
        assertTrue("expected about " + expected + " samples, got " + pcm.size, Math.abs(pcm.size - expected) <= 3)
    }

    @Test
    fun renderedBufferIsAudibleAndDoesNotClip() {
        val pcm = ReadTones.render(ReadTones.forCue(ReadCue.UNKNOWN))
        assertTrue(pcm.isNotEmpty())
        val peak = pcm.maxOf { Math.abs(it.toInt()) }
        assertTrue("silent buffer", peak > 1_000)
        assertTrue("clipped buffer", peak <= Short.MAX_VALUE.toInt())
    }

    @Test
    fun theSamplesActuallyMove() {
        // A DC buffer would be silent through a speaker but non-zero in a peak test.
        val pcm = ReadTones.render(ReadTones.forCue(ReadCue.RECORDED))
        val crossings = pcm.indices.drop(1).count { i ->
            (pcm[i - 1] >= 0) != (pcm[i] >= 0)
        }
        assertTrue("a 1046 Hz tone must cross zero many times, got " + crossings, crossings > 20)
    }
}
