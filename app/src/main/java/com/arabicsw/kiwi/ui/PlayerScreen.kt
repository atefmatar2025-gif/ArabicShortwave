package com.arabicsw.kiwi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicsw.kiwi.data.Broadcast
import com.arabicsw.kiwi.data.RankedReceiver
import com.arabicsw.kiwi.player.PlayerPhase
import com.arabicsw.kiwi.player.PlayerService
import com.arabicsw.kiwi.player.PlayerState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerState,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onSelect: (Int) -> Unit,
    onMode: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الاستماع") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "رجوع") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isStream) {
                item { StreamHeader(state) }
                item { SignalCard(state) }
                item {
                    OutlinedButton(onClick = onStop) {
                        Icon(Icons.Filled.Stop, null)
                        Spacer(Modifier.width(6.dp))
                        Text("إيقاف")
                    }
                }
                return@LazyColumn
            }
            item { StationHeader(state) }
            item { SignalCard(state) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onNext, enabled = state.candidates.size > 1) {
                        Icon(Icons.Filled.SkipNext, null)
                        Spacer(Modifier.width(6.dp))
                        Text("مستقبل آخر")
                    }
                    OutlinedButton(onClick = onStop) {
                        Icon(Icons.Filled.Stop, null)
                        Spacer(Modifier.width(6.dp))
                        Text("إيقاف")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("نمط الاستقبال:", style = MaterialTheme.typography.labelLarge)
                    FilterChip(selected = state.mode == "am", onClick = { onMode("am") }, label = { Text("AM") })
                    FilterChip(selected = state.mode == "sam", onClick = { onMode("sam") }, label = { Text("SAM (ضد الخفوت)") })
                }
            }
            if (state.candidates.isNotEmpty()) {
                item {
                    Text(
                        if (state.refLabel == "من موقع الإرسال") "المستقبلات المرشّحة (الأقرب إلى موقع الإرسال أولاً)"
                        else "المستقبلات المرشّحة (الأقرب إلى منطقة البث أولاً)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                itemsIndexed(state.candidates) { i, rr ->
                    ReceiverRow(rr, selected = i == state.index, onClick = { onSelect(i) })
                }
            }
        }
    }
}

@Composable
private fun StationHeader(s: PlayerState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(s.station, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    PlayerService.freqText(s.freqKhz),
                    color = Amber,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                )
                Spacer(Modifier.width(10.dp))
                Text(Broadcast.bandFor(s.freqKhz), color = MaterialTheme.colorScheme.onPrimary)
            }
            Text(
                "${s.timeLabel} • إلى: ${s.targetLabel}",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StreamHeader(s: PlayerState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("إذاعة إنترنت", color = Amber, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(s.station, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            if (s.streamInfo.isNotBlank()) {
                Text(
                    s.streamInfo,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SignalCard(s: PlayerState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.phase == PlayerPhase.SEARCHING || s.phase == PlayerPhase.CONNECTING) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    s.status,
                    color = if (s.phase == PlayerPhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
            s.current?.let { rr ->
                Spacer(Modifier.height(10.dp))
                Text(rr.rx.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        rr.rx.location.takeIf { it.isNotBlank() },
                        "${rr.distanceKm.roundToInt()} كم ${s.refLabel}",
                        "المستخدمون ${rr.rx.users}/${rr.rx.usersMax}",
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val rssi = s.rssi
            if (rssi != null) {
                Spacer(Modifier.height(12.dp))
                val frac = ((rssi + 127f) / 94f).coerceIn(0f, 1f)   // -127 … -33 dBm
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = if (s.weak) OnAir else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${sUnits(rssi)} • ${rssi.roundToInt()} dBm" + if (s.weak) " — إشارة ضعيفة" else "",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.weak) OnAir else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReceiverRow(rr: RankedReceiver, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(rr.rx.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${rr.distanceKm.roundToInt()} كم • ${rr.rx.users}/${rr.rx.usersMax} مستخدم • SNR ${rr.rx.snr} dB" +
                    (rr.rx.location.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** S-meter: S9 = -73 dBm, 6 dB per S-unit. */
fun sUnits(dbm: Float): String {
    if (dbm >= -73f) return "S9+${(dbm + 73f).roundToInt()}"
    val s = (9 + (dbm + 73f) / 6f).roundToInt().coerceIn(0, 9)
    return "S$s"
}
