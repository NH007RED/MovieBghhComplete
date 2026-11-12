package com.moviebox.app.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Delays audio by [delayMs] (0..2000 ms, audio plays LATER) using a PCM-16 ring buffer.
 * Ported from VideoPlayerOffline v1.4.
 */
@UnstableApi
class AudioDelayProcessor : BaseAudioProcessor() {
    @Volatile
    var delayMs: Int = 0
    private var ring = ByteArray(0)
    private var pos = 0
    private var frameSize = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        frameSize = inputAudioFormat.channelCount * 2
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val frames = (inputAudioFormat.sampleRate.toLong() * delayMs / 1000).toInt()
        val wanted = frames * frameSize
        if (wanted != ring.size) {
            ring = ByteArray(wanted)
            pos = 0
        }
        val out = replaceOutputBuffer(remaining)
        if (ring.isEmpty()) {
            out.put(inputBuffer)
        } else {
            while (inputBuffer.hasRemaining()) {
                out.put(ring[pos])
                ring[pos] = inputBuffer.get()
                pos = (pos + 1) % ring.size
            }
        }
        out.flip()
    }

    override fun onQueueEndOfStream() {
        if (ring.isNotEmpty()) {
            val out = replaceOutputBuffer(ring.size)
            out.put(ring, pos, ring.size - pos)
            out.put(ring, 0, pos)
            out.flip()
            ring = ByteArray(0)
            pos = 0
        }
    }

    override fun onFlush() {
        ring = ByteArray(0)
        pos = 0
    }

    override fun onReset() {
        ring = ByteArray(0)
        pos = 0
    }
}
