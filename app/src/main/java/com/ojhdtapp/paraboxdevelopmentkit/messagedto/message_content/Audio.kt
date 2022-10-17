package com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content

import android.net.Uri
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * 语音消息类型
 * @param url 语音文件URL
 * @param length 语音长度
 * @param fileName 语音文件名
 * @param fileSize 语音文件大小
 * @param uri 语音文件URI。需授予权限才可供主端使用
 */
@Parcelize
data class Audio(
    val url: String? = null,
    val length: Long = 0L,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val uri: Uri? = null
) : MessageContent {
    @IgnoredOnParcel
    val type = MessageContent.AUDIO
    override fun getContentString(): String {
        return "[语音]"
    }
}
