package com.arabicsw.kiwi.player

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.arabicsw.kiwi.App
import com.arabicsw.kiwi.R
import com.arabicsw.kiwi.audio.KiwiClient
import com.arabicsw.kiwi.data.Broadcast
import com.arabicsw.kiwi.data.Geo
import com.arabicsw.kiwi.data.KiwiDirectory
import com.arabicsw.kiwi.data.Prefs
import com.arabicsw.kiwi.data.RankedReceiver
import com.arabicsw.kiwi.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class PlayerPhase { IDLE, SEARCHING, CONNECTING, PLAYING, ERROR }

data class PlayerState(
    val phase: PlayerPhase = PlayerPhase.IDLE,
    val station: String = "",
    val freqKhz: Double = 0.0,
    val timeLabel: String = "",
    val targetLabel: String = "",
    val status: String = "",
    val candidates: List<RankedReceiver> = emptyList(),
    val index: Int = -1,
    val rssi: Float? = null,
    val weak: Boolean = false,
    val mode: String = "am",
    /** What receiver distances are measured from (target area, or transmitter for mediumwave). */
    val refLabel: String = "من منطقة البث",
) {
    val active: Boolean get() = phase != PlayerPhase.IDLE
    val current: RankedReceiver? get() = candidates.getOrNull(index)
}

/**
 * Foreground service that finds the best KiwiSDR for a broadcast, plays it,
 * and moves to the next receiver when a receiver is busy, fails, or the
 * signal stays weak.
 */
class PlayerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var client: KiwiClient? = null
    private var searchJob: Job? = null
    private var generation = 0

    private var target = Geo.ARAB_WORLD
    private var autoFailovers = 0
    private var connectedAt = 0L
    private var weakSince = 0L
    private var rssiEma = Float.NaN
    private var lastRssiPush = 0L

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                goForeground("جارٍ البحث عن مستقبل…")
                startPlayback(intent)
            }
            ACTION_NEXT -> {
                val s = _state.value
                if (s.candidates.isNotEmpty()) connect((s.index + 1) % s.candidates.size, manual = true)
            }
            ACTION_SELECT -> {
                val i = intent.getIntExtra(EXTRA_INDEX, 0)
                if (i in _state.value.candidates.indices) connect(i, manual = true)
            }
            ACTION_MODE -> {
                val m = intent.getStringExtra(EXTRA_MODE) ?: "am"
                Prefs.setMode(this, m)
                _state.value = _state.value.copy(mode = m)
                val s = _state.value
                if (s.index >= 0) connect(s.index, manual = true)
            }
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            else -> if (!_state.value.active) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(intent: Intent) {
        val station = intent.getStringExtra(EXTRA_STATION) ?: "?"
        val freq = intent.getDoubleExtra(EXTRA_FREQ, 0.0)
        val tgt = intent.getStringExtra(EXTRA_TARGET).orEmpty()
        val itu = intent.getStringExtra(EXTRA_ITU).orEmpty()
        val time = intent.getStringExtra(EXTRA_TIME).orEmpty()

        client?.stop(); client = null
        generation++
        autoFailovers = 0
        val remarks = intent.getStringExtra(EXTRA_REMARKS).orEmpty()
        // Mediumwave/longwave travel mostly by ground wave (especially by day), so a
        // receiver near the transmitter hears it best. Shortwave: near the target area.
        val tx = if (freq < 1800) Geo.transmitterPoint(itu, remarks) else null
        target = tx ?: Geo.targetPoint(tgt, itu)
        val refLabel = if (tx != null) "من موقع الإرسال" else "من منطقة البث"
        acquireLocks()

        _state.value = PlayerState(
            phase = PlayerPhase.SEARCHING,
            station = station,
            freqKhz = freq,
            timeLabel = time,
            targetLabel = Geo.targetLabel(tgt),
            status = "جارٍ البحث عن أفضل مستقبل KiwiSDR…",
            mode = Prefs.mode(this),
            refLabel = refLabel,
        )

        searchJob?.cancel()
        searchJob = scope.launch {
            val all = runCatching { KiwiDirectory.load(this@PlayerService) }.getOrDefault(emptyList())
            val ranked = KiwiDirectory.rank(all, freq, target)
            if (ranked.isEmpty()) {
                setError(if (all.isEmpty()) "تعذّر تحميل قائمة المستقبلات. تحقق من الاتصال بالإنترنت."
                         else "لا يوجد مستقبل متاح حالياً لهذا التردد.")
                return@launch
            }
            _state.value = _state.value.copy(candidates = ranked)
            connect(0, manual = false)
        }
    }

    private fun connect(i: Int, manual: Boolean) {
        val s = _state.value
        val rr = s.candidates.getOrNull(i) ?: return
        if (manual) autoFailovers = 0
        client?.stop()
        val gen = ++generation
        connectedAt = System.currentTimeMillis()
        weakSince = 0L
        rssiEma = Float.NaN

        _state.value = s.copy(
            phase = PlayerPhase.CONNECTING,
            index = i,
            rssi = null,
            weak = false,
            status = "الاتصال بـ ${rr.rx.name.take(60)}…",
        )
        updateNotification()

        val c = KiwiClient(rr.rx.url, s.freqKhz, s.mode, object : KiwiClient.Callback {
            override fun onPlaying() = post(gen) {
                _state.value = _state.value.copy(phase = PlayerPhase.PLAYING, status = "يعمل الآن")
                updateNotification()
            }

            override fun onRssi(dbm: Float) = post(gen) { handleRssi(dbm) }

            override fun onFailure(kind: KiwiClient.Failure, detail: String) = post(gen) {
                val reason = when (kind) {
                    KiwiClient.Failure.BUSY -> "المستقبل مشغول"
                    KiwiClient.Failure.PASSWORD -> "المستقبل يتطلب كلمة مرور"
                    KiwiClient.Failure.DOWN -> "المستقبل متوقف"
                    KiwiClient.Failure.NO_AUDIO -> "لا يصل صوت"
                    KiwiClient.Failure.NETWORK -> "انقطع الاتصال"
                }
                failover("$reason، جارٍ الانتقال إلى مستقبل آخر…")
            }
        })
        client = c
        c.start()
    }

    private fun failover(msg: String) {
        val s = _state.value
        autoFailovers++
        if (autoFailovers > MAX_AUTO_FAILOVERS || s.candidates.size < 2 && autoFailovers > 1) {
            setError("تعذّر الاستقبال من المستقبلات المتاحة. جرّب لاحقاً أو اختر مستقبلاً يدوياً.")
            return
        }
        _state.value = s.copy(status = msg)
        connect((s.index + 1) % s.candidates.size, manual = false)
    }

    private fun handleRssi(dbm: Float) {
        rssiEma = if (rssiEma.isNaN()) dbm else rssiEma * 0.95f + dbm * 0.05f
        val now = System.currentTimeMillis()
        val settled = now - connectedAt > SETTLE_MS
        val weak = settled && rssiEma < WEAK_DBM
        if (weak) {
            if (weakSince == 0L) weakSince = now
            if (now - weakSince > WEAK_HOLD_MS && Prefs.autoSwitch(this) && _state.value.candidates.size > 1) {
                weakSince = 0L
                failover("الإشارة ضعيفة، جارٍ تجربة مستقبل آخر…")
                return
            }
        } else {
            weakSince = 0L
        }
        if (now - lastRssiPush > 250) {
            lastRssiPush = now
            _state.value = _state.value.copy(rssi = rssiEma, weak = weak)
        }
    }

    private fun post(gen: Int, block: () -> Unit) {
        scope.launch { if (gen == generation) block() }
    }

    private fun setError(msg: String) {
        client?.stop(); client = null
        _state.value = _state.value.copy(phase = PlayerPhase.ERROR, status = msg, rssi = null)
        updateNotification()
        releaseLocks()
    }

    private fun stopEverything() {
        searchJob?.cancel()
        client?.stop(); client = null
        generation++
        _state.value = PlayerState()
        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        client?.stop(); client = null
        _state.value = PlayerState()
        releaseLocks()
        scope.cancel()
        super.onDestroy()
    }

    // ---- notification & locks ----

    private fun goForeground(text: String) {
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(text),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun updateNotification() {
        val s = _state.value
        if (!s.active) return
        val nm = getSystemService(android.app.NotificationManager::class.java)
        runCatching { nm.notify(NOTIF_ID, buildNotification(s.status)) }
    }

    private fun buildNotification(text: String): Notification {
        val s = _state.value
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = PendingIntent.getService(
            this, 1, Intent(this, PlayerService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, PlayerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (s.station.isNotEmpty()) "${s.station} • ${freqText(s.freqKhz)}" else "موجات عربية"
        val sub = s.current?.rx?.location?.takeIf { it.isNotBlank() } ?: s.current?.rx?.name.orEmpty()
        return NotificationCompat.Builder(this, App.CH_PLAYER)
            .setSmallIcon(R.drawable.ic_stat_radio)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(sub.take(40))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "مستقبل آخر", next)
            .addAction(0, "إيقاف", stop)
            .build()
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArabicSW:player").apply {
                setReferenceCounted(false); acquire(4 * 60 * 60 * 1000L)
            }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ArabicSW:player").apply {
                setReferenceCounted(false); acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.release() }; wakeLock = null
        runCatching { wifiLock?.release() }; wifiLock = null
    }

    companion object {
        const val ACTION_PLAY = "play"
        const val ACTION_NEXT = "next"
        const val ACTION_SELECT = "select"
        const val ACTION_MODE = "mode"
        const val ACTION_STOP = "stop"
        const val EXTRA_STATION = "station"
        const val EXTRA_FREQ = "freq"
        const val EXTRA_TARGET = "target"
        const val EXTRA_ITU = "itu"
        const val EXTRA_TIME = "time"
        const val EXTRA_REMARKS = "remarks"
        const val EXTRA_INDEX = "index"
        const val EXTRA_MODE = "mode"

        private const val NOTIF_ID = 42
        private const val MAX_AUTO_FAILOVERS = 8
        private const val SETTLE_MS = 10_000L
        private const val WEAK_HOLD_MS = 20_000L
        /** ≈ S3–S4. Below this for 20 s the receiver is considered weak. */
        const val WEAK_DBM = -105f

        private val _state = MutableStateFlow(PlayerState())
        val state: StateFlow<PlayerState> = _state

        fun freqText(f: Double): String =
            if (f % 1.0 == 0.0) "${f.toLong()} kHz" else String.format(java.util.Locale.US, "%.1f kHz", f)

        fun playIntent(ctx: Context, b: Broadcast): Intent =
            Intent(ctx, PlayerService::class.java).setAction(ACTION_PLAY)
                .putExtra(EXTRA_STATION, b.station)
                .putExtra(EXTRA_FREQ, b.freqKhz)
                .putExtra(EXTRA_TARGET, b.target)
                .putExtra(EXTRA_ITU, b.itu)
                .putExtra(EXTRA_TIME, b.utcRange())
                .putExtra(EXTRA_REMARKS, b.remarks)

        fun play(ctx: Context, b: Broadcast) =
            ContextCompat.startForegroundService(ctx, playIntent(ctx, b))

        fun command(ctx: Context, action: String, extras: Intent.() -> Unit = {}) {
            if (!_state.value.active) return
            ctx.startService(Intent(ctx, PlayerService::class.java).setAction(action).apply(extras))
        }
    }
}
