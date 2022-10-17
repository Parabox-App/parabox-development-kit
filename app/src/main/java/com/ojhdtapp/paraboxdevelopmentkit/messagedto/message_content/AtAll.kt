package com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content

import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
/**
 * At全体成员消息类型
 * @since 1.0.0
 * @see MessageContent
 */
@Parcelize
object AtAll: MessageContent {
    @IgnoredOnParcel
    val type = MessageContent.AT_ALL
    override fun getContentString(): String {
        return "@全体成员"
    }
}