package com.moviebox.app.player

import java.io.File
import java.nio.charset.Charset
import java.util.Locale

data class SubtitleCue(val start: Long, val end: Long, val text: String)

/**
 * External SRT/VTT subtitle parser, charset detector, and time-shifted WebVTT writer.
 * Ported from VideoPlayerOffline v1.4.
 */
object SubtitleTools {
    private val ts = Regex("((?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{1,3})\\s*-->\\s*((?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{1,3})")

    fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) String(bytes, Charset.forName("windows-1252")) else utf8
    }

    fun parse(raw: String): List<SubtitleCue> {
        val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val cues = ArrayList<SubtitleCue>()
        for (block in text.split(Regex("\n{2,}"))) {
            val lines = block.trim().split("\n")
            val idx = lines.indexOfFirst { ts.containsMatchIn(it) }
            if (idx < 0) continue
            val m = ts.find(lines[idx]) ?: continue
            val body = lines.drop(idx + 1).joinToString("\n").trim()
            if (body.isEmpty()) continue
            cues += SubtitleCue(parseTime(m.groupValues[1]), parseTime(m.groupValues[2]), body)
        }
        return cues
    }

    private fun parseTime(s: String): Long {
        val parts = s.replace(',', '.').split(":")
        val h: Long
        val m: Long
        val secPart: String
        if (parts.size == 3) {
            h = parts[0].toLongOrNull() ?: 0L
            m = parts[1].toLongOrNull() ?: 0L
            secPart = parts[2]
        } else {
            h = 0L
            m = parts[0].toLongOrNull() ?: 0L
            secPart = parts.getOrElse(1) { "0" }
        }
        val sec = secPart.substringBefore('.').toLongOrNull() ?: 0L
        val ms = secPart.substringAfter('.', "0").padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return ((h * 60 + m) * 60 + sec) * 1000 + ms
    }

    private fun fmt(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        val r = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, r)
    }

    fun writeShiftedVtt(dir: File, cues: List<SubtitleCue>, delayMs: Long, n: Int): File {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val sb = StringBuilder("WEBVTT\n\n")
        for (c in cues) {
            val end = c.end + delayMs
            if (end <= 0) continue
            sb.append(fmt((c.start + delayMs).coerceAtLeast(0)))
                .append(" --> ")
                .append(fmt(end))
                .append('\n')
                .append(c.text)
                .append("\n\n")
        }
        val f = File(dir, "sub_$n.vtt")
        f.writeText(sb.toString())
        return f
    }
}
