package com.ojhdtapp.paraboxdevelopmentkit.messagedto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Profile(
    val name: String,
    val avatar: String?,
    val id: Long?
) : Parcelable