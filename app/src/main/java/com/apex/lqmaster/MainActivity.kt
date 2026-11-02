package com.apex.lqopti

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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

// Bảng màu Neon Gaming
val BgDark    = Color(0xFF0B0E14)
val CardBg    = Color(0xFF161A25)
val NeonCyan  = Color(0xFF00E5FF)
val NeonBlue  = Color(0xFF2979FF)
val TextWhite = Color(0xFFF5F5F5)
val TextGray  = Color(0xFF8A93A6)
val RedColor  = Color(0xFFFF5252)
val GreenColor= Color(0xFF00E676)

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

    // Hiệu ứng nhịp đập
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.02f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isRunning) 0.8f else 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

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
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ================= HEADER =================
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 30.dp)) {
            Text(
                "LQ OPTIMIZER",
                color = NeonCyan,
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
                letterSpacing = 4.sp
            )
            Text(
                "UNITY ENGINE SAFE MODE",
                color = TextGray,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
        }

        // ================= CENTER (BUTTON) =================
        Box(contentAlignment = Alignment.Center) {
            // Vòng hào quang (Glow)
            Canvas(modifier = Modifier.size(280.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonCyan.copy(alpha = glowAlpha), Color.Transparent),
                        center = center,
                        radius = size.minDimension / 2
                    ),
                    radius = size.minDimension / 2
                )
            }

            // Nút bấm chính
            Box(
                Modifier
                    .scale(scale)
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = when {
                                isRunning -> listOf(Color(0xFF444444), Color(0xFF222222))
                                hasShizuku && hasLQ -> listOf(NeonCyan, NeonBlue)
                                else -> listOf(Color(0xFF333333), Color(0xFF111111))
                            }
                        )
                    )
                    .border(3.dp, if (isRunning) NeonBlue else NeonCyan, CircleShape)
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isRunning) {
                        Text(
                            "$progress%",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 48.sp
                        )
                        Text(
                            "OPTIMIZING",
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    } else {
                        Text(
                            when {
                                hasShizuku && hasLQ -> "OPTIMIZE"
                                !hasLQ -> "NO GAME"
                                else -> "NO PERMISSION"
                            },
                            color = if (hasShizuku && hasLQ) Color.White else TextGray,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                hasShizuku && hasLQ -> "TAP TO BOOST"
                                !hasLQ -> "CÀI LIÊN QUÂN"
                                else -> "CẤP QUYỀN SHIZUKU"
                            },
                            color = if (hasShizuku && hasLQ) Color.White.copy(alpha = 0.7f) else TextGray,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // ================= FOOTER (STATUS) =================
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hộp hiển thị trạng thái
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Trạng thái", color = TextGray, fontSize = 10.sp)
                    Text(
                        if (isRunning) message else "Sẵn sàng",
                        color = if (isRunning) NeonCyan else TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(220.dp)
                    )
                }
                
                // Đèn báo hiệu
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isRunning -> NeonCyan
                                hasShizuku && hasLQ -> GreenColor
                                else -> RedColor
                            }
                        )
                )
            }
        }
    }
}
