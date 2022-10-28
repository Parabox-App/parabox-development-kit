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
 * @param cloudType 云端存储服务类型。资源需要从云端存储服务获取时使用
 * @param cloudId 云端存储资源ID。资源需要从云端存储服务获取时使用
 * @since 1.0.0
 * @see MessageContent
 */
@Parcelize
data class Audio(
    val url: String? = null,
    val length: Long = 0L,
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val uri: Uri? = null,
    val cloudType: Int? = null,
    val cloudId: String? = null,
) : MessageContent {
    @IgnoredOnParcel
    val type = MessageContent.AUDIO
    override fun getContentString(): String {
        return "[语音]"
    }
}
