package com.ojhdtapp.paraboxdevelopmentkit.connector

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 一次 REQUEST 或 COMMAND 的返回结果。必为一个 [ParaboxResult.Success] 或 [ParaboxResult.Fail] 对象
 * @param commandOrRequest 命令或请求类型
 * @param timestamp 消息发送时间
 * @since 1.0.0
 * @see ParaboxKey
 */
@Parcelize
sealed class ParaboxResult(
    open val commandOrRequest: Int,
    open val timestamp: Long,
) : Parcelable {
    /**
     * 成功的请求或命令结果
     * @param commandOrRequest 命令或请求类型
     * @param timestamp 消息发送时间
     * @param obj 额外数据
     * @since 1.0.0
     * @see ParaboxKey
     */
    data class Success(
        override val commandOrRequest: Int,
        override val timestamp: Long,
        val obj : Bundle = Bundle()
    ) : ParaboxResult(commandOrRequest = commandOrRequest, timestamp = timestamp)

    /**
     * 失败的请求或命令结果
     * @param commandOrRequest 命令或请求类型
     * @param timestamp 消息发送时间
     * @param errorCode 错误码
     * @since 1.0.0
     * @see ParaboxKey
     */
    data class Fail(
        override val commandOrRequest: Int,
        override val timestamp: Long,
        val errorCode: Int,
    ) : ParaboxResult(commandOrRequest = commandOrRequest, timestamp = timestamp)
}