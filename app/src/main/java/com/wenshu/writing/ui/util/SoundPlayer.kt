package com.wenshu.writing.ui.util

import android.content.Context
import android.media.RingtoneManager

/** 播放提示音：AI 回复完成、弹出确认窗口时使用。 */
object SoundPlayer {

    val SOUND_TYPES = listOf(
        "notification" to "默认提示音",
        "ringtone" to "清脆铃声",
        "alarm" to "悠长闹铃"
    )

    fun labelOf(type: String): String =
        SOUND_TYPES.firstOrNull { it.first == type }?.second ?: SOUND_TYPES.first().second

    fun play(context: Context, soundType: String) {
        val type = when (soundType) {
            "ringtone" -> RingtoneManager.TYPE_RINGTONE
            "alarm" -> RingtoneManager.TYPE_ALARM
            else -> RingtoneManager.TYPE_NOTIFICATION
        }
        runCatching {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(type))?.play()
        }
    }
}
