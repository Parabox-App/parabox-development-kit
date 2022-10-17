package com.ojhdtapp.paraboxdevelopmentkit.messagedto

import android.os.Parcelable
import com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content.MessageContent

import kotlinx.parcelize.Parcelize
/**
 * 待接收消息传输对象。
 *
 * 可参阅 https://docs.parabox.ojhdt.dev/developer/#_12
 * @param contents [MessageContent] 列表，表示消息内容
 * @param profile Profile 的实例。描述当前消息的发送者信息，包括昵称，头像等。id需保证唯一性，用于@时识别发送者
 * @param subjectProfile Profile 的实例。描述当前消息所属会话的信息。对于私聊会话，该值与profile一致。对于群聊，可用于描述群聊信息，包括昵称，头像等。id需保证唯一性，用于唯一识别当前会话
 * @param timestamp 消息发送时间时间戳
 * @param messageId 需保证唯一性，用于唯一识别该条消息。允许置空，数据库将为其自动分配id，但置空将导致消息撤回，缓存机制等失效
 * @param pluginConnection 用于描述该条消息所属会话的连接信息
 * @since 1.0.0
 * @see PluginConnection
 */
@Parcelize
data class ReceiveMessageDto(
    val contents: List<MessageContent>,
    val profile: Profile,
    val subjectProfile: Profile,
    val timestamp: Long,
    val messageId: Long?,
    val pluginConnection: PluginConnection
) : Parcelable