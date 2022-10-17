package com.ojhdtapp.paraboxdevelopmentkit.messagedto.message_content

import android.os.Parcelable

/**
 * 消息内容公用接口
 * @since 1.0.0
 */
interface MessageContent : Parcelable {
    companion object {
        const val PLAIN_TEXT = 0
        const val IMAGE = 1
        const val AT = 2
        const val AUDIO = 3
        const val QUOTE_REPLY = 4
        const val AT_ALL = 5
        const val FILE = 6
    }

    fun getContentString(): String
}

/**
 * 获得 [MessageContent] 列表的字符串表示
 * @since 1.0.0
 */
fun List<MessageContent>.getContentString(): String {
    val builder = StringBuilder()
    forEachIndexed { index, messageContent ->
        builder.append(messageContent.getContentString())
        if (index != lastIndex) {
            builder.append(" ")
        }
    }
    return builder.toString()
}