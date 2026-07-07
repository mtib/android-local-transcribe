package dev.mtib.localtranscribe.phone

import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mtib.localtranscribe.core.RecordingController
import dev.mtib.localtranscribe.core.export.ShareHelper
import dev.mtib.localtranscribe.core.session.RecordingSession
import dev.mtib.localtranscribe.phone.ui.Waveform
import dev.mtib.localtranscribe.phone.ui.formatDuration
import dev.mtib.localtranscribe.phone.ui.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingListScreen(
    vm: RecordingsViewModel,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
) {
    // Refresh on entry and again the instant a new session is saved (so it appears immediately).
    val lastCompleted by RecordingController.lastCompletedId.collectAsState()
    LaunchedEffect(lastCompleted) { vm.refresh() }
    val sessions by vm.sessions.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Local Transcribe") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                text = { Text("New recording") },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No recordings yet.\nTap “New recording” to start.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sessions, key = { it.id }) { session ->
                    RecordingCard(session) { onOpen(session.id) }
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(session: RecordingSession, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTimestamp(session.meta.createdAt), style = MaterialTheme.typography.titleMedium)
                Text(
                    formatDuration(session.meta.durationMs),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                session.transcript.ifBlank { "(no speech detected)" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ActiveRecordingScreen(onStopped: () -> Unit) {
    val context = LocalContext.current
    val isRecording by RecordingController.isRecording.collectAsState()
    val isPreparing by RecordingController.isPreparing.collectAsState()
    val elapsed by RecordingController.elapsedMs.collectAsState()
    val waveform by RecordingController.waveform.collectAsState()
    val committed by RecordingController.committed.collectAsState()
    val partial by RecordingController.partial.collectAsState()

    var startedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(isRecording) {
        if (isRecording) startedOnce = true
        if (startedOnce && !isRecording) onStopped()
    }

    val transcriptScroll = rememberScrollState()
    LaunchedEffect(committed, partial) { transcriptScroll.animateScrollTo(transcriptScroll.maxValue) }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (isPreparing) "Loading model…" else formatDuration(elapsed),
            fontSize = 44.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        Waveform(levels = waveform, modifier = Modifier.fillMaxWidth().height(96.dp))
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(transcriptScroll),
        ) {
            Text(committed, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
            if (partial.isNotBlank()) {
                Text(
                    (if (committed.isBlank()) "" else " ") + partial,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { RecordingService.stop(context) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text("  Stop")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailScreen(
    id: String,
    vm: RecordingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<RecordingSession?>(null) }
    LaunchedEffect(id) { session = vm.repository.get(id) }

    val player = remember { MediaPlayer() }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { runCatching { player.release() } }
    }

    var menuOpen by remember { mutableStateOf(false) }
    val s = session

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s?.let { formatTimestamp(it.meta.createdAt) } ?: "Recording") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Share text") }, onClick = {
                            menuOpen = false
                            s?.let { context.startActivity(ShareHelper.shareIntent(context, vm.repository, it, ShareHelper.Mode.TEXT)) }
                        })
                        DropdownMenuItem(text = { Text("Share audio") }, onClick = {
                            menuOpen = false
                            s?.let { context.startActivity(ShareHelper.shareIntent(context, vm.repository, it, ShareHelper.Mode.AUDIO)) }
                        })
                        DropdownMenuItem(text = { Text("Share audio + text") }, onClick = {
                            menuOpen = false
                            s?.let { context.startActivity(ShareHelper.shareIntent(context, vm.repository, it, ShareHelper.Mode.BOTH)) }
                        })
                    }
                    IconButton(onClick = {
                        s?.let { vm.delete(it.id); onBack() }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (s != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        val file = vm.repository.audioFile(s.id)
                        if (!playing && file.exists()) {
                            runCatching {
                                player.reset()
                                player.setDataSource(file.absolutePath)
                                player.setOnCompletionListener { playing = false }
                                player.prepare()
                                player.start()
                                playing = true
                            }
                        } else {
                            runCatching { player.pause() }
                            playing = false
                        }
                    }) {
                        Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                        Text(if (playing) "  Stop" else "  Play")
                    }
                    Spacer(Modifier.fillMaxWidth().weight(1f))
                    Text(formatDuration(s.meta.durationMs), color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text(
                        s.transcript.ifBlank { "(no speech detected)" },
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}
