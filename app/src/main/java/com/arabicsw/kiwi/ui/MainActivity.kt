package com.arabicsw.kiwi.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arabicsw.kiwi.player.PlayerService

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Root()
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Root() {
        val player by PlayerService.state.collectAsStateWithLifecycle()
        var showPlayer by rememberSaveable { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }

        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (showPlayer && player.active) {
            BackHandler { showPlayer = false }
            PlayerScreen(
                state = player,
                onClose = { showPlayer = false },
                onNext = { PlayerService.command(this, PlayerService.ACTION_NEXT) },
                onStop = {
                    PlayerService.command(this, PlayerService.ACTION_STOP)
                    showPlayer = false
                },
                onSelect = { i ->
                    PlayerService.command(this, PlayerService.ACTION_SELECT) { putExtra(PlayerService.EXTRA_INDEX, i) }
                },
                onMode = { m ->
                    PlayerService.command(this, PlayerService.ACTION_MODE) { putExtra(PlayerService.EXTRA_MODE, m) }
                },
            )
        } else {
            ScheduleScreen(
                vm = vm,
                player = player,
                onPlay = { b ->
                    vm.play(b)
                    showPlayer = true
                },
                onPlayStream = { st ->
                    vm.playStream(st)
                    showPlayer = true
                },
                onOpenPlayer = { showPlayer = true },
                onSettings = { showSettings = true },
            )
        }

        if (showSettings) {
            val s by vm.settings.collectAsStateWithLifecycle()
            SettingsDialog(
                current = s,
                exactAllowed = com.arabicsw.kiwi.alerts.AlertScheduler.canExact(this),
                onRequestExact = { requestExactAlarms() },
                onDismiss = { showSettings = false },
                onSave = {
                    vm.updateSettings(it)
                    showSettings = false
                },
            )
        }
    }

    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                startActivity(
                    Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
            }
        }
    }
}
