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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

val DarkBg    = Color(0xFF000000)
val CardBg    = Color(0xFF1C1C1E)
val PurpleBtn = Color(0xFFD0BCFF)
val TextWhite = Color(0xFFEEEEEE)
val TextGray  = Color(0xFFAAAAAA)
val RedColor  = Color(0xFFFF5252)
val GreenColor= Color(0xFF4CAF50)

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
            MaterialTheme(colorScheme = darkColorScheme(background = DarkBg, surface = CardBg)) {
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
    var selectedMode by remember { mutableStateOf("low") }

    val progress by ProgressState.progress.collectAsState()
    val message  by ProgressState.message.collectAsState()
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

    fun runAction(action: String) {
        ProgressState.start()
        ProgressState.update(0, "Đang chuẩn bị...")
        try {
            val i = Intent(context, OptimizerService::class.java).apply {
                this.action = action
                putExtra("MODE", selectedMode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        } catch (_: Exception) { ProgressState.reset() }
    }

    Column(
        Modifier.fillMaxSize().background(DarkBg).verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("LQ MASTER OPTIMIZER", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text("Chỉ can thiệp file cấu hình LQ", color = TextGray, fontSize = 12.sp)

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("low", "Siêu Mượt", "Tắt hết"),
                Triple("balanced", "Cân Bằng", "Vừa phải"),
                Triple("high", "Đẹp", "Chi tiết")
            ).forEach { (id, title, desc) ->
                val sel = selectedMode == id
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (sel) PurpleBtn.copy(alpha = 0.2f) else CardBg)
                        .border(if (sel) 2.dp else 1.dp,
                            if (sel) PurpleBtn else Color(0xFF333333), RoundedCornerShape(12.dp))
                        .clickable { selectedMode = id }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, color = if (sel) PurpleBtn else TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(desc, color = TextGray, fontSize = 9.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isRunning || progress > 0) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$progress%", color = PurpleBtn, fontWeight = FontWeight.Black, fontSize = 26.sp)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = PurpleBtn, trackColor = Color(0xFF333333)
                )
                Spacer(Modifier.height(8.dp))
                Text(message, color = TextGray, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
        }

        Box(
            Modifier.size(160.dp).clip(RoundedCornerShape(80.dp))
                .background(
                    when {
                        isLocked -> Brush.radialGradient(listOf(Color(0xFF333333), Color(0xFF111111)))
                        isRunning -> Brush.radialGradient(listOf(Color(0xFF444444), Color(0xFF222222)))
                        hasShizuku -> Brush.radialGradient(listOf(PurpleBtn, Color(0xFF8860D0)))
                        else -> Brush.radialGradient(listOf(Color(0xFF444444), Color(0xFF222222)))
                    }
                )
                .clickable {
                    if (hasShizuku && !isRunning && !isLocked) {
                        runAction("ACTION_OPTIMIZE")
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
                fontWeight = FontWeight.Black, fontSize = 18.sp,
                lineHeight = 22.sp, textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionMini("QUÉT FILE", Color(0xFF2196F3)) {
                if (hasShizuku && !isRunning) runAction("ACTION_SCAN")
            }
            ActionMini("XÓA EFFECT", Color(0xFFFF9800)) {
                if (hasShizuku && !isRunning) runAction("ACTION_CLEAR_EFFECTS")
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF333333))
                .clickable { if (hasShizuku && !isRunning) runAction("ACTION_RESTORE") },
            contentAlignment = Alignment.Center
        ) {
            Text("KHÔI PHỤC CONFIG GỐC", color = RedColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text("App chỉ đọc/ghi file trong thư mục LQ.\nKhông động kernel, không đổi cài đặt máy.",
            color = TextGray, fontSize = 10.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun ActionMini(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color, RoundedCornerShape(21.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}
