package com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content

import com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content.MessageContent
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * 纯文本消息类型
 * @param text 文本
 * @since 1.0.0
 * @see MessageContent
 */
@Parcelize
data class PlainText(val text: String) : MessageContent {
    @IgnoredOnParcel
    val type = MessageContent.PLAIN_TEXT
    override fun getContentString(): String {
        return text
    }
}
