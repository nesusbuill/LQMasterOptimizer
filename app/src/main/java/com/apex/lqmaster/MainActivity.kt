package com.apex.lqmaster

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import rikka.shizuku.Shizuku

val DarkBg     = Color(0xFF000000)
val PurpleBtn  = Color(0xFFD0BCFF)
val TextWhite  = Color(0xFFEEEEEE)
val TextGray   = Color(0xFFAAAAAA)

class MainActivity : ComponentActivity() {
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DarkBg)) {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    var hasShizuku by remember { mutableStateOf(false) }
    var isShizukuInstalled by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }

    val progress by ProgressState.progress.collectAsState()
    val message by ProgressState.message.collectAsState()
    val isRunning by ProgressState.isRunning.collectAsState()

    fun checkStatus() {
        isShizukuInstalled = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) != null
        } catch (_: Exception) { false }

        hasShizuku = try {
            if (isShizukuInstalled)
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            else false
        } catch (_: Throwable) { false }

        try {
            val info = context.packageManager.getPackageInfo("com.garena.game.kgvn", 0)
            isLocked = Prefs.isOptimizedForCurrentVersion(context, info.versionName)
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) { checkStatus() }
    LaunchedEffect(isRunning) { if (!isRunning) checkStatus() }

    fun runOptimize() {
        ProgressState.start()
        try {
            val i = Intent(context, OptimizerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        } catch (_: Exception) { ProgressState.reset() }
    }

    Column(
        Modifier.fillMaxSize().background(DarkBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("LQ OPTIMIZER", color = PurpleBtn, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = 2.sp)
        Text("Full Skin · Nhẹ Tối Đa · 1 Nút", color = TextGray, fontSize = 13.sp)
        
        Spacer(Modifier.height(40.dp))

        if (isRunning || progress > 0) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("$progress%", color = PurpleBtn, fontWeight = FontWeight.Black, fontSize = 30.sp)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = PurpleBtn, trackColor = Color(0xFF333333)
                )
                Spacer(Modifier.height(12.dp))
                Text(message, color = TextGray, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(40.dp))
        }

        // NÚT BẤM DUY NHẤT
        Box(
            Modifier.size(200.dp).clip(RoundedCornerShape(100.dp))
                .background(
                    when {
                        isLocked -> Brush.radialGradient(listOf(Color(0xFF333333), Color(0xFF111111)))
                        isRunning -> Brush.radialGradient(listOf(Color(0xFF444444), Color(0xFF222222)))
                        hasShizuku -> Brush.radialGradient(listOf(PurpleBtn, Color(0xFF8860D0)))
                        else -> Brush.radialGradient(listOf(Color(0xFF444444), Color(0xFF222222)))
                    }
                )
                .border(4.dp, if (hasShizuku && !isLocked) PurpleBtn else Color.Transparent, RoundedCornerShape(100.dp))
                .clickable {
                    if (hasShizuku && !isRunning && !isLocked) {
                        runOptimize()
                    } else if (!hasShizuku) {
                        if (!isShizukuInstalled) {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))) } catch (_: Exception) {}
                        } else {
                            try { Shizuku.requestPermission(1001) } catch (_: Throwable) {}
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                when {
                    isLocked -> "ĐÃ TỐI ƯU"
                    isRunning -> "ĐANG CHẠY..."
                    hasShizuku -> "OPTIMIZE"
                    else -> "CẤP QUYỀN\nSHIZUKU"
                },
                color = if (hasShizuku && !isRunning && !isLocked) DarkBg else TextWhite,
                fontWeight = FontWeight.Black, fontSize = 24.sp,
                lineHeight = 28.sp, textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(40.dp))
        Text("Tự động dọn rác, tắt bóng, tắt hiệu ứng.\nGiữ nguyên 100% Texture và Skin trong trận.",
            color = TextGray, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
    }
}
