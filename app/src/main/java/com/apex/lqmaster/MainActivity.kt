package com.apex.lqmaster

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import rikka.shizuku.Shizuku

val BgDark    = Color(0xFF141414)
val CardBg    = Color(0xFF1C1C1E)
val PurpleBtn = Color(0xFFD0BCFF)
val TextWhite = Color(0xFFEEEEEE)
val TextGray  = Color(0xFF888888)
val RedColor  = Color(0xFFFF5252)

class MainActivity : ComponentActivity() {
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
    var hasLQ by remember { mutableStateOf(false) }
    
    val isRunning by ProgressState.isRunning.collectAsState()
    val progress by ProgressState.progress.collectAsState()
    val message by ProgressState.message.collectAsState()

    // TỰ ĐỘNG CHECK & XIN QUYỀN KHI MỞ APP
    LaunchedEffect(Unit) {
        hasLQ = try {
            context.packageManager.getPackageInfo("com.garena.game.kgvn", 0) != null
        } catch (_: Exception) { false }

        val isShizukuInstalled = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) != null
        } catch (_: Exception) { false }

        hasShizuku = try {
            if (isShizukuInstalled)
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            else false
        } catch (_: Throwable) { false }

        if (isShizukuInstalled && !hasShizuku) {
            try { Shizuku.requestPermission(1001) } catch (_: Throwable) {}
        } else if (!isShizukuInstalled) {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))) } catch (_: Exception) {}
        }
    }

    fun checkStatus() {
        val isShizukuInstalled = try {
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

    fun runOptimize() {
        checkStatus()
        if (!hasLQ) {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.garena.game.kgvn"))) } catch (_: Exception) {}
            return
        }
        if (!hasShizuku) {
            try { Shizuku.requestPermission(1001) } catch (_: Throwable) {}
            return
        }

        ProgressState.start()
        try {
            val i = Intent(context, OptimizerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        } catch (_: Exception) { ProgressState.reset() }
    }

    Column(
        Modifier.fillMaxSize().background(BgDark).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("<", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Light)
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                
                // DÙNG BOX THAY VÌ ẢNH ĐỂ TRÁNH LỖI BUILD FILE ẢNH (AAPT)
                Box(
                    Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE65100)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("5v5", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, fontStyle = FontStyle.Italic)
                }
                
                Spacer(Modifier.width(12.dp))
                
                Column {
                    Text("Liên Quân Mobile", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("com.garena.game.kgvn", color = TextGray, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            val lines = listOf(
                "• Fix Lag Siêu Mượt (Còn 1,3GB)",
                "• Chơi được đấu thường, rank, đỉnh cao, 3v3",
                "• Nền sảnh, tổ đội",
                "• Sử dụng map gốc",
                "• Có cấu hình cao",
                "• Không avt",
                "• Có emote",
                "• Có skin (Ko mod)",
                "• Có âm thanh chiêu thức",
                "• Xoá chi tiết tướng và quái",
                "• Không các hiệu ứng đổ(ảo choàng băng...)"
            )
            
            lines.forEach { line ->
                Text(text = line, color = TextWhite, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 2.dp))
            }

            Spacer(Modifier.height(16.dp))

            Text("✖ Mỗi bản update chỉ optimize 1 lần.", color = RedColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(Modifier.weight(1f))

        if (isRunning) {
            Text(
                text = "$message ($progress%)",
                color = PurpleBtn,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = if (isRunning) listOf(Color(0xFF888888), Color(0xFF555555)) else listOf(PurpleBtn, PurpleBtn)
                    )
                )
                .clickable(enabled = !isRunning) { runOptimize() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isRunning) "$progress%" else "Optimize",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                if (!isRunning) {
                    Spacer(Modifier.width(8.dp))
                    Text("▶", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
