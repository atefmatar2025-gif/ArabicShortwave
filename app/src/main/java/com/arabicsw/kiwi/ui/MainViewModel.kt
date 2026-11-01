package com.arabicsw.kiwi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arabicsw.kiwi.alerts.AlertScheduler
import com.arabicsw.kiwi.data.Broadcast
import com.arabicsw.kiwi.data.OnlineRadio
import com.arabicsw.kiwi.data.OnlineStation
import com.arabicsw.kiwi.data.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.arabicsw.kiwi.data.ScheduleRepository
import com.arabicsw.kiwi.player.PlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneOffset
import java.time.ZonedDateTime

data class Settings(
    val notifyMode: Int,
    val leadMinutes: Int,
    val autoSwitch: Boolean,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()

    val broadcasts: StateFlow<List<Broadcast>> = ScheduleRepository.items

    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val lastUpdated = MutableStateFlow(0L)
    val source = MutableStateFlow("")
    val favorites = MutableStateFlow(Prefs.favorites(app))
    val settings = MutableStateFlow(readSettings())

    /** UTC clock that ticks every 30 s so "on air" badges stay current. */
    val now: StateFlow<ZonedDateTime> = flow {
        while (true) {
            emit(ZonedDateTime.now(ZoneOffset.UTC))
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZonedDateTime.now(ZoneOffset.UTC))

    init {
        viewModelScope.launch(Dispatchers.IO) {
            ScheduleRepository.ensureLoaded(ctx)
            lastUpdated.value = ScheduleRepository.lastUpdated(ctx)
            source.value = Prefs.scheduleSource(ctx)
            val stale = System.currentTimeMillis() - lastUpdated.value > 3L * 24 * 3600 * 1000
            if (broadcasts.value.isEmpty() || stale) refresh()
        }
    }

    fun refresh() {
        if (loading.value) return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                ScheduleRepository.refresh(ctx)
                lastUpdated.value = ScheduleRepository.lastUpdated(ctx)
                source.value = Prefs.scheduleSource(ctx)
                AlertScheduler.reschedule(ctx)
            } catch (e: Exception) {
                error.value = "تعذّر تحديث الجدول: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                loading.value = false
            }
        }
    }

    // ---- internet radio
    val online = MutableStateFlow<List<OnlineStation>>(emptyList())
    val onlineLoading = MutableStateFlow(false)
    val onlineError = MutableStateFlow<String?>(null)
    val onlineFavorites = MutableStateFlow(Prefs.onlineFavorites(app))
    val onlineCountry = MutableStateFlow(Prefs.onlineCountry(app))

    fun setOnlineCountry(code: String) {
        Prefs.setOnlineCountry(ctx, code)
        onlineCountry.value = code
    }
    private var onlineJob: Job? = null

    fun loadOnline(query: String, force: Boolean = false) {
        onlineJob?.cancel()
        val country = onlineCountry.value
        val job = viewModelScope.launch {
            if (query.isNotBlank() && country.isEmpty()) delay(450)   // wait until the user stops typing
            onlineLoading.value = true
            onlineError.value = null
            try {
                online.value = when {
                    // a country is selected: its full list, filtered locally (works with Arabic names too)
                    country.isNotEmpty() -> OnlineRadio.byCountry(ctx, country, force).let { all ->
                        if (query.isBlank()) all
                        else all.filter { it.name.contains(query, ignoreCase = true) || it.tags.contains(query, ignoreCase = true) }
                    }
                    query.isBlank() -> OnlineRadio.arabic(ctx, force)
                    else -> OnlineRadio.search(query)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onlineError.value = "تعذّر تحميل قائمة الإذاعات: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                if (onlineJob === coroutineContext[Job]) onlineLoading.value = false
            }
        }
        onlineJob = job
    }

    fun toggleOnlineFavorite(st: OnlineStation) {
        onlineFavorites.value = Prefs.toggleOnlineFavorite(ctx, st)
    }

    fun playStream(st: OnlineStation) = PlayerService.playStream(ctx, st)

    fun toggleFavorite(station: String) {
        favorites.value = Prefs.toggleFavorite(ctx, station)
        AlertScheduler.reschedule(ctx)
    }

    fun play(b: Broadcast) = PlayerService.play(ctx, b)

    fun updateSettings(s: Settings) {
        Prefs.setNotifyMode(ctx, s.notifyMode)
        Prefs.setLeadMinutes(ctx, s.leadMinutes)
        Prefs.setAutoSwitch(ctx, s.autoSwitch)
        settings.value = s
        AlertScheduler.reschedule(ctx)
    }

    private fun readSettings() = Settings(
        notifyMode = Prefs.notifyMode(ctx),
        leadMinutes = Prefs.leadMinutes(ctx),
        autoSwitch = Prefs.autoSwitch(ctx),
    )
}
