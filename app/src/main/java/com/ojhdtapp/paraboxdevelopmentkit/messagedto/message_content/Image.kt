package com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content

import android.net.Uri
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * 图片消息类型
 * @param url 图片URL
 * @param width 图片宽度
 * @param height 图片高度，该属性暂时空置
 * @param fileName 图片文件名
 * @param uri 文件URI，需授予权限才可供主端使用
 * @param cloudType 云端存储服务类型。资源需要从云端存储服务获取时使用
 * @param cloudId 云端存储资源ID。资源需要从云端存储服务获取时使用
 * @since 1.0.0
 * @see MessageContent
 */
@Parcelize
data class Image(
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fileName: String? = null,
    val uri: Uri? = null,
    val cloudType: Int? = null,
    val cloudId: String? = null,
) : MessageContent {
    @IgnoredOnParcel
    val type = MessageContent.IMAGE
    override fun getContentString(): String {
        return "[图片]"
    }
}