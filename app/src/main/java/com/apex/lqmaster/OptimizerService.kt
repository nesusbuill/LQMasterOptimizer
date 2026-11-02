    private fun sh(cmd: String) {
        try {
            // Kiểm tra quyền Shizuku
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                // Sử dụng Reflection để gọi hàm newProcess (vì hàm này đã bị ẩn/private trong Shizuku 13.x)
                val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                val newProcessMethod = shizukuClass.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                // Cho phép truy cập vào hàm private
                newProcessMethod.isAccessible = true
                
                // Gọi hàm và thực thi lệnh
                val process = newProcessMethod.invoke(
                    null,
                    arrayOf("sh", "-c", cmd),
                    null,
                    null
                ) as rikka.shizuku.ShizukuRemoteProcess
                
                process.waitFor()
            }
        } catch (e: Exception) {
            // Log lỗi nếu cần thiết, nhưng không làm crash app
            e.printStackTrace()
        }
    }
