package com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content

import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * At某人消息类型
 * @param target 识别@对象的唯一ID
 * @param name @对象名称
 * @since 1.0.0
 * @see MessageContent
 */
@Parcelize
data class At(val target: Long, val name: String) : MessageContent {
    @IgnoredOnParcel
    val type = MessageContent.AT
    override fun getContentString(): String {
        return "@$name"
    }
}
