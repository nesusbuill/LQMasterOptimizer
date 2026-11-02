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
    private val CHANNEL_ID = "lq_master_ch"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotif("Đang phân tích tài nguyên...", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                emit(10, "Đang tắt Avt, sảnh, hiệu ứng...")
                applyConfigTweaks()
                delay(3000)

                emit(30, "Đang ép xung CPU/GPU & dọn RAM...")
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

                emit(50, "Đang duy trì trạng thái tối ưu...")
                sh("am set-standby-bucket $LQ_PKG active")
                sh("settings put global background_process_limit 2")

                val totalMins = 19
                for (i in 1..totalMins) {
                    delay(60_000)
                    val progress = 50 + (i * 50 / totalMins)
                    emit(progress, "Đang duy trì tối ưu... ($i/$totalMins phút)")
                    if (i % 2 == 0) sh("am kill-all")
                }

                emit(100, "Tối ưu hoàn tất! Hãy vào game.")
                try {
                    val info = packageManager.getPackageInfo(LQ_PKG, 0)
                    Prefs.saveOptimizedVersion(this@OptimizerService, info.versionName)
                } catch (_: Exception) {}

                ProgressState.finish()
                delay(3000)
                stopSelf()
            } catch (e: Exception) {
                e.printStackTrace()
                ProgressState.reset()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun emit(progress: Int, msg: String) {
        ProgressState.update(progress, msg)
        updateNotif(msg, progress)
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
                val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                val newProcessMethod = shizukuClass.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                )
                newProcessMethod.isAccessible = true
                val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null) as ShizukuRemoteProcess
                process.waitFor()
            }
        } catch (e: InvocationTargetException) {
            e.targetException?.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Optimize Service", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotif(text: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
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
