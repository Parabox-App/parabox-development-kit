package com.ojhdtapp.paraboxdevelopmentkit.messagedto

import android.os.Parcelable
import com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content.MessageContent
import kotlinx.parcelize.Parcelize

/**
 * 待发送消息传输对象。
 *
 * 可参阅 https://docs.parabox.ojhdt.dev/developer/#_13
 * @param contents [MessageContent] 列表，表示消息内容
 * @param timestamp 消息发送时间时间戳
 * @param pluginConnection 用于描述该条消息所属会话的连接消息
 * @param messageId 唯一识别该条消息，由系统生成。建议以 map 临时保存，用于撤回该条消息
 * @since 1.0.0
 * @see PluginConnection
 */
@Parcelize
data class SendMessageDto(
    val contents: List<MessageContent>,
    val timestamp: Long,
    val pluginConnection: PluginConnection,
    val messageId: Long?,
) : Parcelable