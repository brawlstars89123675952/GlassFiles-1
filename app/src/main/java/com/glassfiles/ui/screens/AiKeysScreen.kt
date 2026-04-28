package com.glassfiles.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.models.AiProviderId

/** Screen for entering / managing the API key for each [AiProviderId]. */
@Composable
fun AiKeysScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val keyValues = remember { mutableStateMapOf<AiProviderId, String>() }
    val showKey = remember { mutableStateMapOf<AiProviderId, Boolean>() }
    val savedFlash = remember { mutableStateMapOf<AiProviderId, Boolean>() }

    LaunchedEffect(Unit) {
        AiProviderId.entries.forEach { keyValues[it] = AiKeyStore.getKey(context, it) }
    }

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 44.dp, start = 4.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = colors.onSurface)
            }
            Text(
                Strings.aiKeys,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(AiProviderId.entries) { provider ->
                ProviderKeyCard(
                    provider = provider,
                    value = keyValues[provider].orEmpty(),
                    onValueChange = { keyValues[provider] = it },
                    revealed = showKey[provider] == true,
                    onToggleReveal = { showKey[provider] = !(showKey[provider] ?: false) },
                    saved = savedFlash[provider] == true,
                    onSave = {
                        AiKeyStore.saveKey(context, provider, keyValues[provider].orEmpty())
                        savedFlash[provider] = true
                    },
                    onClear = {
                        keyValues[provider] = ""
                        AiKeyStore.saveKey(context, provider, "")
                        savedFlash[provider] = false
                    },
                    onOpenConsole = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(provider.consoleUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    onFocus = { savedFlash[provider] = false },
                )
            }
        }
    }
}

@Composable
private fun ProviderKeyCard(
    provider: AiProviderId,
    value: String,
    onValueChange: (String) -> Unit,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    saved: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onOpenConsole: () -> Unit,
    onFocus: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                provider.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (value.isBlank()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.error.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        Strings.aiNoKey,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        color = colors.error,
                    )
                }
            }
        }

        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                onFocus()
            },
            placeholder = { Text(Strings.aiKeyHint, color = colors.onSurfaceVariant) },
            singleLine = true,
            visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggleReveal) {
                    Icon(
                        if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        null,
                        tint = colors.onSurfaceVariant,
                    )
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                cursorColor = colors.primary,
                focusedIndicatorColor = colors.primary,
                unfocusedIndicatorColor = colors.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenConsole() }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    null,
                    Modifier.size(14.dp),
                    tint = colors.primary,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    Strings.aiKeyGetHere,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary,
                )
            }

            Spacer(Modifier.weight(1f))

            if (value.isNotBlank()) {
                TextButton(onClick = onClear) {
                    Icon(Icons.Rounded.Clear, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                    Spacer(Modifier.size(4.dp))
                    Text(Strings.aiKeyClear, fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }

            TextButton(onClick = onSave, enabled = value.isNotBlank()) {
                Text(
                    if (saved) Strings.aiKeySaved else Strings.aiKeySave,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (saved) colors.tertiary else colors.primary,
                )
            }
        }
    }
}
