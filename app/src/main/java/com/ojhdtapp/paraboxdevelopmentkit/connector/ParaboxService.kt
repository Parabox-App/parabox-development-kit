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

abstract class ParaboxService : LifecycleService() {
    private var serviceState = ParaboxKey.STATE_STOP
    lateinit var paraboxMessenger: Messenger
    private var clientMessenger: Messenger? = null
    private var mainAppMessenger: Messenger? = null

    private val deferredMap = mutableMapOf<String, CompletableDeferred<ParaboxResult>>()
    private val messageUnreceivedMap = mutableMapOf<Long, ReceiveMessageDto>()

    abstract fun onStartParabox()
    abstract fun onStopParabox()
    abstract fun onStateUpdate(state: Int, message: String? = null)
    abstract fun customHandleMessage(msg: Message, metadata: ParaboxMetadata)
    abstract suspend fun onSendMessage(dto: SendMessageDto): Boolean
    abstract suspend fun onRecallMessage(messageId: Long): Boolean
    abstract fun onRefreshMessage()
    abstract fun onMainAppLaunch()

    fun getServiceState(): Int = serviceState

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

    fun updateServiceState(state: Int, message: String? = null) {
        serviceState = state
        onStateUpdate(state, message)
        sendNotification(ParaboxKey.NOTIFICATION_STATE_UPDATE, Bundle().apply {
            putInt("state", state)
            message?.let { putString("message", it) }
        })
    }

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

    fun receiveMessage(dto: ReceiveMessageDto) {
        sendRequest(request = ParaboxKey.REQUEST_RECEIVE_MESSAGE,
            client = ParaboxKey.CLIENT_MAIN_APP,
            extra = Bundle().apply {
                putParcelable("dto", dto)
            },
            timeoutMillis = 6000,
            onResult = {
                if (it is ParaboxResult.Fail) {
                    messageUnreceivedMap[dto.messageId!!] = dto
                } else {
                    messageUnreceivedMap.remove(dto.messageId!!)
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
            receiveMessage(it.value)
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

    fun sendCommandResponse(
        isSuccess: Boolean,
        metadata: ParaboxMetadata,
        extra: Bundle = Bundle(),
        errorCode: Int? = null
    ) {
        if (isSuccess) {
            ParaboxResult.Success(
                command = metadata.commandOrRequest,
                timestamp = metadata.timestamp,
                obj = extra,
            )
        } else {
            ParaboxResult.Fail(
                command = metadata.commandOrRequest,
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
                                command = metadata.commandOrRequest,
                                timestamp = metadata.timestamp,
                                obj = obj
                            )
                        } else {
                            ParaboxResult.Fail(
                                command = metadata.commandOrRequest,
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

