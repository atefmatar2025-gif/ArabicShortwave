package com.arabicsw.kiwi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arabicsw.kiwi.data.Broadcast
import com.arabicsw.kiwi.data.Geo
import com.arabicsw.kiwi.data.OnlineStation
import com.arabicsw.kiwi.player.PlayerPhase
import com.arabicsw.kiwi.player.PlayerService
import com.arabicsw.kiwi.player.PlayerState
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private enum class Tab(val label: String) {
    LIVE("على الهواء"), NET("إذاعات الإنترنت"), NEXT("خلال 24 ساعة"), FAV("المفضلة"), ALL("الكل")
}

private data class RowItem(val b: Broadcast, val live: Boolean, val next: ZonedDateTime?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    vm: MainViewModel,
    player: PlayerState,
    onPlay: (Broadcast) -> Unit,
    onPlayStream: (OnlineStation) -> Unit,
    onOpenPlayer: () -> Unit,
    onSettings: () -> Unit,
) {
    val all by vm.broadcasts.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.LIVE) }
    var query by rememberSaveable { mutableStateOf("") }

    val rows = remember(all, favs, now, tab, query) {
        val q = query.trim()
        val limit = now.plusHours(24)
        all.asSequence()
            .filter { b ->
                q.isEmpty() || b.station.contains(q, ignoreCase = true) ||
                    b.freqKhz.toLong().toString().contains(q) ||
                    Geo.targetLabel(b.target).contains(q) || b.target.contains(q, ignoreCase = true)
            }
            .map { b -> RowItem(b, b.isOnAt(now), b.nextStartAfter(now)) }
            .filter { r ->
                when (tab) {
                    Tab.LIVE -> r.live
                    Tab.NEXT -> r.live || (r.next != null && r.next.isBefore(limit))
                    Tab.FAV -> r.b.station in favs
                    Tab.ALL -> true
                    Tab.NET -> false
                }
            }
            .sortedWith(
                when (tab) {
                    Tab.ALL -> compareBy<RowItem> { it.b.startMin }.thenBy { it.b.freqKhz }
                    else -> compareBy<RowItem> { if (it.live) 0 else 1 }
                        .thenBy { it.next?.toEpochSecond() ?: Long.MAX_VALUE }
                        .thenBy { it.b.freqKhz }
                }
            )
            .toList()
    }

    val country by vm.onlineCountry.collectAsStateWithLifecycle()
    LaunchedEffect(tab, query, country) {
        if (tab == Tab.NET) vm.loadOnline(query.trim())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("موجات عربية", fontWeight = FontWeight.Bold)
                        Text(
                            "UTC ${now.format(DateTimeFormatter.ofPattern("HH:mm"))} • محلي ${
                                now.withZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                            }",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            if (tab == Tab.NET) vm.loadOnline(query.trim(), force = true) else vm.refresh()
                        }) { Icon(Icons.Filled.Refresh, "تحديث") }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "الإعدادات") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { if (player.active) MiniPlayer(player, onOpenPlayer) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = {
                    Text(
                        when {
                            tab != Tab.NET -> "ابحث باسم المحطة أو التردد أو المنطقة"
                            country.isEmpty() -> "ابحث عن أي إذاعة: KBS، مونت كارلو، Medi1…"
                            else -> "ابحث داخل إذاعات ${countryName(country)}"
                        }
                    )
                },
                shape = RoundedCornerShape(14.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tab.values().forEach { t ->
                    FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) })
                }
            }

            error?.let { msg ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = vm::refresh) { Text("إعادة المحاولة") }
                    }
                }
            }

            if (tab == Tab.NET) {
                OnlineList(
                    vm = vm,
                    query = query.trim(),
                    playing = if (player.active && player.isStream) player.station else null,
                    onPlay = onPlayStream,
                )
            } else if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            all.isEmpty() && loading -> "جارٍ تنزيل جدول البث…"
                            all.isEmpty() -> "لا يوجد جدول بعد. اضغط زر التحديث."
                            tab == Tab.FAV -> "لا توجد محطات مفضلة. اضغط ☆ بجانب أي محطة لإضافتها وتلقي التنبيهات."
                            tab == Tab.LIVE -> "لا توجد فترات بث عربية على الهواء الآن."
                            else -> "لا توجد نتائج."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows) { r ->
                        BroadcastRow(
                            r = r,
                            now = now,
                            fav = r.b.station in favs,
                            onFav = { vm.toggleFavorite(r.b.station) },
                            onPlay = { onPlay(r.b) },
                        )
                    }
                    item {
                        Text(
                            "${rows.size} فترة • الجدول: ${source.ifEmpty { "EiBi" }} • المستقبلات: شبكة KiwiSDR العامة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastRow(r: RowItem, now: ZonedDateTime, fav: Boolean, onFav: () -> Unit, onPlay: () -> Unit) {
    val b = r.b
    Card(
        onClick = onPlay,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Dial: the frequency, the thing a listener actually tunes to
            Column(
                Modifier
                    .widthIn(min = 76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (b.freqKhz % 1.0 == 0.0) b.freqKhz.toLong().toString() else b.freqKhz.toString(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(b.band, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (r.live) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(OnAir))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        b.station,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (b.durationMin >= 1440) "على مدار الساعة" else "${b.localRange()} محلي • ${b.utcRange()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    buildString {
                        append("إلى: ").append(Geo.targetLabel(b.target))
                        val d = b.daysLabel()
                        if (d != "يومياً") append(" • ").append(d)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        r.live -> "على الهواء الآن"
                        r.next != null -> "يبدأ بعد ${humanDuration(Duration.between(now, r.next))}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (r.live) OnAir else MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onFav) {
                    if (fav) Icon(Icons.Filled.Star, "إزالة من المفضلة", tint = Amber)
                    else Icon(Icons.Outlined.StarBorder, "إضافة إلى المفضلة")
                }
                FilledTonalIconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, "استمع") }
            }
        }
    }
}

private val COUNTRIES = listOf(
    "" to "🌍 عربية",
    "PS" to "🇵🇸 فلسطين",
    "MA" to "🇲🇦 المغرب",
    "EG" to "🇪🇬 مصر",
    "DZ" to "🇩🇿 الجزائر",
)

private fun countryName(code: String) = when (code) {
    "PS" -> "فلسطين"
    "MA" -> "المغرب"
    "EG" -> "مصر"
    "DZ" -> "الجزائر"
    else -> code
}

@Composable
private fun OnlineList(vm: MainViewModel, query: String, playing: String?, onPlay: (OnlineStation) -> Unit) {
    val country by vm.onlineCountry.collectAsStateWithLifecycle()
    val list by vm.online.collectAsStateWithLifecycle()
    val loading by vm.onlineLoading.collectAsStateWithLifecycle()
    val err by vm.onlineError.collectAsStateWithLifecycle()
    val favs by vm.onlineFavorites.collectAsStateWithLifecycle()
    val favIds = remember(favs) { favs.map { it.uuid }.toSet() }
    val shownFavs = if (query.isEmpty() && country.isEmpty()) favs else emptyList()
    val rest = remember(list, favIds, shownFavs) {
        if (shownFavs.isEmpty()) list else list.filter { it.uuid !in favIds }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                COUNTRIES.forEach { (code, label) ->
                    FilterChip(
                        selected = country == code,
                        onClick = { vm.setOnlineCountry(code) },
                        label = { Text(label) },
                    )
                }
            }
        }
        if (country.isNotEmpty() && !loading && err == null && rest.isNotEmpty()) {
            item {
                Text(
                    "إذاعات ${countryName(country)}: ${rest.size}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item {
            Text(
                "بث مباشر عبر الإنترنت لإذاعات FM والأقمار الصناعية — ليس استقبالاً لاسلكياً.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        err?.let { msg ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { vm.loadOnline(query, force = true) }) { Text("إعادة المحاولة") }
                    }
                }
            }
        }
        if (shownFavs.isNotEmpty()) {
            item { Text("المفضلة", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(4.dp)) }
            items(shownFavs, key = { "f" + it.uuid }) { st ->
                OnlineRow(st, fav = true, playing = st.name == playing, onFav = { vm.toggleOnlineFavorite(st) }, onPlay = { onPlay(st) })
            }
            item {
                Text(
                    "الإذاعات العربية الأكثر استماعاً",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
                )
            }
        }
        if (loading && rest.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (!loading && rest.isEmpty() && err == null) {
            item {
                Text(
                    when {
                        query.isEmpty() -> "لا توجد إذاعات."
                        country.isNotEmpty() -> "لا توجد إذاعة بهذا الاسم في ${countryName(country)}."
                        else -> "لا توجد إذاعة بهذا الاسم. جرّب كتابة الاسم بالإنجليزية."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(rest, key = { it.uuid }) { st ->
            OnlineRow(st, fav = st.uuid in favIds, playing = st.name == playing, onFav = { vm.toggleOnlineFavorite(st) }, onPlay = { onPlay(st) })
        }
        item {
            Text(
                "دليل الإذاعات: radio-browser.info",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun OnlineRow(st: OnlineStation, fav: Boolean, playing: Boolean, onFav: () -> Unit, onPlay: () -> Unit) {
    Card(
        onClick = onPlay,
        colors = CardDefaults.cardColors(
            containerColor = if (playing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    st.countryCode.ifBlank { "🌐" },
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(st.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (st.info.isNotBlank()) {
                    Text(
                        st.info,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (playing) {
                    Text("● يعمل الآن — اضغط لفتح شاشة الاستماع", style = MaterialTheme.typography.labelSmall, color = OnAir)
                } else if (st.quality.isNotBlank()) {
                    Text(st.quality, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            IconButton(onClick = onFav) {
                if (fav) Icon(Icons.Filled.Star, "إزالة من المفضلة", tint = Amber)
                else Icon(Icons.Outlined.StarBorder, "إضافة إلى المفضلة")
            }
            FilledTonalIconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, "استمع") }
        }
    }
}

@Composable
private fun MiniPlayer(s: PlayerState, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (s.isStream) s.station else "${s.station} • ${PlayerService.freqText(s.freqKhz)}",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(s.status, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (s.phase == PlayerPhase.SEARCHING || s.phase == PlayerPhase.CONNECTING) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("فتح ›", fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun humanDuration(d: Duration): String {
    val mins = d.toMinutes().coerceAtLeast(0)
    val h = mins / 60
    val m = mins % 60
    return when {
        h == 0L -> "$m د"
        m == 0L -> "$h س"
        else -> "$h س $m د"
    }
}
