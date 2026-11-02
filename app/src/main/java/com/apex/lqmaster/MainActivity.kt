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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import rikka.shizuku.Shizuku

// Màu sắc cơ bản
val BgDark    = Color(0xFF121212)
val BtnReady  = Color(0xFF2196F3) // Xanh dương
val BtnRun    = Color(0xFFF44336) // Đỏ
val BtnDisable= Color(0xFF333333) // Xám
val TextWhite = Color(0xFFEEEEEE)
val TextGray  = Color(0xFF888888)

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
            MaterialTheme(colorScheme = darkColorScheme(background = BgDark)) {
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
    var hasLQ by remember { mutableStateOf(false) }
    
    val isRunning by ProgressState.isRunning.collectAsState()
    val progress by ProgressState.progress.collectAsState()
    val message by ProgressState.message.collectAsState()

    fun checkStatus() {
        isShizukuInstalled = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) != null
        } catch (_: Exception) { false }

        hasShizuku = try {
            if (isShizukuInstalled)
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            else false
        } catch (_: Throwable) { false }

        hasLQ = try {
            context.packageManager.getPackageInfo("com.garena.game.kgvn", 0) != null
        } catch (_: Exception) { false }
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
        Modifier.fillMaxSize().background(BgDark).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Tiêu đề
        Text(
            "LQ OPTIMIZER",
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp
        )
        
        Spacer(Modifier.height(60.dp))

        // Nút bấm duy nhất
        val buttonColor = when {
            isRunning -> BtnRun
            hasShizuku && hasLQ -> BtnReady
            else -> BtnDisable
        }
        
        val buttonText = when {
            isRunning -> "$progress%"
            hasShizuku && hasLQ -> "OPTIMIZE"
            !hasLQ -> "CHƯA CÀI GAME"
            else -> "CẤP QUYỀN"
        }

        Box(
            Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable(enabled = !isRunning) {
                    if (hasShizuku && hasLQ) {
                        runOptimize()
                    } else if (!hasLQ) {
                        try { 
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.garena.game.kgvn"))) 
                        } catch (_: Exception) {}
                    } else if (!hasShizuku) {
                        if (!isShizukuInstalled) {
                            try { 
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))) 
                            } catch (_: Exception) {}
                        } else {
                            try { Shizuku.requestPermission(1001) } catch (_: Throwable) {}
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buttonText,
                color = if (hasShizuku && hasLQ) Color.White else TextGray,
                fontWeight = FontWeight.Bold,
                fontSize = if (isRunning) 48.sp else 22.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(40.dp))

        // Trạng thái
        Text(
            text = if (isRunning) message else "Hệ thống sẵn sàng",
            color = TextGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(40.dp)
        )
    }
}
