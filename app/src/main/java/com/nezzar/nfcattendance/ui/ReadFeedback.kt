package com.nezzar.nfcattendance.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.nezzar.nfcattendance.data.ReadCue
import com.nezzar.nfcattendance.data.ReadTones
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Plays the door's tones. One worker thread, one buffer per read, and nothing
 * kept alive between reads: a sound that has to be prepared before it is needed
 * is a sound that arrives after the next student has already tapped.
 *
 * The stream is USAGE_MEDIA on purpose. A phone in silent mode still plays media,
 * and a teacher who silenced the ringer must not lose the one signal that tells
 * her the tap landed - the whole point of the three tones.
 */
class ReadFeedback {

    private val player: ExecutorService = Executors.newSingleThreadExecutor { job ->
        Thread(job, "presencia-read-cue").apply { isDaemon = true }
    }

    @Volatile
    private var released = false

    fun play(cue: ReadCue) {
        if (released) return
        player.execute { tone(cue) }
    }

    private fun tone(cue: ReadCue) {
        val spec = ReadTones.forCue(cue)
        val pcm = ReadTones.render(spec)
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(ReadTones.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(spec.totalMillis.toLong() + 80L)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (blocked: Throwable) {
            // A read must never fail because the speaker is busy or the audio
            // hardware refused. The verdict on screen still says what happened.
        } finally {
            try {
                track?.stop()
            } catch (ignored: Throwable) {
                // Nothing to stop.
            }
            try {
                track?.release()
            } catch (ignored: Throwable) {
                // Already gone.
            }
        }
    }

    fun release() {
        released = true
        player.shutdownNow()
    }
}
