package com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content

import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * At某人消息类型
 * @param latitude 位置经度
 * @param longitude 位置纬度
 * @param name 可选，位置名称
 * @param description 可选，位置描述
 * @since 1.0.6
 * @see MessageContent
 */
@Parcelize
data class Location(val latitude: Double, val longitude: Double, val name: String?, val description: String?) : MessageContent {
    @IgnoredOnParcel
    val type = MessageContent.LOCATION
    override fun getContentString(): String {
        return "[位置]$name"
    }
}
