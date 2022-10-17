package com.ojhdtapp.paraboxdevelopmentkit.connector

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 消息元数据。携带描述消息的基本信息
 * @param commandOrRequest 命令或请求类型
 * @param timestamp 消息发送时间
 * @param sender 消息发送客户端，可选值为MAIN_APP，CONTROLLER或SERVICE
 * @param key 单独标识该消息的key，由时间戳及8位随机数字序列组成
 * @since 1.0.0
 * @see ParaboxKey
 * @see ParaboxActivity.sendCommand
 * @see ParaboxService.sendRequest
 */
@Parcelize
class ParaboxMetadata(
    val commandOrRequest: Int,
    val timestamp: Long,
    val sender: Int,
    val key: String,
) : Parcelable