package com.ojhdtapp.paraboxdevelopmentkit.connector

/**
 * 框架在消息传输过程中使用的约定常量。请避免自定义与其冲突的常量
 * @since 1.0.0
 */
object ParaboxKey {
    // 指定连接双方的类型
    const val CLIENT_MAIN_APP = 255650
    const val CLIENT_CONTROLLER = 255651
    const val CLIENT_SERVICE = 255652
    // 指定消息类型
    const val TYPE_COMMAND = 255659
    const val TYPE_NOTIFICATION = 255658
    const val TYPE_REQUEST = 255657
    const val TYPE_WELCOME_TEXT = 255656
    // 指定命令类型
    const val COMMAND_START_SERVICE = 2556510
    const val COMMAND_STOP_SERVICE = 2556511
    const val COMMAND_FORCE_STOP_SERVICE = 2556512
    const val COMMAND_SEND_MESSAGE = 2556513
    const val COMMAND_RECALL_MESSAGE = 2556514
    const val COMMAND_REFRESH_MESSAGE = 2556515
    const val COMMAND_GET_STATE = 2556516
    // 指定通知类型
    const val NOTIFICATION_STATE_UPDATE = 2556520
    const val NOTIFICATION_MAIN_APP_LAUNCH = 2556521
    const val NOTIFICATION_UPLOAD_FILE_PROGRESS = 2556522
    // 指定请求类型
    const val REQUEST_RECEIVE_MESSAGE = 2556530
    // 指定错误类型
    const val ERROR_TIMEOUT = 2556560
    const val ERROR_DISCONNECTED = 2556561
    const val ERROR_REPEATED_CALL = 2556562
    const val ERROR_RESOURCE_NOT_FOUND = 2556563
    const val ERROR_SEND_FAILED = 2556564
    // 指定核心服务状态
    const val STATE_STOP = 2556570
    const val STATE_PAUSE = 2556571
    const val STATE_ERROR = 2556572
    const val STATE_LOADING = 2556573
    const val STATE_RUNNING = 2556574
}