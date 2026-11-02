package com.apex.lqmaster

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import rikka.shizuku.Shizuku

val DarkBg = Color(0xFF000000)
val CardBg = Color(0xFF1C1C1E)
val PurpleBtn = Color(0xFFD0BCFF)
val TextWhite = Color(0xFFEEEEEE)
val TextGray = Color(0xFFAAAAAA)
val RedColor = Color(0xFFFF5252)
val GreenColor = Color(0xFF4CAF50)

class MainActivity : ComponentActivity() {
    
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        // Fix crash UI toàn màn hình chuẩn Android 14
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Xin quyền thông báo (Bắt buộc cho Android 13+ để không crash Service)
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
    var optimizing by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }

    fun checkStatus() {
        // Kiểm tra xem Shizuku có được cài không
        isShizukuInstalled = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) != null
        } catch (e: Exception) {
            false
        }

        // Bọc try-catch 100% để không bao giờ crash
        hasShizuku = try {
            if (isShizukuInstalled) {
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else false
        } catch (e: Throwable) { 
            false 
        }

        try {
            val info = context.packageManager.getPackageInfo("com.garena.game.kgvn", 0)
            isLocked = Prefs.isOptimizedForCurrentVersion(context, info.versionName)
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) { checkStatus() }

    Column(
        Modifier.fillMaxSize().background(DarkBg).verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // CARD 1: THÔNG TIN
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(16.dp)
        ) {
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
                    if (!isShizukuInstalled) {
                        // Mở CH Play nếu chưa cài
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
                        } catch (_: Exception) {}
                    } else if (!hasShizuku) {
                        // Xin quyền nếu đã cài
                        try { Shizuku.requestPermission(1001) } catch (e: Throwable) {} 
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
                    text = when {
                        !isShizukuInstalled -> "Chưa cài đặt. Bấm để tải trên CH Play"
                        !hasShizuku -> "Đã cài. Bấm để cấp quyền"
                        else -> "Đã cấp quyền Shizuku"
                    },
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
                        try {
                            val intent = Intent(context, OptimizerService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                            else context.startService(intent)
                        } catch (e: Exception) {
                            optimizing = false
                        }
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
