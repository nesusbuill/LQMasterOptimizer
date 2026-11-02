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
    private val LQ_OBB = "/sdcard/Android/obb/$LQ_PKG"
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
        val action = intent?.action
        val mode = intent?.getStringExtra("MODE") ?: "low"

        scope.launch {
            try {
                when (action) {
                    "ACTION_SCAN" -> {
                        emit(20, "Đang quét file tài nguyên LQ...")
                        emit(100, scanResources())
                        delay(3000); ProgressState.finish(); stopSelf(); return@launch
                    }
                    "ACTION_CLEAR_EFFECTS" -> {
                        emit(20, "Đang dọn rác toàn diện...")
                        val freed = nukeGarbageEverywhere()
                        emit(100, "Đã xóa ${freed}MB rác")
                        delay(2000); ProgressState.finish(); stopSelf(); return@launch
                    }
                    "ACTION_RESTORE" -> {
                        emit(40, "Đang khôi phục config gốc...")
                        restoreConfigs()
                        emit(100, "Đã khôi phục!")
                        delay(2000); ProgressState.finish(); stopSelf(); return@launch
                    }
                }

                // ============ QUY TRÌNH OPTIMIZE CHÍNH ============
                emit(8, "Sao lưu config gốc...")
                backupAllConfigs()
                delay(400)

                emit(20, "Ép đồ họa trong tất cả shared_prefs...")
                forceAllSharedPrefs(mode)
                delay(400)

                emit(32, "Tắt Avatar, Sảnh, Hiệu ứng hạ...")
                disableEffectsEverywhere()
                delay(400)

                emit(44, "Xóa file Avatar/Sảnh cache...")
                nukeAvatarAndLobby()
                delay(400)

                emit(56, "Tắt Video/Animation preview tướng...")
                disableHeroPreviewAnimation()
                delay(400)

                emit(68, "Tinh chỉnh JSON config...")
                tweakJsonConfigs(mode)
                delay(400)

                emit(80, "Dọn rác toàn diện...")
                val freed = nukeGarbageEverywhere()
                delay(400)

                emit(92, "Force-stop game reload config...")
                sh("am force-stop $LQ_PKG")
                delay(800)

                emit(100, "Xong! Giải phóng ${freed}MB. Mở LQ lại.")
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

    // ============ 1. SCAN (CHỈ ĐỌC) ============
    private fun scanResources(): String {
        val sb = StringBuilder()
        val obbSize = out("du -sh $LQ_OBB 2>/dev/null | awk '{print \$1}'").ifEmpty { "?" }
        sb.appendLine("OBB (Skin/Map): $obbSize")
        sb.appendLine("Data LQ: ${out("du -sh $LQ_EXT 2>/dev/null | awk '{print \$1}'").ifEmpty { "?" }}")
        sb.appendLine("Cache: ${out("du -smc $LQ_EXT/cache $LQ_DATA/cache 2>/dev/null | tail -1 | cut -f1").ifEmpty { "0" }}MB")
        sb.appendLine("Avatar/Sảnh cache: ${out("du -smc $LQ_EXT/files/avatar $LQ_EXT/files/lobby $LQ_EXT/files/CDNImage 2>/dev/null | tail -1 | cut -f1").ifEmpty { "0" }}MB")
        sb.appendLine("Preview/Video: ${out("du -smc $LQ_EXT/files/preview $LQ_EXT/files/showcase $LQ_EXT/cache/video 2>/dev/null | tail -1 | cut -f1").ifEmpty { "0" }}MB")
        sb.appendLine("Log/Replay: ${out("du -smc $LQ_EXT/files/log $LQ_EXT/files/replay 2>/dev/null | tail -1 | cut -f1").ifEmpty { "0" }}MB")
        val totalMb = parseMb(obbSize) + parseMb(out("du -sh $LQ_EXT 2>/dev/null | awk '{print \$1}'"))
        sb.appendLine(if (totalMb > 3000) "=> Tài nguyên FULL" else "=> Chưa đầy đủ")
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

    // ============ 2. BACKUP TẤT CẢ CONFIG ============
    private fun backupAllConfigs() {
        sh("find $LQ_DATA/shared_prefs -name '*.xml' -exec cp {} {}.lqopt.bak \\; 2>/dev/null")
        sh("find $LQ_DATA/files -name '*setting*.json' -o -name '*graphic*.json' -o -name '*option*.json' -o -name '*config*.json' 2>/dev/null | while read f; do cp \"\$f\" \"\$f.lqopt.bak\"; done")
    }

    // ============ 3. ÉP ĐỒ HỌA TRONG TẤT CẢ XML ============
    private fun forceAllSharedPrefs(mode: String) {
        val (res, shadow, effect, texture) = when (mode) {
            "low"      -> listOf("0.4", "0", "0", "0")
            "balanced" -> listOf("0.7", "1", "1", "1")
            "high"     -> listOf("1.0", "2", "2", "2")
            else       -> listOf("0.4", "0", "0", "0")
        }
        sh("""
            find $LQ_DATA/shared_prefs -name '*.xml' -exec sed -i \
            -e 's/<int name="ShadowQuality" value="[0-9]*" \/>/<int name="ShadowQuality" value="$shadow" \/>/g' \
            -e 's/<int name="EffectQuality" value="[0-9]*" \/>/<int name="EffectQuality" value="$effect" \/>/g' \
            -e 's/<int name="TextureQuality" value="[0-9]*" \/>/<int name="TextureQuality" value="$texture" \/>/g' \
            -e 's/<int name="GraphicsQuality" value="[0-9]*" \/>/<int name="GraphicsQuality" value="$texture" \/>/g' \
            -e 's/<float name="ResolutionScale" value="[0-9.]*" \/>/<float name="ResolutionScale" value="$res" \/>/g' \
            -e 's/<boolean name="AntiAliasing" value="true" \/>/<boolean name="AntiAliasing" value="false" \/>/g' \
            -e 's/<boolean name="EnableHDR" value="true" \/>/<boolean name="EnableHDR" value="false" \/>/g' \
            {} \;
        """.trimIndent())
    }

    // ============ 4. TẮT HIỆU ỨNG ============
    private fun disableEffectsEverywhere() {
        sh("""
            find $LQ_DATA/shared_prefs -name '*.xml' -exec sed -i \
            -e 's/<boolean name="enable_avatar" value="true" \/>/<boolean name="enable_avatar" value="false" \/>/g' \
            -e 's/<boolean name="show_avatar" value="true" \/>/<boolean name="show_avatar" value="false" \/>/g' \
            -e 's/<boolean name="show_lobby" value="true" \/>/<boolean name="show_lobby" value="false" \/>/g' \
            -e 's/<boolean name="enable_lobby" value="true" \/>/<boolean name="enable_lobby" value="false" \/>/g' \
            -e 's/<boolean name="kill_effect" value="true" \/>/<boolean name="kill_effect" value="false" \/>/g' \
            -e 's/<boolean name="show_kill_effect" value="true" \/>/<boolean name="show_kill_effect" value="false" \/>/g' \
            -e 's/<boolean name="button_effect" value="true" \/>/<boolean name="button_effect" value="false" \/>/g' \
            -e 's/<boolean name="particle_effect" value="true" \/>/<boolean name="particle_effect" value="false" \/>/g' \
            {} \;
        """.trimIndent())

        for (p in listOf("*setting*.json", "*graphic*.json", "*option*.json", "*config*.json")) {
            sh("""
                find $LQ_DATA/files -name '$p' -exec sed -i \
                -e 's/"enable_avatar"\s*:\s*true/"enable_avatar":false/g' \
                -e 's/"show_avatar"\s*:\s*true/"show_avatar":false/g' \
                -e 's/"show_lobby"\s*:\s*true/"show_lobby":false/g' \
                -e 's/"kill_effect"\s*:\s*true/"kill_effect":false/g' \
                {} \;
            """.trimIndent())
        }
    }

    // ============ 5. XÓA AVATAR + SẢNH CACHE ============
    // Xóa avatar/sảnh = giảm RAM/VRAM cực mạnh ở màn hình chính
    // KHÔNG đụng skin/obb → vào trận skin vẫn full
    private fun nukeAvatarAndLobby() {
        // Xóa thư mục avatar (ảnh đại diện tướng ở lobby)
        for (d in listOf(
            "$LQ_EXT/files/avatar",
            "$LQ_EXT/files/avatars",
            "$LQ_EXT/files/hero_avatar",
            "$LQ_EXT/files/head_icon",
            "$LQ_EXT/files/headicon",
            "$LQ_EXT/cache/avatar",
            "$LQ_EXT/cache/avatars",
            "$LQ_DATA/files/avatar",
            "$LQ_DATA/cache/avatar"
        )) {
            sh("rm -rf '$d'/* 2>/dev/null")
        }

        // Xóa sảnh/lobby background
        for (d in listOf(
            "$LQ_EXT/files/lobby",
            "$LQ_EXT/files/lobby_bg",
            "$LQ_EXT/files/lobby_background",
            "$LQ_EXT/files/CDNImage",
            "$LQ_EXT/files/cdn_image",
            "$LQ_EXT/cache/lobby",
            "$LQ_DATA/files/lobby",
            "$LQ_DATA/cache/lobby"
        )) {
            sh("rm -rf '$d'/* 2>/dev/null")
        }

        // Xóa ảnh/video avatar theo đuôi file (chỉ trong thư mục avatar/lobby)
        sh("find $LQ_EXT $LQ_DATA -path '*avatar*' \\( -name '*.png' -o -name '*.jpg' -o -name '*.webp' -o -name '*.ktx' -o -name '*.astc' \\) -delete 2>/dev/null")
        sh("find $LQ_EXT $LQ_DATA -path '*lobby*' \\( -name '*.png' -o -name '*.jpg' -o -name '*.webp' -o -name '*.mp4' -o -name '*.webm' -o -name '*.ktx' -o -name '*.astc' \\) -delete 2>/dev/null")
        sh("find $LQ_EXT $LQ_DATA -path '*CDNImage*' -delete 2>/dev/null")
    }

    // ============ 6. TẮT VIDEO/ANIMATION PREVIEW TƯỚNG ============
    private fun disableHeroPreviewAnimation() {
        // Tắt cờ trong XML
        sh("""
            find $LQ_DATA/shared_prefs -name '*.xml' -exec sed -i \
            -e 's/<boolean name="enable_hero_preview" value="true" \/>/<boolean name="enable_hero_preview" value="false" \/>/g' \
            -e 's/<boolean name="show_hero_preview" value="true" \/>/<boolean name="show_hero_preview" value="false" \/>/g' \
            -e 's/<boolean name="enable_hero_animation" value="true" \/>/<boolean name="enable_hero_animation" value="false" \/>/g' \
            -e 's/<boolean name="show_hero_animation" value="true" \/>/<boolean name="show_hero_animation" value="false" \/>/g' \
            -e 's/<boolean name="hero_showcase" value="true" \/>/<boolean name="hero_showcase" value="false" \/>/g' \
            -e 's/<boolean name="showcase_enabled" value="true" \/>/<boolean name="showcase_enabled" value="false" \/>/g' \
            -e 's/<boolean name="pick_animation" value="true" \/>/<boolean name="pick_animation" value="false" \/>/g' \
            -e 's/<boolean name="pick_video" value="true" \/>/<boolean name="pick_video" value="false" \/>/g' \
            -e 's/<boolean name="video_preview" value="true" \/>/<boolean name="video_preview" value="false" \/>/g' \
            -e 's/<int name="hero_preview_quality" value="[0-9]*" \/>/<int name="hero_preview_quality" value="0" \/>/g' \
            -e 's/<int name="showcase_quality" value="[0-9]*" \/>/<int name="showcase_quality" value="0" \/>/g' \
            {} \;
        """.trimIndent())

        // Tắt cờ trong JSON
        for (p in listOf("*setting*.json", "*graphic*.json", "*option*.json", "*config*.json", "*hero*.json")) {
            sh("""
                find $LQ_DATA/files -name '$p' -exec sed -i \
                -e 's/"enable_hero_preview"\s*:\s*true/"enable_hero_preview":false/g' \
                -e 's/"show_hero_preview"\s*:\s*true/"show_hero_preview":false/g' \
                -e 's/"enable_hero_animation"\s*:\s*true/"enable_hero_animation":false/g' \
                -e 's/"hero_showcase"\s*:\s*true/"hero_showcase":false/g' \
                -e 's/"showcase_enabled"\s*:\s*true/"showcase_enabled":false/g' \
                -e 's/"pick_animation"\s*:\s*true/"pick_animation":false/g' \
                -e 's/"pick_video"\s*:\s*true/"pick_video":false/g' \
                -e 's/"video_preview"\s*:\s*true/"video_preview":false/g' \
                {} \;
            """.trimIndent())
        }

        // Xóa file preview/video/showcase cache
        for (d in listOf(
            "$LQ_EXT/files/preview",
            "$LQ_EXT/files/hero_preview",
            "$LQ_EXT/files/showcase",
            "$LQ_EXT/files/hero_video",
            "$LQ_EXT/files/video",
            "$LQ_EXT/cache/preview",
            "$LQ_EXT/cache/video",
            "$LQ_EXT/cache/showcase",
            "$LQ_DATA/files/preview",
            "$LQ_DATA/files/showcase",
            "$LQ_DATA/cache/preview",
            "$LQ_DATA/cache/video"
        )) {
            sh("rm -rf '$d'/* 2>/dev/null")
        }

        // Xóa video preview (.mp4/.webm/.bk2/.usm)
        sh("find $LQ_EXT $LQ_DATA -path '*preview*' \\( -name '*.mp4' -o -name '*.webm' -o -name '*.bk2' -o -name '*.usm' \\) -delete 2>/dev/null")
        sh("find $LQ_EXT $LQ_DATA -path '*showcase*' \\( -name '*.mp4' -o -name '*.webm' -o -name '*.bk2' -o -name '*.usm' \\) -delete 2>/dev/null")
        sh("find $LQ_EXT $LQ_DATA -path '*hero*video*' \\( -name '*.mp4' -o -name '*.webm' \\) -delete 2>/dev/null")

        // Xóa animation preview (.skel/.atlas) — CHỈ trong showcase/preview
        sh("find $LQ_EXT $LQ_DATA -path '*showcase*' \\( -name '*.skel' -o -name '*.atlas' \\) -delete 2>/dev/null")
        sh("find $LQ_EXT $LQ_DATA -path '*hero_preview*' \\( -name '*.skel' -o -name '*.atlas' \\) -delete 2>/dev/null")
    }

    // ============ 7. TINH CHỈNH JSON CONFIG ============
    private fun tweakJsonConfigs(mode: String) {
        val target = when (mode) { "high" -> "2"; "balanced" -> "1"; else -> "0" }
        for (p in listOf("*setting*.json", "*graphic*.json", "*option*.json", "*config*.json")) {
            sh("""
                find $LQ_DATA/files -name '$p' -exec sed -i \
                -e 's/"shadowQuality"\s*:\s*[0-9]/"shadowQuality":$target/g' \
                -e 's/"effectQuality"\s*:\s*[0-9]/"effectQuality":$target/g' \
                -e 's/"textureQuality"\s*:\s*[0-9]/"textureQuality":$target/g' \
                -e 's/"graphicsQuality"\s*:\s*[0-9]/"graphicsQuality":$target/g' \
                -e 's/"particleQuality"\s*:\s*[0-9]/"particleQuality":$target/g' \
                {} \;
            """.trimIndent())
        }
    }

    // ============ 8. DỌN RÁC TOÀN DIỆN ============
    private fun nukeGarbageEverywhere(): Long {
        val before = out("du -sm $LQ_DATA $LQ_EXT 2>/dev/null | awk '{s+=\$1} END {print s}'").toLongOrNull() ?: 0L

        // File rác theo đuôi
        sh("find $LQ_DATA $LQ_EXT -name '*.tmp' -delete 2>/dev/null")
        sh("find $LQ_DATA $LQ_EXT -name '*.old' -delete 2>/dev/null")
        sh("find $LQ_DATA $LQ_EXT -name '*.log' -delete 2>/dev/null")
        sh("find $LQ_DATA $LQ_EXT -name '*.cache' -delete 2>/dev/null")
        sh("find $LQ_DATA $LQ_EXT -name '*.bak' ! -name '*.lqopt.bak' -delete 2>/dev/null")

        // Làm sạch thư mục cache/log/replay/temp
        sh("find $LQ_DATA $LQ_EXT -type d -name 'cache' -exec find {} -mindepth 1 -delete \\; 2>/dev/null")
        sh("find $LQ_DATA $LQ_EXT -type d -name 'log' -exec find {} -mindepth 1 -delete \\; 2>/dev/null")
        sh("find $LQ_DATA $LQ_EXT -type d -name 'logs' -exec find {} -mindepth 1 -delete \\; 2>/dev/null")
        sh("find $LQ_DATA $LQ_EXT -type d -name 'replay' -exec find {} -mindepth 1 -delete \\; 2>/dev/null")
        sh("find $LQ_DATA $LQ_EXT -type d -name 'temp' -exec find {} -mindepth 1 -delete \\; 2>/dev/null")
        sh("find $LQ_DATA $LQ_EXT -type d -name 'tmp' -exec find {} -mindepth 1 -delete \\; 2>/dev/null")
        sh("find $LQ_DATA $LQ_EXT -type d -name 'crash' -exec find {} -mindepth 1 -delete \\; 2>/dev/null")

        // Shader cache
        sh("find $LQ_DATA -path '*cache*shader*' -delete 2>/dev/null")
        sh("find $LQ_DATA -path '*cache*gpu*' -delete 2>/dev/null")

        val after = out("du -sm $LQ_DATA $LQ_EXT 2>/dev/null | awk '{s+=\$1} END {print s}'").toLongOrNull() ?: 0L
        return (before - after).coerceAtLeast(0)
    }

    // ============ 9. RESTORE ============
    private fun restoreConfigs() {
        sh("find $LQ_DATA/shared_prefs -name '*.xml.lqopt.bak' -exec sh -c 'mv \"\$1\" \"\${1%.lqopt.bak}\"' _ {} \\; 2>/dev/null")
        sh("find $LQ_DATA/files -name '*.json.lqopt.bak' -exec sh -c 'mv \"\$1\" \"\${1%.lqopt.bak}\"' _ {} \\; 2>/dev/null")
        Prefs.saveOptimizedVersion(this, "")
    }

    // ============ HELPERS ============
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

    private fun out(cmd: String): String {
        return try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                val c = Class.forName("rikka.shizuku.Shizuku")
                val m = c.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                m.isAccessible = true
                val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as ShizukuRemoteProcess
                val sb = StringBuilder()
                val t = Thread { p.inputStream.bufferedReader().forEachLine { sb.appendLine(it) } }
                t.start(); p.waitFor(); t.join(500); sb.toString().trim()
            } else ""
        } catch (_: Exception) { "" }
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
