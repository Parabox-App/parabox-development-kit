package com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content

import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * 文件消息类型
 * @param url 文件下载URL
 * @param name 文件名
 * @param extension 文件扩展名
 * @param size 文件大小
 * @param lastModifiedTime 上次修改时间戳
 * @param expiryTime 过期时间。该属性暂时空置
 * @param uri 文件URI，需授予权限才可供主端使用
 * @since 1.0.0
 * @see MessageContent
 */
@Parcelize
data class File(
    val url: String? = null,
    val name: String,
    val extension: String,
    val size: Long,
    val lastModifiedTime: Long,
    val expiryTime: Long? = null,
    val uri: String? = null
): MessageContent {
    @IgnoredOnParcel
    val type = MessageContent.FILE
    override fun getContentString(): String {
        return "[文件]"
    }
}
