package com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content

import com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content.MessageContent
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * 引用回复消息类型
 * @param quoteMessageSenderName 被引用消息发送者名称
 * @param quoteMessageTimestamp 被引用消息发送时间戳
 * @param quoteMessageId 被引用消息ID
 * @param quoteMessageContent 被引用消息的 [MessageContent] 列表。
 * @since 1.0.0
 * @see MessageContent
 */
@Parcelize
data class QuoteReply(
    val quoteMessageSenderName: String?,
    val quoteMessageTimestamp: Long?,
    val quoteMessageId: Long?,
    val quoteMessageContent: List<MessageContent>?
) : MessageContent {
    @IgnoredOnParcel
    val type = MessageContent.QUOTE_REPLY
    override fun getContentString(): String {
        return "[引用回复]"
    }
}