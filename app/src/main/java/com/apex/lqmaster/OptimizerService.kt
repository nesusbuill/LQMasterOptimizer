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
        val notification = buildNotif("Đang khởi tạo...", 0)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val mode = intent?.getStringExtra("MODE") ?: "low"

        scope.launch {
            try {
                when (action) {
                    "ACTION_SCAN" -> {
                        emit(20, "Đang quét file tài nguyên LQ...")
                        val report = scanResources()
                        emit(100, report)
                        delay(3000)
                        ProgressState.finish()
                        stopSelf()
                        return@launch
                    }
                    "ACTION_CLEAR_EFFECTS" -> {
                        emit(20, "Đang dọn file effect/cache của LQ...")
                        val freed = clearCacheAndEffects()
                        emit(100, "Đã xóa ${freed}MB file effect/cache")
                        delay(2000)
                        ProgressState.finish()
                        stopSelf()
                        return@launch
                    }
                    "ACTION_RESTORE" -> {
                        emit(40, "Đang khôi phục config gốc...")
                        restoreConfigs()
                        emit(100, "Đã khôi phục config gốc!")
                        delay(2000)
                        ProgressState.finish()
                        stopSelf()
                        return@launch
                    }
                }

                // QUY TRÌNH OPTIMIZE CHÍNH (CHỈ FILE TRONG LQ)
                emit(15, "Sao lưu config gốc...")
                backupConfigs()
                delay(500)

                emit(35, "Tắt Avatar, Sảnh, Hiệu ứng hạ trong config...")
                disableUselessEffects()
                delay(500)

                emit(55, "Ép đồ họa skin/map xuống mức thấp...")
                forceLowestGraphics(mode)
                delay(500)

                emit(75, "Dọn cache/effect rác...")
                clearCacheAndEffects()
                delay(500)

                emit(90, "Force-stop game để reload config...")
                sh("am force-stop $LQ_PKG")
                delay(800)

                emit(100, "Xong. Mở LQ lại để áp dụng.")
                try {
                    val info = packageManager.getPackageInfo(LQ_PKG, 0)
                    Prefs.saveOptimizedVersion(this@OptimizerService, info.versionName)
                } catch (_: Exception) {}

                ProgressState.finish()
                delay(2000)
                stopSelf()

            } catch (e: Exception) {
                e.printStackTrace()
                ProgressState.reset()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // ===== 1. QUÉT FILE TÀI NGUYÊN (CHỈ ĐỌC) =====
    private fun scanResources(): String {
        val sb = StringBuilder()
        val obbDir = "/sdcard/Android/obb/$LQ_PKG"
        val obbSize = out("du -sh $obbDir 2>/dev/null | awk '{print \$1}'").ifEmpty { "?" }
        sb.appendLine("OBB (Skin/Map): $obbSize")
        
        val dataSize = out("du -sh $LQ_EXT 2>/dev/null | awk '{print \$1}'").ifEmpty { "?" }
        sb.appendLine("Data LQ: $dataSize")

        val cacheMb = out("du -smc $LQ_EXT/cache $LQ_DATA/cache 2>/dev/null | tail -1 | cut -f1").toLongOrNull() ?: 0
        sb.appendLine("Cache: ${cacheMb}MB")
        
        val totalMb = parseMb(obbSize) + parseMb(dataSize)
        sb.appendLine(if (totalMb > 3000) "=> Tài nguyên đã FULL" else "=> Tài nguyên chưa đầy đủ")
        return sb.toString()
    }

    private fun parseMb(s: String): Long {
        val n = s.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
        return when {
            s.endsWith("G", true) -> (n * 1024).toLong()
            s.endsWith("M", true) -> n.toLong()
            s.endsWith("K", true) -> (n / 1024).toLong()
            else -> 0L
        }
    }

    // ===== 2. XÓA CACHE/EFFECT RÁC (KHÔNG ĐỤNG VÀO FILE GỐC) =====
    private fun clearCacheAndEffects(): Long {
        var freed = 0L
        val before = out("du -smc $LQ_EXT/cache $LQ_DATA/cache 2>/dev/null | tail -1 | cut -f1").toLongOrNull() ?: 0L

        // Xóa cache
        sh("rm -rf $LQ_EXT/cache/* 2>/dev/null")
        sh("rm -rf $LQ_DATA/cache/* 2>/dev/null")

        // Xóa log, replay, tmp
        sh("rm -rf $LQ_EXT/files/log/* 2>/dev/null")
        sh("rm -rf $LQ_EXT/files/replay/* 2>/dev/null")
        sh("rm -rf $LQ_DATA/files/log/* 2>/dev/null")
        sh("rm -rf $LQ_DATA/files/replay/* 2>/dev/null")
        sh("find $LQ_EXT $LQ_DATA -name '*.tmp' -delete 2>/dev/null")
        sh("find $LQ_EXT $LQ_DATA -name '*.old' -delete 2>/dev/null")

        // Xóa cache effect (giữ nguyên file gốc)
        for (d in listOf("$LQ_EXT/files/effect", "$LQ_EXT/files/effects", "$LQ_DATA/files/effect")) {
            sh("find '$d' -name '*.cache' -delete 2>/dev/null")
            sh("find '$d' -name '*.tmp' -delete 2>/dev/null")
        }
        // Shader cache
        sh("find $LQ_DATA -path '*cache*shader*' -delete 2>/dev/null")

        val after = out("du -smc $LQ_EXT/cache $LQ_DATA/cache 2>/dev/null | tail -1 | cut -f1").toLongOrNull() ?: 0L
        freed += (before - after).coerceAtLeast(0)
        return freed
    }

    // ===== 3. CHỈNH CONFIG GAME (SHARED_PREFS XML) =====
    private fun configFile(): String {
        return "$LQ_DATA/shared_prefs/com.garena.game.kgvn_preferences.xml"
    }

    private fun backupConfigs() {
        val f = configFile()
        sh("cp '$f' '$f.lqopt.bak' 2>/dev/null")
    }

    private fun restoreConfigs() {
        val f = configFile()
        sh("cp '$f.lqopt.bak' '$f' 2>/dev/null")
        Prefs.saveOptimizedVersion(this, "")
    }

    private fun disableUselessEffects() {
        val f = configFile()
        val cmd = """
            sed -i \
            -e 's/<boolean name="enable_avatar" value="true" \/>/<boolean name="enable_avatar" value="false" \/>/g' \
            -e 's/<boolean name="show_avatar" value="true" \/>/<boolean name="show_avatar" value="false" \/>/g' \
            -e 's/<boolean name="show_lobby" value="true" \/>/<boolean name="show_lobby" value="false" \/>/g' \
            -e 's/<boolean name="enable_lobby" value="true" \/>/<boolean name="enable_lobby" value="false" \/>/g' \
            -e 's/<boolean name="kill_effect" value="true" \/>/<boolean name="kill_effect" value="false" \/>/g' \
            -e 's/<boolean name="show_kill_effect" value="true" \/>/<boolean name="show_kill_effect" value="false" \/>/g' \
            -e 's/<boolean name="button_effect" value="true" \/>/<boolean name="button_effect" value="false" \/>/g' \
            '$f' 2>/dev/null
        """.trimIndent()
        sh(cmd)
    }

    private fun forceLowestGraphics(mode: String) {
        val f = configFile()
        val (res, shadow, effect, texture) = when (mode) {
            "low"      -> listOf("0.4", "0", "0", "0")
            "balanced" -> listOf("0.7", "1", "1", "1")
            "high"     -> listOf("1.0", "2", "2", "2")
            else       -> listOf("0.4", "0", "0", "0")
        }
        val cmd = """
            sed -i \
            -e 's/<int name="ShadowQuality" value="[0-9]*" \/>/<int name="ShadowQuality" value="$shadow" \/>/g' \
            -e 's/<int name="EffectQuality" value="[0-9]*" \/>/<int name="EffectQuality" value="$effect" \/>/g' \
            -e 's/<int name="GraphicsQuality" value="[0-9]*" \/>/<int name="GraphicsQuality" value="$texture" \/>/g' \
            -e 's/<int name="TextureQuality" value="[0-9]*" \/>/<int name="TextureQuality" value="$texture" \/>/g' \
            -e 's/<float name="ResolutionScale" value="[0-9.]*" \/>/<float name="ResolutionScale" value="$res" \/>/g' \
            -e 's/<boolean name="AntiAliasing" value="true" \/>/<boolean name="AntiAliasing" value="false" \/>/g' \
            '$f' 2>/dev/null
        """.trimIndent()
        sh(cmd)
    }

    // ===== HELPERS =====
    private fun emit(progress: Int, msg: String) {
        ProgressState.update(progress, msg)
        updateNotif(msg, progress)
    }

    private fun sh(cmd: String) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                val c = Class.forName("rikka.shizuku.Shizuku")
                val m = c.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                m.isAccessible = true
                val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as ShizukuRemoteProcess
                p.waitFor()
            }
        } catch (_: InvocationTargetException) { }
        catch (_: Exception) { }
    }

    private fun out(cmd: String): String {
        return try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                val c = Class.forName("rikka.shizuku.Shizuku")
                val m = c.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                m.isAccessible = true
                val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as ShizukuRemoteProcess
                val sb = StringBuilder()
                val t = Thread { p.inputStream.bufferedReader().forEachLine { sb.appendLine(it) } }
                t.start(); p.waitFor(); t.join(500)
                sb.toString().trim()
            } else ""
        } catch (_: Exception) { "" }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "LQ Optimizer", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String, progress: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LQ Game Optimizer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

    private fun updateNotif(text: String, progress: Int) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1, buildNotif(text, progress))
    }

    override fun onDestroy() { job.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
