package com.ojhdtapp.paraboxdevelopmentkit.connector

import android.content.Intent
import android.os.*
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ojhdtapp.paraboxdevelopmentkit.messagedto.ReceiveMessageDto
import com.ojhdtapp.paraboxdevelopmentkit.messagedto.SendMessageDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 核心服务。对接各平台消息接收及发送的核心单元。
 *
 * 一方面与Parabox后台服务绑定，承担与主端通信的任务。另一方面与 [ParaboxActivity] 绑定，向主界面提供运行状态更新。
 *
 * 继承自 [LifecycleService]，可使用 [LifecycleService.lifecycleScope] 进行协程操作。
 *
 * @since 1.0.0
 */
abstract class ParaboxService : LifecycleService() {
    private var serviceState = ParaboxKey.STATE_STOP
    lateinit var paraboxMessenger: Messenger
    private var clientMessenger: Messenger? = null
    private var mainAppMessenger: Messenger? = null

    private val deferredMap = mutableMapOf<String, CompletableDeferred<ParaboxResult>>()
    private val messageUnreceivedMap = mutableMapOf<Long, ReceiveMessageDto>()
    private val messageUnsyncedMap = mutableMapOf<Long, SendMessageDto>()

    /**
     * 启动核心服务
     * @since 1.0.0
     */
    abstract fun onStartParabox()

    /**
     * 关闭核心服务
     * @since 1.0.0
     */
    abstract fun onStopParabox()

    /**
     * 服务状态变更时调用
     * @since 1.0.0
     */
    abstract fun onStateUpdate(state: Int, message: String? = null)
    /**
     * msg.what未匹配预定义Key时屌用，用于自定义Request，Command及Notification。
     *
     * 具体可参阅通信机制说明 https://docs.parabox.ojhdt.dev/developer/#_10
     * @param msg 从核心服务接收到的消息。
     * - msg.what 预定义Key
     * - msg.arg1 发送客户端类型，可选值为 MAIN_APP 或 CONTROLLER
     * - msg.arg2 消息类型，可选值为 REQUEST，COMMAND 或 NOTIFICATION
     * - msg.obj 附加信息，可转换为Bundle类型
     * @param metadata 消息元数据。
     * @since 1.0.0
     * @see ParaboxKey
     */
    abstract fun customHandleMessage(msg: Message, metadata: ParaboxMetadata)

    /**
     * 主客户端尝试通过该插件发送消息时调用
     * @param SendMessageDto 待发送消息传输对象
     * @since 1.0.0
     * @return 布尔值。true表示发送成功，false表示发送失败。
     * @see SendMessageDto
     */
    abstract suspend fun onSendMessage(dto: SendMessageDto): Boolean

    /**
     * 主客户端尝试通过该插件撤回消息时调用
     * @param messageId 尝试撤回消息的消息ID。通过与 [SendMessageDto.messageId] 比对获取待撤回的消息。
     * @since 1.0.0
     * @return 布尔值。true表示撤回成功，false表示撤回失败。
     * @see SendMessageDto
     * @see onSendMessage
     */
    abstract suspend fun onRecallMessage(messageId: Long): Boolean

    /**
     * 主客户端下拉刷新时调用
     * @since 1.0.0
     */
    abstract fun onRefreshMessage()

    /**
     * 主客户端 onStart 回调触发时调用。用于在主客户端启动时进行初始化操作。
     *
     * 于此处调用 [onStartParabox] 可在主客户端启动时自动启动核心服务，但推荐提供开关控制。
     * @since 1.0.0
     */
    abstract fun onMainAppLaunch()

    /**
     * 获取当前核心服务状态
     * @since 1.0.0
     * @return 当前核心服务状态
     * @see ParaboxKey
     */
    fun getServiceState(): Int = serviceState

    /**
     * 回应预定义 Command [ParaboxKey.COMMAND_START_SERVICE] 。不建议由服务自行调用。
     * @param metadata 消息元数据
     * @since 1.0.0
     */
    fun startParabox(metadata: ParaboxMetadata) {
        if (serviceState in listOf<Int>(ParaboxKey.STATE_STOP, ParaboxKey.STATE_ERROR)) {
            onStartParabox()
            sendCommandResponse(
                isSuccess = true,
                metadata = metadata
            )
        } else {
            sendCommandResponse(
                isSuccess = false,
                metadata = metadata,
                errorCode = ParaboxKey.ERROR_REPEATED_CALL
            )
        }
    }

    /**
     * 回应预定义 Command [ParaboxKey.COMMAND_STOP_SERVICE] 。不建议由服务自行调用。
     * @param metadata 消息元数据
     * @since 1.0.0
     */
    fun stopParabox(metadata: ParaboxMetadata) {
        if (serviceState in listOf<Int>(ParaboxKey.STATE_RUNNING)) {
            onStopParabox()
            sendCommandResponse(
                isSuccess = true,
                metadata = metadata
            )
        } else {
            sendCommandResponse(
                isSuccess = false,
                metadata = metadata,
                errorCode = ParaboxKey.ERROR_REPEATED_CALL
            )
        }
    }

    /**
     * 回应预定义 Command [ParaboxKey.COMMAND_FORCE_STOP_SERVICE] 。不建议由服务自行调用。
     * @param metadata 消息元数据
     * @since 1.0.0
     */
    fun forceStopParabox(metadata: ParaboxMetadata) {
        if (serviceState in listOf<Int>(
                ParaboxKey.STATE_RUNNING,
                ParaboxKey.STATE_ERROR,
                ParaboxKey.STATE_LOADING,
                ParaboxKey.STATE_PAUSE
            )
        ) {
            onStopParabox()
            sendCommandResponse(isSuccess = true, metadata = metadata)
        } else {
            sendCommandResponse(
                isSuccess = false,
                metadata = metadata,
                errorCode = ParaboxKey.ERROR_REPEATED_CALL
            )
        }
    }

    /**
     * 更新核心服务状态。
     *
     * 建议于 [onStartParabox]、[onStopParabox] 中调用。用于更新主客户端及插件显示界面显示状态。可参阅开发指引 https://docs.parabox.ojhdt.dev/developer/#_9
     * @param state 新的核心服务状态
     * @param message 附加信息
     * @since 1.0.0
     * @see ParaboxKey
     * @see ParaboxActivity.onParaboxServiceStateChanged
     */
    fun updateServiceState(state: Int, message: String? = null) {
        serviceState = state
        onStateUpdate(state, message)
        sendNotification(ParaboxKey.NOTIFICATION_STATE_UPDATE, Bundle().apply {
            putInt("state", state)
            message?.let { putString("message", it) }
        })
    }

    /**
     * 同时向主端与插件主界面发送一条通知（NOTIFICATION），常用于发送频繁，不需要回复的逻辑。如日志，状态更新等。
     *
     * 由任意一方发起，不需要回复。对接收方是否成功接收不提供保证。
     *
     * 如需详细了解通信机制及自定义 NOTIFICATION，请参阅 https://docs.parabox.ojhdt.dev/developer/#_10
     * @param notification 通知类型
     * @param extra 额外信息
     * @since 1.0.0
     * @see ParaboxKey
     */
    fun sendNotification(notification: Int, extra: Bundle = Bundle()) {
        val timestamp = System.currentTimeMillis()
        val msg = Message.obtain(
            null,
            notification,
            0,
            ParaboxKey.TYPE_NOTIFICATION,
            extra.apply {
                putLong("timestamp", timestamp)
            }).apply {
            replyTo = paraboxMessenger
        }
        try {
            clientMessenger?.send(msg)
            mainAppMessenger?.send(msg)
        } catch (e: DeadObjectException) {
            e.printStackTrace()
        }
    }

    /**
     * 向 Parabox 主端数据库存入一条新消息。（使主端收到一条新消息）。
     *
     * 可参阅 https://docs.parabox.ojhdt.dev/developer/#_12 了解消息接收机制
     * @param dto 待接收消息传输对象
     * @param onResult 结果回调
     * @since 1.0.0
     * @see ReceiveMessageDto
     * @see ParaboxResult
     */
    fun receiveMessage(
        dto: ReceiveMessageDto,
        onResult: (ParaboxResult) -> Unit
    ) {
        sendRequest(request = ParaboxKey.REQUEST_RECEIVE_MESSAGE,
            client = ParaboxKey.CLIENT_MAIN_APP,
            extra = Bundle().apply {
                putParcelable("dto", dto)
            },
            timeoutMillis = 6000,
            onResult = {
                onResult(it)
                dto.messageId?.let { id ->
                    if (it is ParaboxResult.Fail) {
                        messageUnreceivedMap[id] = dto
                    } else {
                        messageUnreceivedMap.remove(id)
                    }
                }
            })
    }

    /**
     * 向 Parabox 主端数据库同步一条已发送的消息。（使主端新增一条已完成的发送记录）。
     *
     * @param dto 待同步消息传输对象
     * @param onResult 结果回调
     * @since 1.0.6
     * @see SendMessageDto
     * @see ParaboxResult
     */
    fun syncMessage(
        dto: SendMessageDto,
        onResult: (ParaboxResult) -> Unit
    ){
        sendRequest(request = ParaboxKey.REQUEST_SYNC_MESSAGE,
            client = ParaboxKey.CLIENT_MAIN_APP,
            extra = Bundle().apply {
                putParcelable("dto", dto)
            },
            timeoutMillis = 6000,
            onResult = {
                onResult(it)
                dto.messageId?.let { id ->
                    if (it is ParaboxResult.Fail) {
                        messageUnsyncedMap[id] = dto
                    } else {
                        messageUnsyncedMap.remove(id)
                    }
                }
            })
    }

    private fun sendMessage(metadata: ParaboxMetadata, dto: SendMessageDto) {
        lifecycleScope.launch {
            if (serviceState == ParaboxKey.STATE_RUNNING) {
                if (onSendMessage(dto)) {
                    // Success
                    sendCommandResponse(
                        isSuccess = true,
                        metadata = metadata
                    )
                } else {
                    sendCommandResponse(
                        isSuccess = false,
                        metadata = metadata,
                        errorCode = ParaboxKey.ERROR_SEND_FAILED
                    )
                }
            } else {
                sendCommandResponse(
                    isSuccess = false,
                    metadata = metadata,
                    errorCode = ParaboxKey.ERROR_DISCONNECTED
                )
            }
        }
    }

    private fun recallMessage(metadata: ParaboxMetadata, messageId: Long) {
        lifecycleScope.launch {
            if (serviceState == ParaboxKey.STATE_RUNNING) {
                if (onRecallMessage(messageId)) {
                    // Success
                    sendCommandResponse(
                        isSuccess = true,
                        metadata = metadata
                    )
                } else {
                    sendCommandResponse(
                        isSuccess = false,
                        metadata = metadata,
                        errorCode = ParaboxKey.ERROR_SEND_FAILED
                    )
                }
            } else {
                sendCommandResponse(
                    isSuccess = false,
                    metadata = metadata,
                    errorCode = ParaboxKey.ERROR_DISCONNECTED
                )
            }
        }
    }

    private fun refreshMessage(metadata: ParaboxMetadata) {
        messageUnreceivedMap.forEach {
            receiveMessage(it.value) {}
        }
        messageUnsyncedMap.forEach {
            syncMessage(it.value) {}
        }
        sendCommandResponse(
            true,
            metadata
        )
        onRefreshMessage()
    }

    private fun sendStateResponse(metadata: ParaboxMetadata) {
        sendCommandResponse(
            isSuccess = true,
            metadata = metadata,
            extra = Bundle().apply {
                putInt("state", serviceState)
            })
    }
    /**
     * 发送 COMMAND 的回送验证。
     *
     * 对于自定义 COMMAND，务必在处理 COMMAND 后调用，否则将导致原 COMMAND 超时。
     *
     * 可参阅 https://docs.parabox.ojhdt.dev/developer/#_10 获取有关通讯机制的更多信息
     * @param isSuccess 本次Request是否正常处理
     * @param metadata 消息元数据
     * @param extra 附加数据，isSuccess 为 true 时，作为 ParaboxResult.Success 的 obj 传递
     * @param errorCode 错误码，isSuccess 为 false 时，作为 ParaboxResult.Fail 的 errorCode 传递
     * @since 1.0.0
     * @see ParaboxActivity.sendCommand
     * @see ParaboxResult
     */
    fun sendCommandResponse(
        isSuccess: Boolean,
        metadata: ParaboxMetadata,
        extra: Bundle = Bundle(),
        errorCode: Int? = null
    ) {
        if (isSuccess) {
            ParaboxResult.Success(
                commandOrRequest = metadata.commandOrRequest,
                timestamp = metadata.timestamp,
                obj = extra,
            )
        } else {
            ParaboxResult.Fail(
                commandOrRequest = metadata.commandOrRequest,
                timestamp = metadata.timestamp,
                errorCode = errorCode!!
            )
        }.also {
            deferredMap[metadata.key]?.complete(it)
//            coreSendCommandResponse(isSuccess, metadata, it)
        }
    }

    private fun coreSendCommandResponse(
        isSuccess: Boolean,
        metadata: ParaboxMetadata,
        result: ParaboxResult,
        extra: Bundle = Bundle()
    ) {
        try {
            when (metadata.sender) {
                ParaboxKey.CLIENT_MAIN_APP -> {
                    val errorCode = if (!isSuccess) {
                        (result as ParaboxResult.Fail).errorCode
                    } else 0
                    val msg = Message.obtain(
                        null,
                        metadata.commandOrRequest,
                        ParaboxKey.CLIENT_MAIN_APP,
                        ParaboxKey.TYPE_COMMAND,
                        extra.apply {
                            putBoolean("isSuccess", isSuccess)
                            putParcelable("metadata", metadata)
                            putInt("errorCode", errorCode)
                        }).apply {
                        replyTo = paraboxMessenger
                    }
                    mainAppMessenger?.send(msg)
                }

                ParaboxKey.CLIENT_CONTROLLER -> {
                    val errorCode = if (!isSuccess) {
                        (result as ParaboxResult.Fail).errorCode
                    } else 0
                    val msg = Message.obtain(
                        null,
                        metadata.commandOrRequest,
                        ParaboxKey.CLIENT_CONTROLLER,
                        ParaboxKey.TYPE_COMMAND,
                        extra.apply {
                            putBoolean("isSuccess", isSuccess)
                            putParcelable("metadata", metadata)
                            putInt("errorCode", errorCode)
                        }).apply {
                        replyTo = paraboxMessenger
                    }
                    clientMessenger?.send(msg)
                }
            }
        } catch (e: DeadObjectException) {
            e.printStackTrace()
        }
    }

    /**
     * 向指定已连接端发送一条请求（REQUEST）。常用于需要确定得到回复才能继续进行的逻辑。如消息发送/接收，更新配置等。
     *
     * 自带回送验证及超时机制。保证每一次通信都必然在超时时间内触发 onResult 回调。
     *
     * Request 与 Command 内部机制近似。如需自定义 Request，可参阅 https://docs.parabox.ojhdt.dev/developer/#command
     * @param request 请求类型
     * @param client 发送对象，可选值为 MAIN_APP 或 CONTROLLER
     * @param extra 附加数据，将作为Message的obj传递
     * @param timeoutMillis 触发超时的时间，单位为毫秒
     * @param onResult 结果回调，超时或核心服务返回结果时调用
     * @since 1.0.0
     * @see ParaboxResult
     * @see ParaboxKey
     */
    fun sendRequest(
        request: Int,
        client: Int,
        extra: Bundle = Bundle(),
        timeoutMillis: Long = 3000,
        onResult: (ParaboxResult) -> Unit
    ) {
        lifecycleScope.launch {
            val timestamp = System.currentTimeMillis()
            val key = "${timestamp}${ParaboxUtil.getRandomNumStr(8)}"
            try {
                withTimeout(timeoutMillis) {
                    val deferred = CompletableDeferred<ParaboxResult>()
                    deferredMap[key] = deferred
                    coreSendRequest(timestamp, key, request, client, extra)
                    deferred.await().also {
                        onResult(it)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                deferredMap[key]?.cancel()
                onResult(
                    ParaboxResult.Fail(
                        request,
                        timestamp,
                        ParaboxKey.ERROR_TIMEOUT
                    )
                )
            } catch (e: RemoteException) {
                deferredMap[key]?.cancel()
                onResult(
                    ParaboxResult.Fail(
                        request,
                        timestamp,
                        ParaboxKey.ERROR_DISCONNECTED
                    )
                )
            }
        }
    }

    private fun coreSendRequest(
        timestamp: Long,
        key: String,
        request: Int,
        client: Int,
        extra: Bundle = Bundle()
    ) {
        val targetClient = when (client) {
            ParaboxKey.CLIENT_CONTROLLER -> clientMessenger
            ParaboxKey.CLIENT_MAIN_APP -> mainAppMessenger
            else -> null
        }
        if (targetClient == null) {
            deferredMap[key]?.complete(
                ParaboxResult.Fail(
                    request, timestamp,
                    ParaboxKey.ERROR_DISCONNECTED
                )
            )
        } else {
            val msg = Message.obtain(null, request, client, ParaboxKey.TYPE_REQUEST, extra.apply {
                putParcelable(
                    "metadata", ParaboxMetadata(
                        commandOrRequest = request,
                        timestamp = timestamp,
                        sender = ParaboxKey.CLIENT_SERVICE,
                        key = key
                    )
                )
            }).apply {
                replyTo = paraboxMessenger
            }
            targetClient.send(msg)
        }
    }

    override fun onCreate() {
        paraboxMessenger = Messenger(CommandHandler())
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return paraboxMessenger.binder
    }


    inner class CommandHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.arg1) {
                ParaboxKey.CLIENT_CONTROLLER -> {
                    clientMessenger = msg.replyTo
                }

                ParaboxKey.CLIENT_MAIN_APP -> {
                    mainAppMessenger = msg.replyTo
                }
            }

            val obj = (msg.obj as Bundle)
            // 对command添加deferred
            when (msg.arg2) {
                ParaboxKey.TYPE_COMMAND -> {
                    lifecycleScope.launch {
                        try {
                            obj.classLoader = ParaboxMetadata::class.java.classLoader
                            val metadata =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    obj.getParcelable("metadata", ParaboxMetadata::class.java)!!
                                } else {
                                    obj.getParcelable<ParaboxMetadata>("metadata")!!
                                }
                            val deferred =
                                CompletableDeferred<ParaboxResult>()
                            deferredMap[metadata.key] = deferred

                            // 指令种类判断
                            when (msg.what) {
                                ParaboxKey.COMMAND_START_SERVICE -> {
                                    startParabox(metadata)
                                }

                                ParaboxKey.COMMAND_STOP_SERVICE -> {
                                    stopParabox(metadata)
                                }

                                ParaboxKey.COMMAND_FORCE_STOP_SERVICE -> {
                                    forceStopParabox(metadata)
                                }

                                ParaboxKey.COMMAND_SEND_MESSAGE -> {
                                    val dto =
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            obj.getParcelable("dto", SendMessageDto::class.java)!!
                                        } else {
                                            obj.getParcelable<SendMessageDto>("dto")!!
                                        }
                                    sendMessage(metadata, dto)
                                }

                                ParaboxKey.COMMAND_RECALL_MESSAGE -> {
                                    val messageId = obj.getLong("messageId")
                                    recallMessage(metadata, messageId)
                                }

                                ParaboxKey.COMMAND_REFRESH_MESSAGE -> {
                                    refreshMessage(metadata)
                                }

                                ParaboxKey.COMMAND_GET_STATE -> {
                                    sendStateResponse(metadata)
                                }

                                else -> customHandleMessage(msg, metadata)
                            }
                            deferred.await().also {
                                val resObj = if (it is ParaboxResult.Success) {
                                    it.obj
                                } else Bundle()
                                coreSendCommandResponse(
                                    it is ParaboxResult.Success,
                                    metadata,
                                    it,
                                    resObj
                                )
                            }
                        } catch (e: RemoteException) {
                            e.printStackTrace()
                        } catch (e: NullPointerException) {
                            e.printStackTrace()
                        } catch (e: ClassNotFoundException) {
                            e.printStackTrace()
                        }
                    }
                }

                ParaboxKey.TYPE_REQUEST -> {
                    try {
                        obj.classLoader = ParaboxMetadata::class.java.classLoader
                        val metadata = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            obj.getParcelable("metadata", ParaboxMetadata::class.java)!!
                        } else {
                            obj.getParcelable<ParaboxMetadata>("metadata")!!
                        }
                        val isSuccess = obj.getBoolean("isSuccess")
                        val errorCode = obj.getInt("errorCode")
                        val result = if (isSuccess) {
                            ParaboxResult.Success(
                                commandOrRequest = metadata.commandOrRequest,
                                timestamp = metadata.timestamp,
                                obj = obj
                            )
                        } else {
                            ParaboxResult.Fail(
                                commandOrRequest = metadata.commandOrRequest,
                                timestamp = metadata.timestamp,
                                errorCode = errorCode
                            )
                        }
                        deferredMap[metadata.key]?.complete(result)
                    } catch (e: NullPointerException) {
                        e.printStackTrace()
                    } catch (e: ClassNotFoundException) {
                        e.printStackTrace()
                    }

                }

                ParaboxKey.TYPE_NOTIFICATION -> {
                    when (msg.what) {
                        ParaboxKey.NOTIFICATION_MAIN_APP_LAUNCH -> {
                            onMainAppLaunch()
                        }
                    }
                }
            }
        }
    }
}

