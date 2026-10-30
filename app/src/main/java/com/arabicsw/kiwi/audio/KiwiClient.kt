package com.arabicsw.kiwi.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.arabicsw.kiwi.data.Http
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Minimal KiwiSDR audio client (same protocol as jks-prv/kiwiclient):
 *  ws://host:port/<timestamp>/SND
 *  → SET auth t=kiwi p=
 *  ← MSG audio_rate=12000      → SET AR OK in=12000 out=44100
 *  ← MSG sample_rate=12001.13  → SET mod=am low_cut=-4900 high_cut=4900 freq=<kHz>, AGC, compression=0 …
 *  ← SND frames: "SND" | flags(1) | seq(4, LE) | smeter(2, BE) | audio
 *     audio = 16-bit big-endian PCM, or IMA-ADPCM if flags & 0x10
 *  → SET keepalive every few seconds
 */
class KiwiClient(
    private val baseUrl: String,
    private val freqKhz: Double,
    private val mode: String,
    private val callback: Callback,
) {
    enum class Failure { BUSY, PASSWORD, DOWN, NETWORK, NO_AUDIO }

    interface Callback {
        fun onPlaying()
        fun onRssi(dbm: Float)
        fun onFailure(kind: Failure, detail: String)
    }

    @Volatile private var closed = false
    @Volatile private var failed = false
    @Volatile private var audioStarted = false
    @Volatile private var sampleRate = 12000.0

    private var ws: WebSocket? = null
    private val timers = Executors.newSingleThreadScheduledExecutor()
    private val queue = LinkedBlockingQueue<ShortArray>(400)
    private var audioThread: Thread? = null
    private val adpcm = ImaAdpcm()

    fun start() {
        val req = Request.Builder()
            .url(wsUrl(baseUrl))
            .header("User-Agent", "ArabicSW-Android/1.0")
            .build()
        ws = Http.wsClient.newWebSocket(req, listener)
        // If no audio arrives within 20 s, give up on this receiver.
        timers.schedule(Runnable {
            if (!audioStarted && !closed) fail(Failure.NO_AUDIO, "لم يصل صوت من المستقبل")
        }, 20, TimeUnit.SECONDS)
    }

    fun stop() {
        closed = true
        runCatching { ws?.close(1000, null) }
        ws = null
        timers.shutdownNow()
        audioThread?.interrupt()
        audioThread = null
        queue.clear()
    }

    private fun send(msg: String) {
        if (!closed) ws?.send(msg)
    }

    private fun fail(kind: Failure, detail: String) {
        if (closed || failed) return
        failed = true
        stop()
        callback.onFailure(kind, detail)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            send("SET auth t=kiwi p=")
            send("SET ident_user=ArabicSW")
            runCatching { timers.scheduleAtFixedRate(Runnable { send("SET keepalive") }, 5, 5, TimeUnit.SECONDS) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (closed) return
            val b = bytes.toByteArray()
            if (b.size < 3) return
            when (String(b, 0, 3, Charsets.US_ASCII)) {
                "MSG" -> if (b.size > 4) handleMsg(String(b, 4, b.size - 4, Charsets.UTF_8))
                "SND" -> handleSnd(b)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.startsWith("MSG ")) handleMsg(text.substring(4))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            fail(Failure.NETWORK, t.message ?: "خطأ في الاتصال")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            fail(Failure.NETWORK, "أُغلق الاتصال")
        }
    }

    private fun handleMsg(body: String) {
        for (pair in body.trim().split(' ')) {
            val eq = pair.indexOf('=')
            val name = if (eq >= 0) pair.substring(0, eq) else pair
            val value = if (eq >= 0) pair.substring(eq + 1) else ""
            when (name) {
                "too_busy" -> fail(Failure.BUSY, "كل القنوات مشغولة")
                "badp" -> if (value != "0") {
                    fail(if (value == "5") Failure.BUSY else Failure.PASSWORD, "المستقبل يرفض الاتصال ($value)")
                }
                "down" -> fail(Failure.DOWN, "المستقبل متوقف")
                "redirect" -> fail(Failure.BUSY, "المستقبل يحوّل إلى عنوان آخر")
                "audio_rate" -> value.toDoubleOrNull()?.let {
                    send("SET AR OK in=${it.roundToInt()} out=44100")
                }
                "sample_rate" -> value.toDoubleOrNull()?.let {
                    sampleRate = it
                    startAudio()
                    configureReceiver()
                }
            }
        }
    }

    private fun configureReceiver() {
        val m = if (mode == "sam") "sam" else "am"
        send("SET squelch=0 max=0")
        send("SET genattn=0")
        send("SET gen=0 mix=-1")
        send(String.format(Locale.US, "SET mod=%s low_cut=-4900 high_cut=4900 freq=%.3f", m, freqKhz))
        send("SET agc=1 hang=0 thresh=-100 slope=6 decay=1000 manGain=50")
        send("SET compression=0")
        send("SET keepalive")
    }

    private fun handleSnd(b: ByteArray) {
        if (b.size < 10) return
        val flags = b[3].toInt() and 0xff
        val smeter = ((b[8].toInt() and 0xff) shl 8) or (b[9].toInt() and 0xff)
        callback.onRssi((0.1 * smeter - 127).toFloat())

        val dataLen = b.size - 10
        if (dataLen <= 0) return
        val samples: ShortArray = if (flags and FLAG_COMPRESSED != 0) {
            adpcm.decode(b, 10)
        } else {
            val little = flags and FLAG_LITTLE_ENDIAN != 0
            ShortArray(dataLen / 2) { i ->
                val hi: Int
                val lo: Int
                if (little) {
                    lo = b[10 + 2 * i].toInt() and 0xff; hi = b[11 + 2 * i].toInt()
                } else {
                    hi = b[10 + 2 * i].toInt(); lo = b[11 + 2 * i].toInt() and 0xff
                }
                ((hi shl 8) or lo).toShort()
            }
        }
        if (!queue.offer(samples)) {
            queue.poll(); queue.offer(samples)   // drop the oldest block if we fall behind
        }
        if (!audioStarted) {
            audioStarted = true
            callback.onPlaying()
        }
    }

    private fun startAudio() {
        if (audioThread != null) return
        val rate = sampleRate.roundToInt().coerceIn(4000, 48000)
        val t = Thread({ audioLoop(rate) }, "kiwi-audio")
        t.priority = Thread.MAX_PRIORITY
        audioThread = t
        t.start()
    }

    private fun buildTrack(rate: Int): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // must be a positive multiple of the frame size (2 bytes); ≈ 1 s of audio
        val bytes = max(if (minBuf > 0) minBuf * 4 else 0, rate * 2)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun audioLoop(rate: Int) {
        val track = try {
            buildTrack(rate)
        } catch (e: Exception) {
            // some devices dislike odd rates such as 12001 Hz; 12000 Hz is inaudibly different
            try {
                buildTrack(12000)
            } catch (e2: Exception) {
                fail(Failure.NO_AUDIO, "تعذّر تشغيل الصوت على هذا الجهاز: ${e2.message}")
                return
            }
        }
        try {
            // pre-buffer ~0.7 s to ride over network jitter
            val pre = ArrayList<ShortArray>()
            var have = 0
            while (!closed && have < rate * 7 / 10) {
                val blk = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                pre += blk; have += blk.size
            }
            if (closed) return
            track.play()
            for (blk in pre) track.write(blk, 0, blk.size)
            while (!closed && !Thread.currentThread().isInterrupted) {
                val blk = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                track.write(blk, 0, blk.size)
            }
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            fail(Failure.NO_AUDIO, "خطأ في الصوت: ${e.message}")
        } finally {
            runCatching { track.pause(); track.flush(); track.stop() }
            runCatching { track.release() }
        }
    }

    companion object {
        private const val FLAG_COMPRESSED = 0x10
        private const val FLAG_LITTLE_ENDIAN = 0x80

        fun wsUrl(base: String): String {
            var u = base.trim().trimEnd('/')
            u = when {
                u.startsWith("https://") -> "wss://" + u.removePrefix("https://")
                u.startsWith("http://") -> "ws://" + u.removePrefix("http://")
                u.startsWith("ws") -> u
                else -> "ws://$u"
            }
            val ts = (System.currentTimeMillis() / 1000) and 0xffffffffL
            return "$u/$ts/SND"
        }
    }
}

/** IMA-ADPCM decoder as used by KiwiSDR (low nibble first). */
class ImaAdpcm {
    private var index = 0
    private var prev = 0

    fun decode(b: ByteArray, offset: Int): ShortArray {
        val n = b.size - offset
        val out = ShortArray(n * 2)
        for (i in 0 until n) {
            val v = b[offset + i].toInt() and 0xff
            out[2 * i] = sample(v and 0x0f)
            out[2 * i + 1] = sample(v shr 4)
        }
        return out
    }

    private fun sample(code: Int): Short {
        val step = STEP[index]
        index = (index + INDEX_ADJ[code]).coerceIn(0, STEP.size - 1)
        var diff = step shr 3
        if (code and 1 != 0) diff += step shr 2
        if (code and 2 != 0) diff += step shr 1
        if (code and 4 != 0) diff += step
        if (code and 8 != 0) diff = -diff
        prev = (prev + diff).coerceIn(-32768, 32767)
        return prev.toShort()
    }

    companion object {
        private val STEP = intArrayOf(
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34,
            37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143,
            157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494,
            544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552,
            1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026,
            4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442,
            11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623,
            27086, 29794, 32767,
        )
        private val INDEX_ADJ = intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8)
    }
}
