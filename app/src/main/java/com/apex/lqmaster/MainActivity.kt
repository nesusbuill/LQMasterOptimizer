package com.apex.lqmaster

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rikka.shizuku.Shizuku

val DarkBg = Color(0xFF000000)
val CardBg = Color(0xFF1C1C1E)
val PurpleBtn = Color(0xFFD0BCFF)
val TextWhite = Color(0xFFEEEEEE)
val TextGray = Color(0xFFAAAAAA)
val RedColor = Color(0xFFFF5252)
val GreenColor = Color(0xFF4CAF50)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.systemBars())
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
    var optimizing by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }

    fun checkStatus() {
        hasShizuku = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) { false }

        try {
            val info = context.packageManager.getPackageInfo("com.garena.game.kgvn", 0)
            isLocked = Prefs.isOptimizedForCurrentVersion(context, info.versionName)
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) { checkStatus() }

    Column(
        Modifier.fillMaxSize().background(DarkBg).verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // CARD 1: THÔNG TIN GAME
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(16.dp)
        ) {
            // Avatar Liên Quân
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE65100)),
                contentAlignment = Alignment.Center
            ) {
                Text("5v5", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column {
                Text("Liên Quân Mobile", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("com.garena.game.kgvn", color = TextGray, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                
                val rules = listOf(
                    "• Fix Lag Liên Quân Siêu Mượt Mới Nhất",
                    "• Chơi được đấu thường, rank, đỉnh cao, 1v1, 3v3",
                    "• Không cấu hình cao, avt, sảnh tổ đội",
                    "• Không hiệu ứng hạ, nút bấm",
                    "• Không mod skin được"
                )
                rules.forEach { Text(it, color = TextWhite, fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp)) }

                Spacer(Modifier.height(12.dp))
                Text("=> Cần khoảng 20p để tối ưu tùy theo máy.\nSau khi nhấn Optimizer bạn có thể để app dưới nền và thoát ra ngoài làm việc khác.", color = TextWhite, fontSize = 12.sp, lineHeight = 16.sp)
                
                Spacer(Modifier.height(8.dp))
                Text("✖ Mỗi bản update chỉ optimize 1 lần.", color = RedColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // CARD 2: SHIZUKU
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg)
                .clickable { 
                    if (!hasShizuku) { 
                        try { Shizuku.requestPermission(1001) } catch (_: Exception) {} 
                    } 
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF333333)), contentAlignment = Alignment.Center) {
                Text("▶", color = RedColor, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Cách sử dụng shizuku android 11+", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    if (hasShizuku) "Đã cấp quyền Shizuku" else "Android 11+ phải có quyền Shizuku để can thiệp files",
                    color = if (hasShizuku) GreenColor else TextGray, fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // NÚT OPTIMIZE
        Box(
            Modifier.fillMaxWidth().height(55.dp).clip(RoundedCornerShape(28.dp))
                .background(if (isLocked) Color(0xFF333333) else if (hasShizuku && !optimizing) PurpleBtn else Color(0xFF444444))
                .clickable {
                    if (hasShizuku && !optimizing && !isLocked) {
                        optimizing = true
                        val intent = Intent(context, OptimizerService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                        else context.startService(intent)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isLocked) "ĐÃ TỐI ƯU CHO BẢN NÀY" 
                       else if (optimizing) "ĐANG TỐI ƯU (20P)..." 
                       else if (hasShizuku) "Optimize" 
                       else "CẦN CẤP SHIZUKU",
                color = if (isLocked) TextGray else if (hasShizuku && !optimizing) DarkBg else TextGray,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}
