package com.glassfiles.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.glassfiles.data.Strings
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun MediaPlayerScreen(filePath: String, onBack: () -> Unit) {
    val file = remember { File(filePath) }
    val ext = file.extension.lowercase()
    val isVideo = ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "m4v")

    if (isVideo) VideoPlayerView(file, onBack)
    else AudioPlayerView(file, onBack)
}

// ═══════════════════════════════════
// Video Player
// ═══════════════════════════════════

@Composable
private fun VideoPlayerView(file: File, onBack: () -> Unit) {
    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls) { delay(4000); showControls = false }
    }

    // Update progress
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoView?.let { currentPos = it.currentPosition; duration = it.duration.coerceAtLeast(1) }
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).clickable { showControls = !showControls }) {
        // Video
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.fromFile(file))
                    setOnPreparedListener { mp ->
                        duration = mp.duration
                        mp.start()
                    }
                    setOnCompletionListener { isPlaying = false }
                    videoView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Controls overlay
        AnimatedVisibility(showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f))) {
                // Top bar
                Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 40.dp, start = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { videoView?.stopPlayback(); onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(22.dp), tint = Color.White)
                    }
                    Text(file.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }

                // Center play/pause
                Box(Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape)
                    .background(Color.White.copy(0.2f))
                    .clickable {
                        if (isPlaying) videoView?.pause() else videoView?.start()
                        isPlaying = !isPlaying
                    }, contentAlignment = Alignment.Center) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                        Modifier.size(36.dp), tint = Color.White)
                }

                // Bottom: seek + time
                Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp)) {
                    Slider(
                        value = if (duration > 0) currentPos.toFloat() / duration else 0f,
                        onValueChange = { videoView?.seekTo((it * duration).toInt()); currentPos = (it * duration).toInt() },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Blue, inactiveTrackColor = Color.White.copy(0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(currentPos), color = Color.White.copy(0.8f), fontSize = 12.sp)
                        Text(formatTime(duration), color = Color.White.copy(0.8f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════
// Audio Player
// ═══════════════════════════════════

@Composable
private fun AudioPlayerView(file: File, onBack: () -> Unit) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }

    val mediaPlayer = remember {
        MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                duration = this.duration
            } catch (_: Exception) {}
        }
    }

    // Update progress
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try { currentPos = mediaPlayer.currentPosition } catch (_: Exception) {}
            delay(300)
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayer.stop(); mediaPlayer.release() } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        // Top bar
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).padding(top = 44.dp, start = 4.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { try { mediaPlayer.stop() } catch (_: Exception) {}; onBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue)
            }
            Text(Strings.shScreenRecord, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
        }

        // Album art placeholder
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(200.dp).clip(RoundedCornerShape(24.dp)).background(Blue.copy(0.1f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, Modifier.size(80.dp), tint = Blue)
                }
                Text(file.nameWithoutExtension, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 32.dp))
                Text("${file.extension.uppercase()} • ${formatFileSize(file.length())}", fontSize = 13.sp, color = TextSecondary)
            }
        }

        // Controls
        Column(Modifier.fillMaxWidth().background(SurfaceWhite).padding(horizontal = 24.dp, vertical = 16.dp)) {
            // Seek bar
            Slider(
                value = if (duration > 0) currentPos.toFloat() / duration else 0f,
                onValueChange = {
                    val pos = (it * duration).toInt()
                    try { mediaPlayer.seekTo(pos) } catch (_: Exception) {}
                    currentPos = pos
                },
                colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = SeparatorColor),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(currentPos), fontSize = 12.sp, color = TextSecondary)
                Text(formatTime(duration), fontSize = 12.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(8.dp))

            // Buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                // Rewind 10s
                IconButton(onClick = {
                    val pos = (currentPos - 10000).coerceAtLeast(0)
                    try { mediaPlayer.seekTo(pos) } catch (_: Exception) {}
                    currentPos = pos
                }) {
                    Icon(Icons.Rounded.Replay10, null, Modifier.size(32.dp), tint = TextPrimary)
                }

                // Play/Pause
                Box(Modifier.size(64.dp).clip(CircleShape).background(Blue)
                    .clickable {
                        if (isPlaying) { try { mediaPlayer.pause() } catch (_: Exception) {} }
                        else { try { mediaPlayer.start() } catch (_: Exception) {} }
                        isPlaying = !isPlaying
                    }, contentAlignment = Alignment.Center) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                        Modifier.size(32.dp), tint = Color.White)
                }

                // Forward 10s
                IconButton(onClick = {
                    val pos = (currentPos + 10000).coerceAtMost(duration)
                    try { mediaPlayer.seekTo(pos) } catch (_: Exception) {}
                    currentPos = pos
                }) {
                    Icon(Icons.Rounded.Forward10, null, Modifier.size(32.dp), tint = TextPrimary)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatFileSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / (1024.0 * 1024))
    else -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
}
