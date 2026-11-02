package com.apex.lqmaster

import kotlinx.coroutines.flow.MutableStateFlow

object ProgressState {
    val progress = MutableStateFlow(0)
    val message = MutableStateFlow("")
    val isRunning = MutableStateFlow(false)

    fun start() {
        isRunning.value = true
        update(0, "Đang chuẩn bị...")
    }

    fun update(p: Int, msg: String) {
        progress.value = p
        message.value = msg
    }

    fun finish() {
        isRunning.value = false
        update(100, "Tối ưu hoàn tất!")
    }

    fun reset() {
        isRunning.value = false
        update(0, "")
    }
}
