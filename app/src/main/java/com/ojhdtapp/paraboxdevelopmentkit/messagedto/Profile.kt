package com.ojhdtapp.paraboxdevelopmentkit.messagedto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 账户信息
 * @param name 名称
 * @param avatar 头像URL
 * @param id 需保证唯一性，用于@时识别发送者
 */
@Parcelize
data class Profile(
    val name: String,
    val avatar: String?,
    val id: Long?
) : Parcelable