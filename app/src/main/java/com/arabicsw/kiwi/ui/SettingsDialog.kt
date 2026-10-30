package com.arabicsw.kiwi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arabicsw.kiwi.data.Prefs

@Composable
fun SettingsDialog(
    current: Settings,
    exactAllowed: Boolean,
    onRequestExact: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Settings) -> Unit,
) {
    var mode by remember { mutableStateOf(current.notifyMode) }
    var lead by remember { mutableStateOf(current.leadMinutes) }
    var auto by remember { mutableStateOf(current.autoSwitch) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الإعدادات") },
        confirmButton = { TextButton(onClick = { onSave(Settings(mode, lead, auto)) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("التنبيهات عند بدء البث", style = MaterialTheme.typography.titleSmall)
                listOf(
                    Prefs.NOTIFY_FAVORITES to "المحطات المفضلة فقط",
                    Prefs.NOTIFY_ALL to "كل المحطات العربية",
                    Prefs.NOTIFY_OFF to "إيقاف التنبيهات",
                ).forEach { (v, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = mode == v, onClick = { mode = v }).padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == v, onClick = { mode = v })
                        Text(label)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("وقت التنبيه", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 10, 15).forEach { m ->
                        FilterChip(
                            selected = lead == m,
                            onClick = { lead = m },
                            label = { Text(if (m == 0) "عند البدء" else "قبل $m د") },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الانتقال التلقائي عند ضعف الإشارة", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "إذا بقيت الإشارة أضعف من S4 لمدة 20 ثانية ينتقل التطبيق إلى المستقبل التالي.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                if (!exactAllowed && mode != Prefs.NOTIFY_OFF) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "لتصل التنبيهات في موعدها بالضبط، اسمح للتطبيق بجدولة المنبّهات الدقيقة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRequestExact) { Text("فتح الإذن") }
                }
            }
        },
    )
}
