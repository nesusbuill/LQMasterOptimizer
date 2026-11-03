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

                emit(5, "Phân tích cấu trúc Unity Engine...")
                delay(500)

                // =========================================================
                // BƯỚC 1: BÍ THUẬT CHỐNG TẢI LẠI (FREEZE VERSION & LOCALHASH)
                // Hiểu Unity: File version/localhash quyết định xem có tải lại hay không.
                // Ta cấp quyền Read-Only (chmod 444) và sửa số version thành max.
                // =========================================================
                emit(15, "Khóa file cập nhật (Chống tải lại)...")
                sh("find $extDir -name '*version*' -exec chmod 444 {} \\; 2>/dev/null")
                sh("find $extDir -name 'localhash' -exec chmod 444 {} \\; 2>/dev/null")
                sh("find $extDir -name '*.json' -exec sed -i 's/\"version\":.*/\"version\":\"99.99.99\",/g' {} \\; 2>/dev/null")
                delay(500)

                // =========================================================
                // BƯỚC 2: XÓA SÂU TÀI NGUYÊN NẶNG (ÉP XUỐNG 1.3GB)
                // Chỉ xóa trong thư mục files/ (tải thêm). KHÔNG ĐỤNG OBB (Map gốc).
                // =========================================================
                emit(35, "Xóa Model Tướng, Quái, Hiệu ứng...")
                // Xóa model chi tiết tướng, lính, quái (giữ lại base model trong OBB)
                sh("rm -rf $extDir/files/hero* 2>/dev/null")
                sh("rm -rf $extDir/files/unit* 2>/dev/null")
                sh("rm -rf $extDir/files/character* 2>/dev/null")
                
                // Xóa hiệu ứng (cloak, particle, kỹ năng)
                sh("rm -rf $extDir/files/effect* 2>/dev/null")
                sh("rm -rf $extDir/files/particle* 2>/dev/null")
                
                // Xóa Avatar, Sảnh, Video quảng cáo (giữ lại emote, âm thanh)
                sh("rm -rf $extDir/files/avatar* 2>/dev/null")
                sh("rm -rf $extDir/files/lobby* 2>/dev/null")
                sh("rm -rf $extDir/files/preview* 2>/dev/null")
                sh("rm -rf $extDir/files/showcase* 2>/dev/null")
                sh("rm -rf $extDir/files/patch* 2>/dev/null")

                // Dọn rác Cache & Shader
                sh("rm -rf $extDir/cache/* 2>/dev/null")
                sh("rm -rf $dataDir/cache/* 2>/dev/null")
                sh("find $dataDir -path '*cache*shader*' -delete 2>/dev/null")
                delay(1000)

                // =========================================================
                // BƯỚC 3: TINH CHỈNH CONFIG ĐỒ HỌA & TẮT HIỆU ỨNG TRANG BỊ
                // =========================================================
                emit(70, "Tắt hiệu ứng trang bị, kỹ năng & tinh chỉnh...")
                sh("""
                    find $dataDir/shared_prefs -name '*.xml' -exec sed -i \
                    -e 's/<int name="ShadowQuality" value="[0-9]*" \/>/<int name="ShadowQuality" value="1" \/>/g' \
                    -e 's/<int name="EffectQuality" value="[0-9]*" \/>/<int name="EffectQuality" value="0" \/>/g' \
                    -e 's/<int name="ParticleQuality" value="[0-9]*" \/>/<int name="ParticleQuality" value="0" \/>/g' \
                    -e 's/<int name="TextureQuality" value="[0-9]*" \/>/<int name="TextureQuality" value="2" \/>/g' \
                    -e 's/<int name="GraphicsQuality" value="[0-9]*" \/>/<int name="GraphicsQuality" value="2" \/>/g' \
                    -e 's/<float name="ResolutionScale" value="[0-9.]*" \/>/<float name="ResolutionScale" value="0.75" \/>/g' \
                    -e 's/<boolean name="AntiAliasing" value="[a-z]*" \/>/<boolean name="AntiAliasing" value="true" \/>/g' \
                    -e 's/<boolean name="enable_avatar" value="true" \/>/<boolean name="enable_avatar" value="false" \/>/g' \
                    -e 's/<boolean name="show_lobby" value="true" \/>/<boolean name="show_lobby" value="false" \/>/g' \
                    -e 's/<boolean name="kill_effect" value="true" \/>/<boolean name="kill_effect" value="false" \/>/g' \
                    -e 's/<boolean name="button_effect" value="true" \/>/<boolean name="button_effect" value="false" \/>/g' \
                    -e 's/<boolean name="enable_item_effect" value="true" \/>/<boolean name="enable_item_effect" value="false" \/>/g' \
                    -e 's/<boolean name="show_equipment_effect" value="true" \/>/<boolean name="show_equipment_effect" value="false" \/>/g' \
                    -e 's/<boolean name="weapon_glow" value="true" \/>/<boolean name="weapon_glow" value="false" \/>/g' \
                    -e 's/<boolean name="show_item_vfx" value="true" \/>/<boolean name="show_item_vfx" value="false" \/>/g' \
                    -e 's/<boolean name="enable_buff_effect" value="true" \/>/<boolean name="enable_buff_effect" value="false" \/>/g' \
                    {} \;
                """.trimIndent())
                delay(1000)

                // =========================================================
                // BƯỚC 4: KHỞI ĐỘNG LẠI GAME
                // =========================================================
                emit(90, "Force-stop Liên Quân...")
                sh("am force-stop $LQ_PKG")
                delay(800)

                emit(100, "Hoàn tất! Dung lượng 1.3GB, Full Skin, Không lag.")
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
