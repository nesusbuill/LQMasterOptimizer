package com.apex.lqmaster

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

class OptimizerService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val LQ_PKG = "com.garena.game.kgvn"
    private val LQ_DATA = "/data/data/$LQ_PKG"

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotif("Đang phân tích tài nguyên...", 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            // BƯỚC 1: Tinh chỉnh file cấu hình (Giống hệt app gốc)
            updateNotif("Đang tắt Avt, sảnh, hiệu ứng...", 10)
            applyConfigTweaks()
            delay(3000)

            // BƯỚC 2: Ép xung CPU/GPU & Dọn dẹp hệ thống
            updateNotif("Đang ép xung CPU/GPU & dọn RAM...", 30)
            // Tắt animation hệ thống
            sh("settings put global window_animation_scale 0")
            sh("settings put global transition_animation_scale 0")
            sh("settings put global animator_duration_scale 0")
            // Ép xung CPU
            for (i in 0..7) {
                sh("echo performance > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor 2>/dev/null")
            }
            // Ép xung GPU
            for (p in listOf(
                "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
                "/sys/class/devfreq/gpufreq/governor",
                "/sys/kernel/gpu/gpu_governor"
            )) {
                sh("echo performance > $p 2>/dev/null")
            }
            // Dọn rác cache
            sh("rm -rf /sdcard/Android/data/$LQ_PKG/cache/*")
            sh("rm -rf /sdcard/Android/data/$LQ_PKG/files/log/*")
            delay(4000)

            // BƯỚC 3: Chạy ngầm duy trì trạng thái (20 phút)
            updateNotif("Đang duy trì trạng thái tối ưu...", 50)
            sh("am set-standby-bucket $LQ_PKG active")
            sh("settings put global background_process_limit 2")

            // Vòng lặp 19 phút (vì 3 bước trên tốn khoảng 10s)
            val totalMins = 19
            for (i in 1..totalMins) {
                delay(60_000)
                val progress = 50 + (i * 50 / totalMins)
                updateNotif("Đang duy trì tối ưu... (${i}/${totalMins} phút)", progress)
                // Cứ 2 phút lại kill app ngầm 1 lần
                if (i % 2 == 0) {
                    sh("am kill-all")
                }
            }

            updateNotif("Tối ưu hoàn tất! Hãy vào game.", 100)
            
            // Lưu lại version game hiện tại để hiển thị thông báo "Đã tối ưu"
            try {
                val info = packageManager.getPackageInfo(LQ_PKG, 0)
                Prefs.saveOptimizedVersion(this@OptimizerService, info.versionName)
            } catch (_: Exception) {}

            delay(3000)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun applyConfigTweaks() {
        val configDir = "$LQ_DATA/files"
        
        // Dùng lệnh sed để thay đổi các key config từ true -> false
        // Tắt Avatar, Sảnh, Hiệu ứng hạ, Nút bấm
        val sedCmd = """
            find $configDir -name '*.json' -exec sed -i \
            -e 's/"enable_avatar"\s*:\s*true/"enable_avatar":false/g' \
            -e 's/"show_avatar"\s*:\s*true/"show_avatar":false/g' \
            -e 's/"show_lobby"\s*:\s*true/"show_lobby":false/g' \
            -e 's/"enable_lobby"\s*:\s*true/"enable_lobby":false/g' \
            -e 's/"kill_effect"\s*:\s*true/"kill_effect":false/g' \
            -e 's/"show_kill_effect"\s*:\s*true/"show_kill_effect":false/g' \
            -e 's/"button_effect"\s*:\s*true/"button_effect":false/g' {} \;
        """.trimIndent()
        
        sh(sedCmd)
    }

    private fun sh(cmd: String) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                process.waitFor()
            }
        } catch (_: Exception) {}
    }

    private fun buildNotif(text: String, progress: Int): Notification {
        val channelId = "lq_master_ch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, "Optimize Service", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("LQ Master Optimizer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotif(text: String, progress: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, buildNotif(text, progress))
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
