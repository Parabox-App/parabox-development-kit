package com.ojhdtapp.paraboxdevelopmentkit.messagedto

/**
 * ObjectId工具箱。内含构造 ObjectId ，以及从 ObjectId 获取其他信息的辅助方法。
 *
 * ObjectId 可单独识别某一会话。对于插件开发，使用场景不多。
 * @since 1.0.0
 */
object ObjectIdUtil {
    /**
     * 构造 ObjectId 的约定方法
     */
    fun getObjectId(connectionType: Int, sendTargetType: Int, id: Long): Long {
        return "${connectionType}${sendTargetType}${id}".toLong()
    }

    /**
     * 从 ObjectId 获取 [SendTargetType]
     */
    fun getSendTargetType(objectId: Long, connectionTypeLength: Int): Int {
        return objectId.toString()[connectionTypeLength].digitToInt()
    }

    /**
     * 从 ObjectId 获取 Id
     */
    fun getId(objectId: Long, connectionTypeLength: Int) : Long{
        return objectId.toString().substring(connectionTypeLength + 1).toLong()
    }
}

/**
 * 描述会话类型
 * @since 1.0.0
 * @see PluginConnection
 */
object SendTargetType {
    const val USER = 0
    const val GROUP = 1
}