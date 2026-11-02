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
    private val LQ_DATA = "/data/data/$LQ_PKG"
    private val LQ_EXT = "/sdcard/Android/data/$LQ_PKG"
    private val CHANNEL_ID = "lq_master_ch"

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
                // 1. Backup
                emit(10, "Sao lưu config gốc...")
                sh("find $LQ_DATA/shared_prefs -name '*.xml' -exec cp {} {}.lqopt.bak \\; 2>/dev/null")
                delay(300)

                // 2. Tối ưu Đồ họa trong trận (Giữ nguyên Skin)
                emit(25, "Tối ưu đồ họa trong trận (Giữ nguyên Skin)...")
                sh("""
                    find $LQ_DATA/shared_prefs -name '*.xml' -exec sed -i \
                    -e 's/<int name="ShadowQuality" value="[0-9]*" \/>/<int name="ShadowQuality" value="0" \/>/g' \
                    -e 's/<int name="EffectQuality" value="[0-9]*" \/>/<int name="EffectQuality" value="0" \/>/g' \
                    -e 's/<int name="TextureQuality" value="[0-9]*" \/>/<int name="TextureQuality" value="1" \/>/g' \
                    -e 's/<int name="GraphicsQuality" value="[0-9]*" \/>/<int name="GraphicsQuality" value="1" \/>/g' \
                    -e 's/<float name="ResolutionScale" value="[0-9.]*" \/>/<float name="ResolutionScale" value="0.6" \/>/g' \
                    -e 's/<boolean name="AntiAliasing" value="true" \/>/<boolean name="AntiAliasing" value="false" \/>/g' \
                    -e 's/<boolean name="EnableHDR" value="true" \/>/<boolean name="EnableHDR" value="false" \/>/g' \
                    {} \;
                """.trimIndent())
                delay(300)

                // 3. TẮT 3D & HIỆU ỨNG Ở MÀN HÌNH CHỜ / CHỌN TƯỚNG (Cái sếp muốn bôi đen)
                emit(40, "Tắt mô hình 3D, video, hiệu ứng ở Lobby & Pick tướng...")
                sh("""
                    find $LQ_DATA/shared_prefs -name '*.xml' -exec sed -i \
                    -e 's/<boolean name="enable_avatar" value="true" \/>/<boolean name="enable_avatar" value="false" \/>/g' \
                    -e 's/<boolean name="show_lobby" value="true" \/>/<boolean name="show_lobby" value="false" \/>/g' \
                    -e 's/<boolean name="lobby_animation" value="true" \/>/<boolean name="lobby_animation" value="false" \/>/g' \
                    -e 's/<boolean name="dynamic_background" value="true" \/>/<boolean name="dynamic_background" value="false" \/>/g' \
                    -e 's/<boolean name="showcase_enabled" value="true" \/>/<boolean name="showcase_enabled" value="false" \/>/g' \
                    -e 's/<boolean name="hero_showcase" value="true" \/>/<boolean name="hero_showcase" value="false" \/>/g' \
                    -e 's/<boolean name="enable_hero_animation" value="true" \/>/<boolean name="enable_hero_animation" value="false" \/>/g' \
                    -e 's/<boolean name="pick_animation" value="true" \/>/<boolean name="pick_animation" value="false" \/>/g' \
                    -e 's/<boolean name="show_ui_effect" value="true" \/>/<boolean name="show_ui_effect" value="false" \/>/g' \
                    -e 's/<boolean name="ui_particle" value="true" \/>/<boolean name="ui_particle" value="false" \/>/g' \
                    -e 's/<boolean name="kill_effect" value="true" \/>/<boolean name="kill_effect" value="false" \/>/g' \
                    -e 's/<boolean name="button_effect" value="true" \/>/<boolean name="button_effect" value="false" \/>/g' \
                    {} \;
                """.trimIndent())
                
                // Tắt luôn trong file JSON (nếu game có lưu)
                for (p in listOf("*setting*.json", "*graphic*.json", "*config*.json")) {
                    sh("""
                        find $LQ_DATA/files -name '$p' -exec sed -i \
                        -e 's/"dynamic_background"\s*:\s*true/"dynamic_background":false/g' \
                        -e 's/"showcase_enabled"\s*:\s*true/"showcase_enabled":false/g' \
                        -e 's/"pick_animation"\s*:\s*true/"pick_animation":false/g' \
                        -e 's/"show_ui_effect"\s*:\s*true/"show_ui_effect":false/g' \
                        {} \;
                    """.trimIndent())
                }
                delay(300)

                // 4. XÓA FILE VIDEO / ANIMATION NỀN (Không xóa tên skin)
                emit(55, "Xóa file video/animation nền màn hình chọn...")
                val dirsToClear = listOf(
                    "$LQ_EXT/files/preview", "$LQ_EXT/files/showcase", 
                    "$LQ_EXT/files/lobby", "$LQ_EXT/files/hero_video",
                    "$LQ_EXT/files/CDNImage", "$LQ_DATA/files/preview", 
                    "$LQ_DATA/files/showcase", "$LQ_DATA/cache/video"
                )
                for (d in dirsToClear) sh("rm -rf '$d'/* 2>/dev/null")
                
                // Chỉ xóa các file animation 3D nền (giữ lại file text/json chứa tên skin)
                sh("find $LQ_EXT $LQ_DATA -path '*preview*' \\( -name '*.mp4' -o -name '*.webm' -o -name '*.skel' -o -name '*.atlas' \\) -delete 2>/dev/null")
                sh("find $LQ_EXT $LQ_DATA -path '*showcase*' \\( -name '*.mp4' -o -name '*.webm' -o -name '*.skel' -o -name '*.atlas' \\) -delete 2>/dev/null")
                delay(300)

                // 5. Dọn rác
                emit(75, "Dọn dẹp cache, log, replay...")
                sh("rm -rf $LQ_EXT/cache/* 2>/dev/null")
                sh("rm -rf $LQ_EXT/files/log/* $LQ_DATA/files/log/* 2>/dev/null")
                sh("rm -rf $LQ_EXT/files/replay/* $LQ_DATA/files/replay/* 2>/dev/null")
                sh("find $LQ_DATA $LQ_EXT -name '*.tmp' -delete 2>/dev/null")
                sh("find $LQ_DATA $LQ_EXT -name '*.log' -delete 2>/dev/null")
                delay(300)

                // 6. Restart Game
                emit(90, "Force-stop game để áp dụng cấu hình...")
                sh("am force-stop $LQ_PKG")
                delay(800)

                // 7. Hoàn tất
                emit(100, "Tối ưu xong! Màn hình sẽ trống/mờ, tên skin vẫn hiện.")
                try {
                    val info = packageManager.getPackageInfo(LQ_PKG, 0)
                    Prefs.saveOptimizedVersion(this@OptimizerService, info.versionName)
                } catch (_: Exception) {}

                ProgressState.finish(); delay(2500); stopSelf()

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
            .setContentTitle("LQ Game Optimizer")
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
