package com.glassfiles

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.BuildConfig
import com.glassfiles.data.AppSettings
import com.glassfiles.security.SecurityManager
import com.glassfiles.ui.GlassFilesApp
import com.glassfiles.ui.screens.OnboardingScreen
import com.glassfiles.ui.theme.*

class MainActivity : ComponentActivity() {

    val hasPermission = mutableStateOf(false)
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var appSettings: AppSettings

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { checkAndUpdatePermission() }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkAndUpdatePermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on while app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        checkAndUpdatePermission()
        if (!hasPermission.value) requestStoragePermission()

        // Request battery optimization exemption
        requestBatteryOptimization()

        appSettings = AppSettings(this)

        // ── Security checks ──
        // Reset hashes on app update
        val currentVersion = try { packageManager.getPackageInfo(packageName, 0).versionCode } catch (_: Exception) { 0 }
        val prefs = getSharedPreferences("gf_sec", MODE_PRIVATE)
        val storedVersion = prefs.getInt("v", 0)
        if (currentVersion != storedVersion) {
            SecurityManager.resetHashes(this)
            prefs.edit().putInt("v", currentVersion).apply()
        }

        val securityResult = SecurityManager.performChecks(this)

        setContent {
            GlassFilesTheme(themeMode = appSettings.themeMode) {
                var showSplash by remember { mutableStateOf(true) }
                var showOnboarding by remember { mutableStateOf(!appSettings.onboardingDone) }
                val isTampered = remember { !securityResult.isSecure && !BuildConfig.DEBUG }
                LaunchedEffect(Unit) {
                    delay(1500)
                    showSplash = false
                }

                // Watch permission changes for onboarding
                val permState = hasPermission.value

                AnimatedContent(
                    targetState = when {
                        showSplash -> "splash"
                        isTampered -> "blocked"
                        showOnboarding -> "onboarding"
                        else -> "app"
                    }, label = "main",
                    transitionSpec = {
                        fadeIn(tween(600)) togetherWith fadeOut(tween(400))
                    }
                ) { state ->
                    when (state) {
                        "splash" -> SplashScreen()
                        "blocked" -> TamperedScreen { finish() }
                        "onboarding" -> OnboardingScreen(
                            appSettings = appSettings,
                            hasPermission = permState,
                            onRequestPermission = { requestStoragePermission() },
                            onComplete = { showOnboarding = false }
                        )
                        else -> GlassFilesApp(
                            hasPermission = permState,
                            onRequestPermission = { requestStoragePermission() },
                            appSettings = appSettings
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndUpdatePermission()
        acquireWakeLock()
    }

    override fun onPause() {
        super.onPause()
        // Don't release wake lock — keep terminal alive in background
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    // Handle config changes without recreating activity
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Activity not recreated — terminal sessions preserved
    }

    private fun checkAndUpdatePermission() {
        hasPermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            ))
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun requestBatteryOptimization() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (_: Exception) {}
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "GlassFiles:TerminalWakeLock"
            )
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours max
        }
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(SurfaceLight), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Folder, null, Modifier.size(80.dp), tint = Blue)
            Text("Glass Files", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(com.glassfiles.data.Strings.splashSubtitle, fontSize = 14.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun TamperedScreen(onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(SurfaceLight), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Rounded.Shield, null, Modifier.size(72.dp), tint = androidx.compose.ui.graphics.Color(0xFFFF3B30))
            Text("Glass Files", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                com.glassfiles.data.Strings.securityViolation,
                fontSize = 15.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Button(
                onClick = onExit,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFF3B30)
                )
            ) {
                Text(com.glassfiles.data.Strings.close, color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp)
            }
        }
    }
}
