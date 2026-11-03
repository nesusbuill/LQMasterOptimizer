package com.apex.lqmaster

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.InvocationTargetException

class OptimizerService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private val LQ_PKG = "com.garena.game.kgvn"
    private val CHANNEL_ID = "lq_opti_ch"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val n = buildNotif("Đang khởi tạo...", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(1, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                val dataDir = "/data/data/$LQ_PKG"
                val extDir = "/sdcard/Android/data/$LQ_PKG"

                emit(10, "Đang phân tích cấu trúc Unity Engine...")
                delay(500)

                // =========================================================
                // BƯỚC 1: DỌN DẸP RÁC "VÔ HÌNH" (AN TOÀN TUYỆT ĐỐI)
                // Hiểu Unity: Không xóa file /files/ (AssetBundle) 
                // Chỉ xóa Shader Cache, Log, Replay, file tạm (.tmp, .old)
                // =========================================================
                emit(30, "Dọn rác ẩn (Shader Cache, Log, Replay)...")
                
                // Xóa cache (không ảnh hưởng Manifest)
                sh("rm -rf $extDir/cache/* 2>/dev/null")
                sh("rm -rf $dataDir/cache/* 2>/dev/null")
                
                // Xóa log, replay
                sh("rm -rf $extDir/files/log/* 2>/dev/null")
                sh("rm -rf $extDir/files/replay/* 2>/dev/null")
                
                // Xóa file rác
                sh("find $extDir -name '*.tmp' -delete 2>/dev/null")
                sh("find $extDir -name '*.old' -delete 2>/dev/null")
                sh("find $extDir -name '*.log' -delete 2>/dev/null")
                
                // Xóa shader cache (ép Unity nạp shader mới nhẹ hơn)
                sh("find $dataDir -path '*cache*shader*' -delete 2>/dev/null")
                sh("find $dataDir -path '*cache*gpu*' -delete 2>/dev/null")
                delay(1000)

                // =========================================================
                // BƯỚC 2: CẮT GIẢM HIỆU ỨNG MÀU SẮC QUA XML (GIỮ FULL SKIN)
                // =========================================================
                emit(60, "Tắt hiệu ứng nặng, hạ Res 0.35, giữ Skin...")
                sh("""
                    find $dataDir/shared_prefs -name '*.xml' -exec sed -i \
                    -e 's/<int name="ShadowQuality" value="[0-9]*" \/>/<int name="ShadowQuality" value="0" \/>/g' \
                    -e 's/<int name="EffectQuality" value="[0-9]*" \/>/<int name="EffectQuality" value="0" \/>/g' \
                    -e 's/<int name="ParticleQuality" value="[0-9]*" \/>/<int name="ParticleQuality" value="0" \/>/g' \
                    -e 's/<int name="TextureQuality" value="[0-9]*" \/>/<int name="TextureQuality" value="1" \/>/g' \
                    -e 's/<int name="GraphicsQuality" value="[0-9]*" \/>/<int name="GraphicsQuality" value="1" \/>/g' \
                    -e 's/<float name="ResolutionScale" value="[0-9.]*" \/>/<float name="ResolutionScale" value="0.35" \/>/g' \
                    -e 's/<boolean name="AntiAliasing" value="true" \/>/<boolean name="AntiAliasing" value="false" \/>/g' \
                    -e 's/<boolean name="EnableHDR" value="true" \/>/<boolean name="EnableHDR" value="false" \/>/g' \
                    -e 's/<boolean name="EnableBloom" value="true" \/>/<boolean name="EnableBloom" value="false" \/>/g' \
                    -e 's/<boolean name="enable_avatar" value="true" \/>/<boolean name="enable_avatar" value="false" \/>/g' \
                    -e 's/<boolean name="show_avatar" value="true" \/>/<boolean name="show_avatar" value="false" \/>/g' \
                    -e 's/<boolean name="show_lobby" value="true" \/>/<boolean name="show_lobby" value="false" \/>/g' \
                    -e 's/<boolean name="enable_lobby" value="true" \/>/<boolean name="enable_lobby" value="false" \/>/g' \
                    -e 's/<boolean name="kill_effect" value="true" \/>/<boolean name="kill_effect" value="false" \/>/g' \
                    -e 's/<boolean name="button_effect" value="true" \/>/<boolean name="button_effect" value="false" \/>/g' \
                    {} \;
                """.trimIndent())
                delay(1000)

                // =========================================================
                // BƯỚC 3: KHỞI ĐỘNG LẠI GAME (Ép Unity nạp cấu hình mới)
                // =========================================================
                emit(90, "Force-stop Liên Quân...")
                sh("am force-stop $LQ_PKG")
                delay(800)

                emit(100, "Hoàn tất! Không tải lại, full Skin.")
                ProgressState.finish(); delay(2000); stopSelf()

            } catch (e: Exception) {
                e.printStackTrace(); ProgressState.reset(); stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun emit(p: Int, msg: String) {
        ProgressState.update(p, msg); updateNotif(msg, p)
    }

    private fun sh(cmd: String) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                val c = Class.forName("rikka.shizuku.Shizuku")
                val m = c.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                m.isAccessible = true
                (m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as ShizukuRemoteProcess).waitFor()
            }
        } catch (_: InvocationTargetException) { } catch (_: Exception) { }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "LQ Optimizer", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotif(t: String, p: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LQ Optimizer")
            .setContentText(t)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setProgress(100, p, false)
            .setOngoing(true)
            .build()

    private fun updateNotif(t: String, p: Int) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1, buildNotif(t, p))
    }

    override fun onDestroy() { job.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
