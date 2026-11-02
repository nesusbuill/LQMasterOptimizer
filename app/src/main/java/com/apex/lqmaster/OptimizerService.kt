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
import rikka.shizuku.ShizukuRemoteProcess

class OptimizerService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val LQ_PKG = "com.garena.game.kgvn"
    private val LQ_DATA = "/data/data/$LQ_PKG"

    override fun onCreate() {
        super.onCreate()
        // Tạo channel trước khi startForeground để tránh crash
        createNotificationChannel()
        startForeground(1, buildNotif("Đang phân tích tài nguyên...", 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                // BƯỚC 1: Config
                updateNotif("Đang tắt Avt, sảnh, hiệu ứng...", 10)
                applyConfigTweaks()
                delay(3000)

                // BƯỚC 2: Ép xung & Dọn rác
                updateNotif("Đang ép xung CPU/GPU & dọn RAM...", 30)
                sh("settings put global window_animation_scale 0")
                sh("settings put global transition_animation_scale 0")
                sh("settings put global animator_duration_scale 0")
                for (i in 0..7) {
                    sh("echo performance > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor 2>/dev/null")
                }
                for (p in listOf(
                    "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
                    "/sys/class/devfreq/gpufreq/governor",
                    "/sys/kernel/gpu/gpu_governor"
                )) {
                    sh("echo performance > $p 2>/dev/null")
                }
                sh("rm -rf /sdcard/Android/data/$LQ_PKG/cache/*")
                sh("rm -rf /sdcard/Android/data/$LQ_PKG/files/log/*")
                delay(4000)

                // BƯỚC 3: Duy trì trạng thái 19 phút
                updateNotif("Đang duy trì trạng thái tối ưu...", 50)
                sh("am set-standby-bucket $LQ_PKG active")
                sh("settings put global background_process_limit 2")

                val totalMins = 19
                for (i in 1..totalMins) {
                    delay(60_000)
                    val progress = 50 + (i * 50 / totalMins)
                    updateNotif("Đang duy trì tối ưu... (${i}/${totalMins} phút)", progress)
                    if (i % 2 == 0) {
                        sh("am kill-all")
                    }
                }

                updateNotif("Tối ưu hoàn tất! Hãy vào game.", 100)
                
                try {
                    val info = packageManager.getPackageInfo(LQ_PKG, 0)
                    Prefs.saveOptimizedVersion(this@OptimizerService, info.versionName)
                } catch (_: Exception) {}

                delay(3000)
                stopSelf()
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun applyConfigTweaks() {
        val configDir = "$LQ_DATA/files"
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
                // Dùng Reflection vượt qua giới hạn private của Shizuku 13.x
                val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                val newProcessMethod = shizukuClass.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                newProcessMethod.isAccessible = true
                val process = newProcessMethod.invoke(
                    null,
                    arrayOf("sh", "-c", cmd),
                    null,
                    null
                ) as ShizukuRemoteProcess
                process.waitFor()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "lq_master_ch", 
                "Optimize Service", 
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotif(text: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, "lq_master_ch")
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
