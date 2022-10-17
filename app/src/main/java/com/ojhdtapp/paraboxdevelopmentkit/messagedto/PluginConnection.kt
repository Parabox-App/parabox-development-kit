package com.ojhdtapp.paraboxdevelopmentkit.messagedto

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * 描述连接信息
 *
 * 使用方法可参阅 https://docs.parabox.ojhdt.dev/developer/#_11
 * @param connectionType 需与 AndroidManifest.xml 中 META_DATA 声明值一致
 * @param sendTargetType 描述当前会话类型。可选 [SendTargetType.USER] 或 [SendTargetType.GROUP]
 * @param id 用于唯一识别当前会话。与 [ReceiveMessageDto] 配合使用时，需要与 [ReceiveMessageDto.subjectProfile] 中的 id 保持一致
 * @since 1.0.0
 * @see ReceiveMessageDto
 * @see SendMessageDto
 */
@Parcelize
data class PluginConnection(val connectionType: Int, val sendTargetType: Int, val id: Long) :
    Parcelable {
    @IgnoredOnParcel
    val objectId = ObjectIdUtil.getObjectId(connectionType, sendTargetType, id)
    override fun equals(other: Any?): Boolean {
        return if (other is PluginConnection) {
            objectId == other.objectId
        } else
            super.equals(other)
    }

    override fun hashCode(): Int {
        var result = connectionType
        result = 31 * result + objectId.hashCode()
        return result
    }
}
