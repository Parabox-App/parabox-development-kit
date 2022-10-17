package com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content

import android.net.Uri
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * 图片消息类型
 * @param url 图片URL
 * @param width 图片宽度
 * @param height 图片高度，该属性暂时空置
 * @param uri 文件URI，需授予权限才可供主端使用
 * @since 1.0.0
 * @see MessageContent
 */
@Parcelize
data class Image(
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val uri: Uri? = null,
) : MessageContent {
    @IgnoredOnParcel
    val type = MessageContent.IMAGE
    override fun getContentString(): String {
        return "[图片]"
    }
}