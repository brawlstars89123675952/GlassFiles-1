package com.glassfiles.ui.components

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.glassfiles.data.*
import com.glassfiles.data.Strings
import com.glassfiles.data.FavoritesManager
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.CoroutineScope

enum class FileAction {
    OPEN, OPEN_WITH, COPY, MOVE, RENAME, DELETE, TRASH, SHARE,
    COMPRESS, DECOMPRESS, PROPERTIES, FAVORITE, INSTALL_APK,
    OPEN_IN_TERMINAL, COPY_PATH, AI_SUMMARIZE, AI_DESCRIBE, CONVERT_IMAGE, TAG, ENCRYPT, BATCH_RENAME,
    UPLOAD_GITHUB, SELECT
}

@Composable
fun FileContextMenu(
    file: FileItem?,
    context: Context,
    scope: CoroutineScope,
    trashManager: TrashManager,
    favoritesManager: FavoritesManager,
    onDismiss: () -> Unit,
    onAction: (FileAction, FileItem) -> Unit
) {
    if (file == null) return

    val isFav = remember(file) { favoritesManager.isFavorite(file.path) }
    val isArchive = file.extension.lowercase() in listOf("zip", "rar", "7z", "tar", "gz", "tar.gz", "tgz")
    val isApk = file.extension.lowercase() == "apk"
    val isImage = file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
    val menuBg = if (ThemeState.isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val divColor = if (ThemeState.isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
            contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(horizontal = 48.dp).widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(14.dp)).background(menuBg)
            ) {
                // Quick actions row
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    QuickBtn(Icons.Rounded.ContentCopy, Strings.copy) { onAction(FileAction.COPY, file); onDismiss() }
                    QuickBtn(Icons.Rounded.DriveFileMove, Strings.move) { onAction(FileAction.MOVE, file); onDismiss() }
                    QuickBtn(Icons.Rounded.Share, Strings.share) { onAction(FileAction.SHARE, file); onDismiss() }
                }

                Divider(divColor)

                // Menu items
                MenuItem(Icons.Rounded.Info, Strings.properties) { onAction(FileAction.PROPERTIES, file); onDismiss() }
                MenuItem(Icons.Rounded.Edit, Strings.rename) { onAction(FileAction.RENAME, file); onDismiss() }

                if (!file.isDirectory) {
                    MenuItem(Icons.Rounded.Compress, Strings.compress) { onAction(FileAction.COMPRESS, file); onDismiss() }
                } else {
                    MenuItem(Icons.Rounded.FolderZip, Strings.compressFolder) { onAction(FileAction.COMPRESS, file); onDismiss() }
                }

                if (isArchive) {
                    MenuItem(Icons.Rounded.FolderZip, Strings.decompress) { onAction(FileAction.DECOMPRESS, file); onDismiss() }
                }

                MenuItem(
                    if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    if (isFav) Strings.removeFromFavorites else Strings.addToFavorites,
                    if (isFav) Color(0xFFFFB300) else null
                ) { onAction(FileAction.FAVORITE, file); onDismiss() }

                if (isApk) {
                    MenuItem(Icons.Rounded.InstallMobile, Strings.installApk) { onAction(FileAction.INSTALL_APK, file); onDismiss() }
                }

                MenuItem(Icons.Rounded.Terminal, Strings.openInTerminal) { onAction(FileAction.OPEN_IN_TERMINAL, file); onDismiss() }
                MenuItem(Icons.Rounded.ContentPaste, Strings.copyPath) { onAction(FileAction.COPY_PATH, file); onDismiss() }

                Divider(divColor)

                // Convert for images
                if (isImage) {
                    MenuItem(Icons.Rounded.Transform, Strings.convertImage, Blue) { onAction(FileAction.CONVERT_IMAGE, file); onDismiss() }
                }

                // AI actions
                if (isImage) {
                    MenuItem(Icons.Rounded.AutoAwesome, Strings.describePhoto, Color(0xFF00B894)) { onAction(FileAction.AI_DESCRIBE, file); onDismiss() }
                }
                if (!file.isDirectory) {
                    MenuItem(Icons.Rounded.Summarize, Strings.summarizeFile, Color(0xFF6C5CE7)) { onAction(FileAction.AI_SUMMARIZE, file); onDismiss() }
                }

                Divider(divColor)

                // Tags
                MenuItem(Icons.Rounded.Label, Strings.tags) { onAction(FileAction.TAG, file); onDismiss() }

                if (!file.isDirectory) {
                    val isEnc = file.name.endsWith(".enc")
                    MenuItem(if (isEnc) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        if (isEnc) Strings.decrypt else Strings.encrypt) { onAction(FileAction.ENCRYPT, file); onDismiss() }
                }

                // Upload to GitHub
                if (com.glassfiles.data.github.GitHubManager.isLoggedIn(context)) {
                    MenuItem(Icons.Rounded.Cloud, Strings.ghUploadToGitHub, Color(0xFF238636)) { onAction(FileAction.UPLOAD_GITHUB, file); onDismiss() }
                }

                Divider(divColor)

                // Select
                MenuItem(Icons.Rounded.CheckCircle, Strings.selectMode) { onAction(FileAction.SELECT, file); onDismiss() }

                // Delete — red, at bottom
                MenuItem(Icons.Rounded.Delete, Strings.delete, Color(0xFFFF3B30)) { onAction(FileAction.TRASH, file); onDismiss() }
            }
        }
    }
}

@Composable
private fun QuickBtn(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = Blue)
        Text(label, fontSize = 11.sp, color = TextPrimary)
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint ?: TextSecondary)
        Text(label, fontSize = 15.sp, color = tint ?: TextPrimary)
    }
}

@Composable
private fun Divider(color: Color) {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(color))
}
